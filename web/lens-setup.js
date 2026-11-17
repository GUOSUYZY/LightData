import {
  deviceIdentity,
  matchDevice,
  matchLens,
  lensEquivalent,
  makeOptics,
  completeOptics,
  opticsField,
} from "./optics.js";

const $ = (id) => document.getElementById(id);
const rounded = (value) => String(Number(value.toFixed(2)));
const deviceName = (device) => {
  const variants = [
    ...new Set(
      (device.aliases || []).filter(
        (name) => /^iPhone /.test(name) && !device.name.endsWith(name),
      ),
    ),
  ];
  return [device.name, ...variants].join(" / ");
};

export class LensSetup {
  constructor({ current, start, save }) {
    this.current = current;
    this.start = start;
    this.save = save;
    this.devices = [];
    this.prompted = new Set();
    $("setup-start").addEventListener("click", () => this.previewCamera());
    $("setup-phone").addEventListener("change", () => this.fillLenses());
    $("setup-lens").addEventListener("change", () => this.applyLens());
    $("setup-fov-mode").addEventListener("change", () => this.preview());
    for (const id of ["setup-aperture", "setup-hfov", "setup-equivalent"])
      $(id).addEventListener("input", () => {
        $("setup-error").textContent = "";
        this.preview();
      });
    $("camera-setup-form").addEventListener("submit", (event) => {
      event.preventDefault();
      this.submit();
    });
    $("setup-skip").addEventListener("click", () => this.close());
    $("setup-close").addEventListener("click", () => this.close());
    $("camera-setup").addEventListener("cancel", (event) => {
      event.preventDefault();
      if (!this.saving && !this.switching) this.close();
    });
  }

  async initialize() {
    const [catalog, identity] = await Promise.allSettled([
      fetch("./data/cameras.json").then((r) => {
        if (!r.ok) throw new Error("镜头参数库暂时不可用");
        return r.json();
      }),
      deviceIdentity(),
    ]);
    this.devices = catalog.status === "fulfilled" ? catalog.value.devices : [];
    this.identity =
      identity.status === "fulfilled" ? identity.value : { model: "" };
    this.detected = matchDevice(this.devices, this.identity);
    $("setup-phone").replaceChildren(new Option("手动输入 / 其他设备", ""));
    for (const device of this.devices)
      $("setup-phone").add(new Option(deviceName(device), device.id));
    this.ready = true;
  }

  consider() {
    if (!this.ready || this.saving || this.switching) return;
    const current = this.current();
    if (!current.active) return;
    if (completeOptics(current.optics)) {
      if (
        $("camera-setup").open &&
        this.editingId &&
        this.editingId !== current.id
      )
        this.close();
      return;
    }
    if (!this.prompted.has(current.id)) {
      this.prompted.add(current.id);
      this.open();
    }
  }

  open() {
    if (!this.ready || this.saving || this.switching) return;
    const current = this.current();
    this.editingId = current.active ? current.id : "";
    if (current.active) this.prompted.add(current.id);
    this.returnSettings = $("settings").open || this.returnSettings;
    if ($("settings").open) $("settings").close();
    $("setup-camera-label").textContent = current.active
      ? `${current.label || "当前相机"} · ${current.settings.width} × ${current.settings.height}`
      : "首次使用：先开启相机确认当前镜头，再选择厂家参数或手动填写视角和光圈。";
    this.refreshCameraList();
    $("setup-detection").textContent = this.detected
      ? `已识别 ${deviceName(this.detected)}（${this.identity.source}）。请选择当前实际使用的镜头。`
      : this.identity.model
        ? `设备型号 ${this.identity.model} 暂未收录，请选择型号或手动填写。`
        : "浏览器未提供具体型号，可手动选择手机或输入镜头参数。";
    if (!this.devices.length)
      $("setup-detection").textContent += " 参数库暂时不可用，仍可手动填写。";
    const optics = current.optics || {};
    $("setup-phone").value =
      optics.phoneId ||
      this.preferredPhoneId ||
      current.phoneId ||
      this.detected?.id ||
      "";
    this.fillLenses(false);
    $("setup-lens").value = optics.lensId || "";
    $("setup-aperture").value = optics.aperture || current.aperture || "";
    $("setup-fov-mode").value = optics.mode || "horizontal";
    $("setup-hfov").value = optics.hfov || current.hfov || "";
    $("setup-equivalent").value = optics.equivalentMm || "";
    if (!completeOptics(optics) && !$("setup-lens").value) {
      const suggested =
        this.selectedPhone() &&
        matchLens(
          this.selectedPhone(),
          current.label,
          current.settings.facingMode,
        );
      if (suggested) {
        $("setup-lens").value = suggested.id;
        this.applyLens();
      }
    }
    $("setup-save").textContent = current.active
      ? "保存并使用"
      : "开启相机并保存";
    $("setup-error").textContent = "";
    this.preview();
    if (!$("camera-setup").open) $("camera-setup").showModal();
  }

  selectedPhone() {
    return this.devices.find((d) => d.id === $("setup-phone").value);
  }

  refreshCameraList() {
    const current = this.current();
    $("setup-camera").replaceChildren();
    for (const device of current.cameras || [])
      $("setup-camera").add(
        new Option(device.label || "默认后置镜头", device.id),
      );
    if (!$("setup-camera").options.length)
      $("setup-camera").add(new Option("默认后置镜头", ""));
    $("setup-camera").value = current.selectedDeviceId || "";
    $("setup-start").textContent = current.active
      ? "开启 / 切换"
      : "开启相机预览";
  }

  async previewCamera() {
    if (this.saving || this.switching) return;
    this.switching = true;
    this.preferredPhoneId = $("setup-phone").value;
    this.busy(true);
    try {
      await this.start($("setup-camera").value);
      if (!this.current().active)
        throw new Error("相机尚未开启，请允许相机权限后重试。");
    } catch (error) {
      $("setup-error").textContent = error.message;
    } finally {
      this.switching = false;
      this.busy(false);
    }
    if (this.current().active) this.open();
  }

  busy(value) {
    for (const id of [
      "setup-save",
      "setup-start",
      "setup-close",
      "setup-skip",
      "setup-camera",
    ])
      $(id).disabled = value;
  }

  fillLenses(clear = true) {
    $("setup-lens").replaceChildren(
      new Option("请选择当前实际镜头 / 手动填写", ""),
    );
    const phone = this.selectedPhone();
    for (const lens of phone?.lenses || []) {
      const equivalent = lensEquivalent(lens);
      $("setup-lens").add(
        new Option(
          `${lens.name} · f/${lens.aperture}${equivalent && !/\d\s*mm/i.test(lens.name) ? ` · ${rounded(equivalent)} mm` : ""}`,
          lens.id,
        ),
      );
    }
    $("setup-lens").disabled = !phone;
    if (clear) {
      $("setup-error").textContent = "";
      // Switching the phone must not silently reuse the previous phone's lens data.
      $("setup-aperture").value = "";
      $("setup-hfov").value = "";
      $("setup-equivalent").value = "";
      this.preview();
    }
  }

  applyLens() {
    $("setup-error").textContent = "";
    const lens = this.selectedPhone()?.lenses.find(
      (l) => l.id === $("setup-lens").value,
    );
    if (!lens) return this.preview();
    $("setup-aperture").value = lens.aperture;
    const equivalent = lensEquivalent(lens);
    $("setup-equivalent").value = equivalent ? rounded(equivalent) : "";
    $("setup-hfov").value = "";
    $("setup-fov-mode").value = equivalent ? "equivalent" : "horizontal";
    this.preview();
  }

  values() {
    return {
      mode: $("setup-fov-mode").value,
      aperture: $("setup-aperture").value,
      hfov: $("setup-hfov").value,
      equivalentMm: $("setup-equivalent").value,
      phoneId: $("setup-phone").value,
      lensId: $("setup-lens").value,
    };
  }

  preview() {
    const equivalent = $("setup-fov-mode").value === "equivalent";
    $("setup-horizontal-field").hidden = equivalent;
    $("setup-equivalent-field").hidden = !equivalent;
    const phone = this.selectedPhone();
    const lens = phone?.lenses.find((l) => l.id === $("setup-lens").value);
    const source = lens?.source || phone?.source;
    $("setup-source").hidden = !source;
    $("setup-source").href = source || "#";
    $("setup-source-note").textContent = [
      lens?.note,
      lens?.manufacturerFov
        ? `厂家视角 ${lens.manufacturerFov}°${lens.fovAxis === "unspecified" ? "（方向未注明，请填写水平视角或等效焦距）" : ""}`
        : "",
    ]
      .filter(Boolean)
      .join(" · ");
    const current = this.current();
    const settings = current.active
      ? current.settings
      : { width: 1280, height: 720 };
    const optics = makeOptics(this.values(), settings);
    const field =
      optics &&
      opticsField(optics, settings.width, settings.height, settings.zoom || 1);
    $("setup-field-info").textContent = field
      ? `${current.active ? "当前预览" : "按横向 16:9 预览"}：水平 ${rounded(field.hfov)}° × 垂直 ${rounded(field.vfov)}°。${equivalent ? "按原生 4:3 画面中心裁切估算，电子防抖或额外裁切可用实测视角修正。" : "请填写当前预览画面左右边缘之间的水平视角。"}`
      : equivalent
        ? "输入厂家标注的 35 mm 等效焦距，以原生 4:3 画面估算预览裁切。不是手机镜头的实际焦距。"
        : "请输入当前预览的水平视角。厂家给出的对角视角不能直接填在这里；也可切换为等效焦距。";
  }

  async submit() {
    if (this.saving || this.switching) return;
    if (
      this.current().active &&
      $("setup-camera").value !== this.current().selectedDeviceId
    ) {
      $("setup-error").textContent =
        "请先点击“开启 / 切换”确认所选相机，再保存对应镜头参数。";
      return;
    }
    const values = this.values();
    if (!makeOptics(values, this.current().settings)) {
      $("setup-error").textContent =
        "请填写 f/0.5–64 的光圈，以及 0.1–175° 的水平视角或 1–2000 mm 的等效焦距。";
      return;
    }
    this.saving = true;
    this.busy(true);
    try {
      if (!this.current().active) await this.start($("setup-camera").value);
      const current = this.current();
      if (!current.active)
        throw new Error("相机尚未开启，请允许相机权限后重试。");
      if (this.editingId && current.id !== this.editingId)
        throw new Error("当前相机已变化，请重新确认镜头参数。");
      const optics = makeOptics(values, current.settings);
      this.save(optics);
      this.prompted.add(current.id);
      this.close();
    } catch (error) {
      $("setup-error").textContent = error.message;
    } finally {
      this.saving = false;
      this.busy(false);
    }
  }

  close() {
    $("camera-setup").close();
    if (this.returnSettings && !$("settings").open) $("settings").showModal();
    this.returnSettings = false;
  }
}
