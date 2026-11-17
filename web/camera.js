/** Browser capture owns one stream. Sampling and metadata polling never restart it. */
export class Camera extends EventTarget {
  constructor(video) {
    super();
    this.video = video;
    this.canvas = document.createElement("canvas");
    this.context = this.canvas.getContext("2d", {
      willReadFrequently: true,
      colorSpace: "srgb",
    });
    this.generation = 0;
    this.settings = {};
    this.capabilities = {};
    this.paused = false;
  }

  emit(type, detail) {
    this.dispatchEvent(new CustomEvent(type, { detail }));
  }

  async start(deviceId = "") {
    this.stop();
    const generation = ++this.generation;
    if (!navigator.mediaDevices?.getUserMedia)
      throw new Error(
        "此浏览器无法使用相机。请在 HTTPS 页面使用支持相机的浏览器。",
      );
    const video = {
      width: { ideal: 1280 },
      height: { ideal: 720 },
      frameRate: { ideal: 30 },
    };
    if (deviceId) video.deviceId = { exact: deviceId };
    else video.facingMode = { ideal: "environment" };
    const stream = await navigator.mediaDevices.getUserMedia({
      audio: false,
      video,
    });
    if (generation !== this.generation) {
      stream.getTracks().forEach((track) => track.stop());
      return;
    }
    this.stream = stream;
    this.track = stream.getVideoTracks()[0];
    this.track.addEventListener("ended", () => {
      if (this.stream === stream) {
        this.stop();
        this.emit("ended");
      }
    });
    this.track.addEventListener("mute", () => this.emit("muted", true));
    this.track.addEventListener("unmute", () => this.emit("muted", false));
    this.video.srcObject = stream;
    await this.video.play();
    if (generation !== this.generation) return;
    this.readSettings();
    this.capabilities = this.track.getCapabilities?.() || {};
    this.emit("started", {
      settings: this.settings,
      capabilities: this.capabilities,
    });
    this.emit("devices", await this.devices());
    this.poll = setInterval(() => {
      if (!this.paused) this.readSettings();
    }, 500);
    this.schedule();
  }

  async devices() {
    return (await navigator.mediaDevices.enumerateDevices()).filter(
      (d) => d.kind === "videoinput",
    );
  }

  readSettings() {
    if (!this.track) return;
    this.settings = this.track.getSettings();
    this.emit("settings", this.settings);
  }

  schedule() {
    if (!this.stream || this.paused) return;
    if (this.video.requestVideoFrameCallback)
      this.callback = this.video.requestVideoFrameCallback((time) =>
        this.frame(time),
      );
    else this.fallback = setTimeout(() => this.frame(performance.now()), 100);
  }

  frame(time) {
    if (!this.stream || this.paused) return;
    if (
      (!this.lastFrame || time - this.lastFrame >= 140) &&
      this.video.readyState >= 2
    ) {
      this.lastFrame = time;
      const scale = Math.min(
        1,
        640 / Math.max(this.video.videoWidth, this.video.videoHeight),
      );
      const w = Math.max(1, Math.round(this.video.videoWidth * scale));
      const h = Math.max(1, Math.round(this.video.videoHeight * scale));
      if (this.canvas.width !== w || this.canvas.height !== h) {
        this.canvas.width = w;
        this.canvas.height = h;
      }
      this.context.drawImage(this.video, 0, 0, w, h);
      this.emit("frame", {
        image: this.context.getImageData(0, 0, w, h),
        settings: { ...this.settings },
        time,
      });
    }
    this.schedule();
  }

  pause(paused) {
    this.paused = paused;
    if (this.callback !== undefined)
      this.video.cancelVideoFrameCallback?.(this.callback);
    clearTimeout(this.fallback);
    if (!paused && this.stream) {
      this.readSettings();
      this.video
        .play()
        .then(() => this.schedule())
        .catch(() => this.emit("ended"));
    }
  }

  async fixedExposure(seconds, iso) {
    if (!this.track) throw new Error("请先开启相机");
    if (!this.capabilities.exposureMode?.includes("manual"))
      throw new Error(
        "浏览器未开放手动曝光。请在相机已固定曝光时填写并确认采集参数。",
      );
    await this.track.applyConstraints({
      advanced: [
        { exposureMode: "manual", exposureTime: seconds * 10000, iso },
      ],
    });
    this.readSettings();
    const actual = this.settings;
    if (
      actual.exposureMode !== "manual" ||
      !(actual.exposureTime > 0) ||
      !(actual.iso > 0)
    )
      throw new Error("浏览器未确认固定曝光参数，仍按未知曝光处理。");
    return { seconds: actual.exposureTime / 10000, iso: actual.iso };
  }

  stop() {
    this.generation++;
    clearInterval(this.poll);
    clearTimeout(this.fallback);
    if (this.callback !== undefined)
      this.video.cancelVideoFrameCallback?.(this.callback);
    this.stream?.getTracks().forEach((track) => track.stop());
    this.video.srcObject = null;
    this.stream = null;
    this.track = null;
    this.lastFrame = null;
    this.emit("stopped");
  }
}

export function parseTime(value) {
  const parts = String(value).trim().split("/").map(Number);
  const seconds =
    parts.length === 2
      ? parts[0] / parts[1]
      : parts.length === 1
        ? parts[0]
        : NaN;
  return Number.isFinite(seconds) && seconds > 0 && seconds <= 86400
    ? seconds
    : NaN;
}

export function captureExposure(settings, profile) {
  const a = Number(profile.aperture);
  if (!(a >= 0.5 && a <= 64)) return null;
  if (
    profile.mode === "auto" &&
    settings.exposureMode === "manual" &&
    settings.exposureTime > 0 &&
    settings.iso > 0
  ) {
    return {
      a,
      t: settings.exposureTime / 10000,
      iso: settings.iso,
      source: "固定参数 · 预览估测",
    };
  }
  if (
    profile.mode === "reported" &&
    settings.exposureTime > 0 &&
    settings.iso > 0
  ) {
    return {
      a,
      t: settings.exposureTime / 10000,
      iso: settings.iso,
      source: "浏览器 AE 参数估测 · 可能滞后",
    };
  }
  if (
    profile.mode === "manual" &&
    profile.confirmed &&
    profile.seconds > 0 &&
    profile.iso > 0 &&
    (!settings.exposureMode || settings.exposureMode === "manual") &&
    (!(settings.exposureTime > 0) ||
      Math.abs(Math.log2(settings.exposureTime / 10000 / profile.seconds)) <
        0.15) &&
    (!(settings.iso > 0) ||
      Math.abs(Math.log2(settings.iso / profile.iso)) < 0.1)
  ) {
    return {
      a,
      t: profile.seconds,
      iso: profile.iso,
      source: "手填采集参数 · 预览估测",
    };
  }
  return null;
}

// A known-EV reference remains valid only while acquisition exposure is unchanged.
export function referenceSignature(settings, profile) {
  return JSON.stringify([
    settings.exposureMode ?? null,
    settings.exposureTime ?? null,
    settings.iso ?? null,
    profile.aperture ?? null,
    profile.seconds ?? null,
    profile.iso ?? null,
  ]);
}
