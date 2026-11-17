// Use a small, fixed sampling grid on the existing analysis frame. The four
// samples per cell damp sensor noise while keeping local subject motion visible.
export class FrameMotion {
  reset() {
    this.previous = this.anchor = null;
    this.still = false;
  }

  observe(image, time) {
    const cols = Math.min(image.width, 40), rows = Math.min(image.height, 30);
    const grid = new Uint8Array(cols * rows);
    const { width, height, data } = image;
    for (let row = 0; row < rows; row++) {
      for (let col = 0; col < cols; col++) {
        let total = 0;
        for (let q = 0; q < 4; q++) {
          const x = Math.floor((col + ((q & 1) ? .75 : .25)) * width / cols);
          const y = Math.floor((row + ((q & 2) ? .75 : .25)) * height / rows);
          const i = 4 * (y * width + x);
          total += 54 * data[i] + 183 * data[i + 1] + 19 * data[i + 2];
        }
        grid[row * cols + col] = Math.round(total / 1024);
      }
    }
    if (!this.previous || time <= this.lastTime || time - this.lastTime > 1000 ||
        this.width !== width || this.height !== height) {
      this.still = false;
      this.quietSince = time;
      this.anchor = grid;
    } else {
      // Compare against the start of the quiet period too, so a slow pan cannot
      // qualify as stationary by moving only slightly between adjacent frames.
      let total = 0, changed = 0;
      for (let i = 0; i < grid.length; i++) {
        const difference = Math.max(Math.abs(grid[i] - this.previous[i]),
          Math.abs(grid[i] - this.anchor[i]));
        total += difference;
        if (difference > 10) changed++;
      }
      if (total / grid.length > (this.still ? 4 : 2.5) ||
          changed / grid.length > (this.still ? .035 : .02)) {
        this.still = false;
        this.quietSince = time;
        this.anchor = grid;
      } else if (time - this.quietSince >= 800) this.still = true;
    }
    this.width = width;
    this.height = height;
    this.lastTime = time;
    this.previous = grid;
    return this.still;
  }
}

// Fractional source rectangle and destination inset preserve transparent areas
// beyond the captured field. Resolution never exceeds available source density.
export function captureGeometry(width, height, source, target, scale = 1) {
  const wide = source && target ? width * target.x / source.x : width;
  const high = source && target ? height * target.y / source.y : height;
  if (![wide, high, scale].every(v => Number.isFinite(v) && v > 0)) return null;
  const ratio = Math.min(1, Math.max(width, height) / Math.max(wide, high)) * Math.min(1, scale);
  const w = Math.max(1, Math.round(wide * ratio)), h = Math.max(1, Math.round(high * ratio));
  const sw = Math.min(width, wide), sh = Math.min(height, high);
  const dw = sw * w / wide, dh = sh * h / high;
  return { w, h, args: [(width - sw) / 2, (height - sh) / 2, sw, sh,
    (w - dw) / 2, (h - dh) / 2, dw, dh] };
}

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
    this.motion = new FrameMotion();
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
      const image = this.captureAnalysis();
      if (image) {
        this.inFrame = true;
        this.previewKey = null;
        this.previewPixels = null;
        try {
          this.emit("frame", {
            image, still: this.motion.observe(image, time),
            settings: { ...this.settings }, time,
          });
        } finally {
          this.inFrame = false;
          this.previewKey = null;
          this.previewPixels = null;
        }
      }
    }
    this.schedule();
  }

  captureAnalysis() {
    const { videoWidth: width, videoHeight: height } = this.video;
    if (!this.stream || this.video.readyState < 2 || !width || !height) return null;
    const scale = Math.min(1, 640 / Math.max(width, height));
    const w = Math.max(1, Math.round(width * scale));
    const h = Math.max(1, Math.round(height * scale));
    if (this.canvas.width !== w || this.canvas.height !== h) {
      this.canvas.width = w;
      this.canvas.height = h;
    }
    this.context.drawImage(this.video, 0, 0, w, h);
    return this.context.getImageData(0, 0, w, h);
  }

  // Reused canvas: consume synchronously. Request capturePreview for an owned
  // pixel snapshot, e.g. when freezing the view or using a CPU renderer.
  previewCanvas(source = null, target = null, scale = 1) {
    const { videoWidth: width, videoHeight: height } = this.video;
    if (!this.stream || this.video.readyState < 2 || !width || !height) return null;
    const rect = captureGeometry(width, height, source, target, scale);
    if (!rect) return null;
    const key = [rect.w, rect.h, ...rect.args].join(",");
    if (this.inFrame && this.previewKey === key) return this.previewSurface;
    if (!this.previewSurface) {
      this.previewSurface = document.createElement("canvas");
      this.previewContext = this.previewSurface.getContext("2d", { colorSpace: "srgb" });
    }
    const canvas = this.previewSurface;
    if (canvas.width !== rect.w || canvas.height !== rect.h) {
      canvas.width = rect.w;
      canvas.height = rect.h;
    } else if (rect.args[4] > 0 || rect.args[5] > 0) {
      this.previewContext.clearRect(0, 0, rect.w, rect.h);
    }
    this.previewContext.drawImage(this.video, ...rect.args);
    this.previewKey = key;
    this.previewPixels = null;
    return canvas;
  }

  capturePreview(source = null, target = null, scale = 1) {
    const canvas = this.previewCanvas(source, target, scale);
    if (!canvas) return null;
    if (this.inFrame && this.previewPixels) return this.previewPixels;
    const image = this.previewContext.getImageData(0, 0, canvas.width, canvas.height);
    if (this.inFrame) this.previewPixels = image;
    return image;
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
    this.motion.reset();
    this.previewContext = this.previewSurface = this.previewPixels = null;
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

// Media Capture exposureTime is measured in 100 µs units, including fractional units.
// https://w3c.github.io/mediacapture-image/#dfn-exposure-time
export function trackExposure(settings) {
  const t = Number(settings.exposureTime) / 10000;
  const iso = Number(settings.iso);
  return Number.isFinite(t) && t > 0 && Number.isFinite(iso) && iso > 0
    ? { t, iso }
    : null;
}

export function captureExposure(settings, profile) {
  const a = Number(profile.aperture);
  if (!(a >= 0.5 && a <= 64)) return null;
  const mode = profile.mode || "auto";
  const reported = trackExposure(settings);
  if (
    reported &&
    (["auto", "reported"].includes(mode) ||
      (mode === "fixed" && settings.exposureMode === "manual"))
  ) {
    return {
      a,
      ...reported,
      source:
        settings.exposureMode === "manual"
          ? "固定参数 · 预览估测"
          : "浏览器 AE 参数估测 · 可能滞后",
    };
  }
  if (
    mode === "manual" &&
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
