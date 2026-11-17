import {
  FORMATS,
  APERTURES,
  SHUTTERS,
  ISOS,
  field,
  exposureEV,
  solve,
  meter,
  histogram,
  defaultResponse,
  spot,
  equivalentExposures,
  loadFilms,
  renderImage,
  timeLabel,
  reciprocity,
  CustomPresets,
  customFilm,
  closest,
  clamp,
  density,
} from "./core.js";
import {
  Camera,
  parseTime,
  captureExposure,
  trackExposure,
  referenceSignature,
} from "./camera.js";
import { Panorama, cropFrame, orientationMatrix } from "./panorama.js";
import { sampleGrid, fitResponse } from "./calibration.js";
import { LensSetup } from "./lens-setup.js";
import { completeOptics, makeOptics, opticsField } from "./optics.js";

const $ = (id) => document.getElementById(id);
const STORE = "lightdata.web.v1";
const HISTOGRAM_SETTINGS_VERSION = 1;
const COLORS = [
  "#ffce58",
  "#55ccff",
  "#ff85bb",
  "#68e3a8",
  "#ba9dff",
  "#ff9864",
];
const spotColor = (id) =>
  COLORS[id - 1] || `hsl(${(id * 137.508) % 360} 75% 72%)`;
const number = (value) => Number(value.toFixed(2)).toString();
const signed = (value) => (value >= 0 ? "+" : "") + value.toFixed(1);
const finite = (value) => Number.isFinite(value);
const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
let saved;
try {
  saved = JSON.parse(localStorage.getItem(STORE) || "{}");
} catch {
  saved = {};
}
const state = {
  mode: ["A", "S", "M"].includes(saved.mode) ? saved.mode : "A",
  manual: { t: 1 / 125, a: 8, iso: 100, ...saved.manual },
  format: saved.format || FORMATS[4],
  customFormat:
    saved.customFormat ||
    (saved.format?.name === "自定义" ? saved.format : null),
  focal: saved.focal || 35,
  orientation: saved.orientation || "auto",
  compensation: saved.compensation || 0,
  filmId: saved.filmId,
  isoLocked: saved.isoLocked !== false,
  style: saved.style || "color",
  preview: saved.preview || false,
  log:
    saved.histogramSettingsVersion === HISTOGRAM_SETTINGS_VERSION
      ? (saved.log ?? true)
      : true,
  devices: saved.devices || {},
  lenses: saved.lenses || {},
  phoneId: saved.phoneId || "",
  layouts: saved.layouts || {},
  presets: [],
  films: [],
  single: false,
  held: null,
  frozen: false,
  spots: [],
  nextSpot: 1,
  raw: null,
  image: null,
  hist: null,
  cameraEV: null,
  source: null,
  reading: null,
  exposure: null,
  profile: {},
  profileKey: "",
  histWindow: null,
  pose: null,
  pano: null,
  scan: false,
  offset: { x: 0, y: 0 },
  runningCalibration: false,
};
try {
  state.presets = CustomPresets.decode(
    saved.presets || CustomPresets.encode([]),
  );
} catch {
  state.presets = [];
}
const camera = new Camera($("capture-video"));
const cameraId = () =>
  camera.settings.deviceId || camera.track?.label || "default";
let activeCameraChoice = "";
const lensSetup = new LensSetup({
  current: () => ({
    id: cameraId(),
    active: !!camera.stream,
    settings: camera.settings,
    label: camera.track?.label,
    optics: state.profile.optics || state.lenses[cameraId()],
    aperture: state.profile.aperture,
    hfov: state.profile.hfov,
    phoneId: state.phoneId,
    cameras: Array.from($("camera-select").options, (o) => ({
      id: o.value,
      label: o.textContent,
    })),
    selectedDeviceId: camera.stream
      ? activeCameraChoice
      : $("camera-select").value,
  }),
  start: (deviceId) => {
    if (typeof deviceId === "string") $("camera-select").value = deviceId;
    return startCamera({ throwErrors: true });
  },
  save: (optics) => {
    if (state.frozen) toggleFreeze();
    cancelScan();
    state.profile.optics = optics;
    state.profile.aperture = optics.aperture;
    state.profile.hfov = opticsField(
      optics,
      camera.settings.width,
      camera.settings.height,
      camera.settings.zoom || 1,
    )?.hfov;
    rememberOptics();
    persist();
    refreshSettings();
    processImage();
    toast("已保存当前镜头的视角与光圈");
  },
});
const viewContext = $("view").getContext("2d");
const chartContext = $("chart").getContext("2d");
let lastPairKey = "",
  lastWheelKey = "",
  lastFrameTime = 0,
  toastTimer,
  editingPreset = null;
let brackets = [],
  calibrationToken = 0,
  calibrationSession = null;
let resetting = false;

function persist() {
  if (resetting) return;
  const value = Object.fromEntries(
    [
      "mode",
      "manual",
      "format",
      "customFormat",
      "focal",
      "orientation",
      "compensation",
      "filmId",
      "isoLocked",
      "style",
      "preview",
      "log",
      "devices",
      "lenses",
      "phoneId",
      "layouts",
    ].map((key) => [key, state[key]]),
  );
  value.presets = CustomPresets.encode(state.presets);
  value.histogramSettingsVersion = HISTOGRAM_SETTINGS_VERSION;
  try {
    localStorage.setItem(STORE, JSON.stringify(value));
  } catch {
    toast("设备存储不可用，本次设置将在关闭页面后丢失");
  }
}
function resetSettings() {
  const retained = {
    log: true,
    histogramSettingsVersion: HISTOGRAM_SETTINGS_VERSION,
    devices: Object.fromEntries(
      Object.entries(state.devices).map(([key, profile]) => [
        key,
        {
          ...profile,
          mode: "auto",
          iso: "",
          seconds: null,
          confirmed: false,
          referenceBase: null,
          referenceSignature: null,
        },
      ]),
    ),
    lenses: state.lenses,
    phoneId: state.phoneId,
    presets: CustomPresets.encode(state.presets),
  };
  try {
    localStorage.setItem(STORE, JSON.stringify(retained));
  } catch {
    toast("无法保存重置后的设置，请检查浏览器存储权限后重试");
    return;
  }
  resetting = true;
  calibrationToken++;
  camera.stop();
  location.reload();
}
function toast(message) {
  $("toast").textContent = message;
  $("toast").classList.add("visible");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => $("toast").classList.remove("visible"), 4500);
}
function notice(title, text) {
  $("notice-title").textContent = title;
  $("notice-text").textContent = text;
  if (!$("notice").open) $("notice").showModal();
}
function warnEstimated() {
  if (calibration() || saved.warned) return;
  saved.warned = true;
  try {
    localStorage.setItem("lightdata.estimateWarned", "yes");
  } catch {
    /* Session flag remains. */
  }
  notice(
    "当前镜头尚未校准",
    "当前结果使用浏览器预览响应估测。手机的自动处理和曝光参数可能影响精度，直方图以红框提示。可在设置中逐镜头校准。",
  );
}
try {
  saved.warned = localStorage.getItem("lightdata.estimateWarned") === "yes";
} catch {
  /* Private browsing. */
}

function allFilms() {
  return [
    ...state.films,
    ...state.presets.map((p) => ({
      ...customFilm(p),
      id: p.id,
      brand: "自定义预设",
      iso: p.iso,
    })),
  ];
}
function film() {
  return allFilms().find((f) => f.id === state.filmId) || state.films[0];
}
function calibration() {
  const capture = captureExposure(camera.settings, state.profile);
  const reported = Number(camera.settings.iso);
  const entered = state.profile.confirmed ? Number(state.profile.iso) : NaN;
  const iso = capture?.iso || (reported > 0 ? reported : entered);
  return finite(iso) && iso > 0
    ? state.profile.calibrations?.[number(iso)] || null
    : null;
}
function response() {
  return (
    (state.frozen && state.frozenResponse) ||
    calibration()?.response ||
    defaultResponse
  );
}
function measurement() {
  return state.single ? state.held : state.reading;
}
function absolute() {
  return finite(state.cameraEV);
}
function target() {
  const portrait =
    state.orientation === "portrait" ||
    (state.orientation === "auto" && innerHeight > innerWidth);
  const w = portrait
    ? Math.min(state.format.w, state.format.h)
    : Math.max(state.format.w, state.format.h);
  const h = portrait
    ? Math.max(state.format.w, state.format.h)
    : Math.min(state.format.w, state.format.h);
  return field(w, h, state.focal);
}
function sourceField() {
  if (!state.raw) return null;
  if (state.profile.optics) {
    const field = opticsField(
      state.profile.optics,
      state.raw.width,
      state.raw.height,
      camera.settings.zoom || 1,
    );
    if (field) return field;
  }
  const hfov = Number(state.profile.hfov);
  if (!(hfov >= 0.1 && hfov <= 175)) return null;
  const x = Math.tan((hfov * Math.PI) / 360);
  return { x, y: (x * state.raw.height) / state.raw.width };
}
function sceneBase(settings = camera.settings) {
  if (
    state.profile.mode === "reference" &&
    state.profile.confirmed &&
    finite(state.profile.referenceBase) &&
    state.profile.referenceSignature ===
      referenceSignature(settings, state.profile)
  ) {
    return {
      value: state.profile.referenceBase,
      source: "已知 EV 标定 · 固定预览估测",
    };
  }
  const capture = captureExposure(settings, state.profile);
  return capture
    ? { value: exposureEV(capture), source: capture.source }
    : { value: null, source: "采集参数待设置" };
}
function updateProfile(settings = camera.settings) {
  const key = `${settings.deviceId || "default"}:${settings.width}x${settings.height}:z${settings.zoom || 1}`;
  if (state.profileKey === key) return;
  if (state.profileKey && state.pano) cancelScan();
  state.profileKey = key;
  state.profile = state.devices[key] || {
    mode: "auto",
    aperture: "",
    iso: "",
    seconds: null,
    confirmed: false,
  };
  state.devices[key] = state.profile;
  if (state.profile.mode === "reported") state.profile.mode = "auto";
  const remembered = state.lenses[cameraId()];
  if (remembered) {
    // Physical lens optics may be reused; response calibrations remain per capture profile.
    state.profile.optics = { ...remembered };
    state.profile.aperture = remembered.aperture;
  } else if (!state.profile.optics) {
    // Preserve pre-onboarding users' already measured and saved lens settings.
    const legacy = makeOptics(
      {
        mode: "horizontal",
        aperture: state.profile.aperture,
        hfov: state.profile.hfov,
      },
      settings,
    );
    if (legacy) state.profile.optics = legacy;
  }
  if (state.profile.optics) {
    state.profile.hfov = opticsField(
      state.profile.optics,
      settings.width,
      settings.height,
      settings.zoom || 1,
    )?.hfov;
    rememberOptics();
    persist();
  }
  state.histWindow = null;
  state.held = null;
  refreshSettings();
  lensSetup.consider();
}
function rememberOptics() {
  if (!camera.stream) return;
  const profile = state.profile;
  if (!profile.optics) {
    profile.optics = makeOptics(
      { mode: "horizontal", aperture: profile.aperture, hfov: profile.hfov },
      camera.settings,
    );
  }
  if (profile.optics) {
    profile.optics.aperture = Number(profile.aperture);
    state.lenses[cameraId()] = { ...profile.optics };
    if (profile.optics.phoneId) state.phoneId = profile.optics.phoneId;
  }
}
function chooseFilm(id) {
  state.filmId = id;
  state.manual.iso = film().iso;
  state.isoLocked = true;
  editingPreset = state.presets.find((p) => p.id === id)?.id || null;
  fillPreset();
  persist();
  refreshSettings();
  refresh();
}
function fillPreset() {
  const preset = state.presets.find((p) => p.id === editingPreset);
  $("preset-name").value = preset?.name || "";
  $("preset-type").value = preset?.digital ? "digital" : "film";
  $("preset-iso").value = preset?.iso || state.manual.iso;
  $("preset-total").value = preset?.total || 12;
  $("preset-above").value = preset?.above || 4;
}

function layout() {
  const landscape = innerWidth > innerHeight;
  const square =
    Math.min(innerWidth, innerHeight) >= 540 &&
    Math.max(innerWidth, innerHeight) / Math.min(innerWidth, innerHeight) <=
      1.3;
  state.side = square ? !landscape : landscape;
  state.layoutKey = `${square ? "square" : ""}${landscape ? "l" : "p"}`;
  document.body.classList.toggle("landscape", landscape);
  document.body.classList.toggle("side", state.side);
  const values = state.layouts[state.layoutKey] || [
    square ? 0.5 : 0.6,
    square ? 0.5 : 0.46,
  ];
  document.documentElement.style.setProperty("--p", values[0] * 100 + "%");
  document.documentElement.style.setProperty("--q", values[1] * 100 + "%");
  $("split-main").setAttribute(
    "aria-orientation",
    state.side ? "vertical" : "horizontal",
  );
  $("split-secondary").setAttribute(
    "aria-orientation",
    state.side ? "horizontal" : "vertical",
  );
  if (state.pano && state.panoAspect !== target().x / target().y) cancelScan();
  if (state.raw && !state.frozen) processImage();
  else refresh();
}

function drawHistogram() {
  const canvas = $("chart"),
    rect = canvas.getBoundingClientRect(),
    dpr = Math.min(devicePixelRatio || 1, 2);
  if (!rect.width || !rect.height) return;
  canvas.width = Math.round(rect.width * dpr);
  canvas.height = Math.round(rect.height * dpr);
  const ctx = chartContext;
  ctx.scale(dpr, dpr);
  const w = rect.width,
    h = rect.height;
  const plot = {
    x: Math.min(29, w * 0.18),
    y: 10,
    w: w - Math.min(29, w * 0.18) - Math.min(26, w * 0.15),
    h: Math.max(10, h - 44),
  };
  const isAbsolute = absolute();
  const axis = state.histWindow || {
    left:
      isAbsolute && finite(state.reading)
        ? Math.round(state.reading / 2) * 2 - 8
        : -8,
    span: 16,
    gain: 1,
  };
  canvas.setAttribute(
    "aria-label",
    `直方图横轴 ${isAbsolute ? "EV100" : "相对 EV"} ${number(axis.left)} 至 ${number(axis.left + axis.span)}，纵轴放大 ${number(axis.gain)} 倍`,
  );
  const base = isAbsolute ? state.cameraEV : 0;
  const middle = isAbsolute ? exposureEV(state.exposure || state.manual) : 0;
  const xAt = (ev) => plot.x + ((ev - axis.left) / axis.span) * plot.w;
  const bins = new Float64Array(Math.max(32, Math.floor(plot.w)));
  if (state.hist)
    for (let i = 0; i < 256; i++) {
      const bin = Math.floor(
        ((base + response()[i] - axis.left) / axis.span) * bins.length,
      );
      if (bin >= 0 && bin < bins.length) bins[bin] += state.hist[i];
    }
  const max = Math.max(1, ...bins) / axis.gain;
  const valueY = (count) =>
    plot.y +
    plot.h *
      (1 -
        clamp(
          state.log
            ? Math.log10(Math.max(1, count)) /
                Math.max(0.01, Math.log10(Math.max(2, max)))
            : count / max,
          0,
          1,
        ));
  ctx.font = "10px monospace";
  ctx.lineWidth = 0.65;
  ctx.textBaseline = "middle";
  let levels;
  if (state.log)
    levels = Array.from(
      { length: Math.max(1, Math.ceil(Math.log10(Math.max(2, max)))) },
      (_, i) => 10 ** i,
    );
  else levels = [0, max * 0.25, max * 0.5, max * 0.75, max];
  ctx.save();
  ctx.beginPath();
  ctx.rect(plot.x, plot.y, plot.w, plot.h);
  ctx.clip();
  for (const n of levels) {
    const y = valueY(n);
    ctx.strokeStyle = "#34383a";
    ctx.beginPath();
    ctx.moveTo(plot.x, y);
    ctx.lineTo(plot.x + plot.w, y);
    ctx.stroke();
  }
  const spacing = axis.span > 24 ? 4 : axis.span > 12 ? 2 : 1;
  for (
    let ev = Math.ceil(axis.left / spacing) * spacing;
    ev <= axis.left + axis.span;
    ev += spacing
  ) {
    const x = xAt(ev);
    ctx.strokeStyle = "#2f3234";
    ctx.beginPath();
    ctx.moveTo(x, plot.y);
    ctx.lineTo(x, plot.y + plot.h);
    ctx.stroke();
  }
  ctx.fillStyle = "#babcb9";
  for (let i = 0; i < bins.length; i++)
    if (bins[i]) {
      const x = plot.x + (i / bins.length) * plot.w,
        y = valueY(bins[i]);
      ctx.fillRect(
        x,
        y,
        Math.max(1, plot.w / bins.length),
        plot.y + plot.h - y,
      );
    }
  const selectedFilm = film();
  const maximumD = selectedFilm
    ? Math.ceil(Math.max(1, ...selectedFilm.dots.map((d) => d[1])) * 1.05 * 2) /
      2
    : 1;
  if (selectedFilm) {
    ctx.strokeStyle = "#ffbd59";
    ctx.lineWidth = 1.7;
    ctx.beginPath();
    for (let i = 0; i <= plot.w; i++) {
      const ev = axis.left + (i / plot.w) * axis.span - middle;
      const y = plot.y + plot.h * (1 - density(selectedFilm, ev) / maximumD);
      if (i === 0) ctx.moveTo(plot.x + i, y);
      else ctx.lineTo(plot.x + i, y);
    }
    ctx.stroke();
    for (const [ev, color] of [
      [selectedFilm.low, "#6dd8a0"],
      [selectedFilm.high, "#ec756a"],
    ]) {
      const x = xAt(middle + ev);
      ctx.strokeStyle = color;
      ctx.lineWidth = 1.3;
      ctx.beginPath();
      ctx.moveTo(x, plot.y);
      ctx.lineTo(x, plot.y + plot.h);
      ctx.stroke();
    }
  }
  ctx.strokeStyle = "#f0efea";
  ctx.lineWidth = 1;
  ctx.setLineDash([3, 3]);
  ctx.beginPath();
  ctx.moveTo(xAt(middle), plot.y);
  ctx.lineTo(xAt(middle), plot.y + plot.h);
  ctx.stroke();
  ctx.setLineDash([]);
  for (const point of state.spots) {
    const x = xAt(point.ev);
    ctx.strokeStyle = spotColor(point.id);
    ctx.lineWidth = 1.2;
    ctx.beginPath();
    ctx.moveTo(x, plot.y);
    ctx.lineTo(x, plot.y + plot.h);
    ctx.stroke();
    ctx.fillStyle = spotColor(point.id);
    ctx.fillText(String(point.id), x + 3, plot.y + 8);
  }
  ctx.restore();
  ctx.fillStyle = "#a5a6a3";
  ctx.font = "9px monospace";
  ctx.textAlign = "right";
  for (const n of levels) {
    if (n > max) continue;
    ctx.fillText(
      n >= 1000 ? number(n / 1000) + "k" : String(Math.round(n)),
      plot.x - 4,
      valueY(n),
    );
  }
  ctx.textAlign = "left";
  ctx.fillStyle = "#ffbd59";
  const densityStep = maximumD <= 3 ? 0.5 : 1;
  for (let d = 0; d <= maximumD; d += densityStep) {
    const y = plot.y + plot.h * (1 - d / maximumD);
    ctx.fillText(number(d), plot.x + plot.w + 3, y);
  }
  ctx.textAlign = "center";
  const labelStep = spacing * ((plot.w / axis.span) * spacing < 27 ? 2 : 1);
  for (
    let ev = Math.ceil(axis.left / labelStep) * labelStep;
    ev <= axis.left + axis.span;
    ev += labelStep
  )
    ctx.fillText(number(ev), xAt(ev), plot.y + plot.h + 11);
  ctx.fillStyle = "#ffbd59";
  for (const [ev, label] of [
    [-3, "II"],
    [0, "V"],
    [3, "VIII"],
  ]) {
    const x = xAt(middle + ev);
    if (x > plot.x + 5 && x < plot.x + plot.w - 7)
      ctx.fillText(label, x, h - 7);
  }
  $("pixel-label").textContent = state.log ? "像素 log" : "像素";
  $("thresholds").children[0].textContent =
    "欠 " + (selectedFilm ? number(middle + selectedFilm.low) : "—");
  $("thresholds").children[1].textContent = isAbsolute ? "EV100" : "相对 EV";
  $("thresholds").children[2].textContent =
    "过 " + (selectedFilm ? number(middle + selectedFilm.high) : "—");
  state.plot = { ...plot, axis };
}

function fitView(image) {
  const stage = $("viewstage"),
    rect = stage.getBoundingClientRect();
  const ratio = image.width / image.height;
  const w = Math.min(rect.width, rect.height * ratio),
    h = w / ratio;
  $("image-frame").style.width = w + "px";
  $("image-frame").style.height = h + "px";
  $("image-frame").style.display = "block";
}
function drawView() {
  if (!state.image || !film()) return;
  const known = absolute();
  const rendered = renderImage(state.image, {
    film: film(),
    style: state.style,
    preview: state.preview && known,
    manual: state.mode === "M" && known,
    cameraEv: state.cameraEV || 0,
    targetEv: exposureEV(state.exposure || state.manual),
    response: response(),
    stripes: known,
  });
  const canvas = $("view");
  canvas.width = rendered.width;
  canvas.height = rendered.height;
  viewContext.putImageData(
    new ImageData(rendered.data, rendered.width, rendered.height),
    0,
    0,
  );
  fitView(rendered);
}
function processImage() {
  if (!state.raw || state.frozen) return;
  const base = sceneBase();
  state.source = sourceField();
  if (state.pano) {
    const result = state.pano.view(
      state.pose,
      state.manualCrop ? state.offset : null,
    );
    state.image = result.image;
    state.cameraEV = result.cameraEV;
    state.missing = result.missing;
  } else {
    state.image = state.source
      ? cropFrame(state.raw, state.source, target(), 640)
      : state.raw;
    state.cameraEV = base.value;
    state.missing = state.source
      ? state.source.x < target().x || state.source.y < target().y
      : false;
  }
  state.hist = histogram(state.image);
  state.reading = absolute()
    ? state.cameraEV + meter(state.hist, response())
    : null;
  if (!state.histWindow && finite(state.reading))
    state.histWindow = {
      left: Math.round(state.reading / 2) * 2 - 8,
      span: 16,
      gain: 1,
    };
  refresh();
}

function renderPairs() {
  const e = state.exposure,
    available = finite(measurement()),
    width = $("meter").clientWidth;
  const pairs = equivalentExposures(e);
  const count = Math.min(
    pairs.length,
    width >= 570 ? 9 : width >= 380 ? 7 : width >= 235 ? 5 : 3,
  );
  const selected = pairs.findIndex((p) => p.t === e.t && p.a === e.a);
  const first = clamp(
    selected - Math.floor(count / 2),
    0,
    pairs.length - count,
  );
  const shown = pairs.slice(first, first + count);
  const key = JSON.stringify([
    shown,
    selected - first,
    state.mode,
    available,
    state.frozen,
    film()?.name,
  ]);
  if (key === lastPairKey) return;
  lastPairKey = key;
  $("exposure-table").replaceChildren();
  const corrections = shown.map((p) => reciprocity(film()?.name, p.t));
  const corrected =
    (available || state.mode === "M") &&
    corrections.some((r) => r.status === "corrected");
  shown.forEach((pair, i) => {
    const button = document.createElement("button");
    button.className = i === selected - first ? "active" : "";
    button.disabled = state.frozen;
    button.setAttribute(
      "aria-label",
      `快门 ${timeLabel(pair.t)}，光圈 ${pair.a}，ISO ${pair.iso}`,
    );
    const t = document.createElement("span"),
      a = document.createElement("span");
    t.textContent = state.mode === "A" && !available ? "—" : timeLabel(pair.t);
    a.textContent =
      state.mode === "S" && !available ? "—" : "f/" + number(pair.a);
    button.append(t);
    if (corrected) {
      const actual = document.createElement("small");
      actual.textContent =
        corrections[i].status === "corrected"
          ? "→ " + timeLabel(corrections[i].seconds)
          : "";
      button.append(actual);
    }
    button.append(a);
    button.addEventListener("click", () => {
      if (performance.now() - (state.lastSwipe || 0) < 250) return;
      if (state.mode === "A") state.manual.a = pair.a;
      else if (state.mode === "S") state.manual.t = pair.t;
      else state.manual = { ...pair };
      persist();
      refresh();
    });
    $("exposure-table").append(button);
  });
  const correctedTime = reciprocity(film()?.name, e.t);
  $("reciprocity-note").textContent =
    e.t >= 1 && (available || state.mode === "M") ? correctedTime.note : "";
  $("reciprocity-note").title = correctedTime.source || "";
}
function renderIso() {
  const values = [...new Set([...ISOS, state.manual.iso])].sort(
    (a, b) => a - b,
  );
  const index = values.indexOf(state.manual.iso),
    count = $("meter").clientWidth > 380 ? 7 : 3;
  const start = clamp(
    index - Math.floor(count / 2),
    0,
    Math.max(0, values.length - count),
  );
  const shown = values.slice(start, start + count);
  const key = JSON.stringify([
    shown,
    state.isoLocked,
    state.manual.iso,
    state.frozen,
  ]);
  if (key === lastWheelKey) return;
  lastWheelKey = key;
  $("iso-wheel").replaceChildren();
  shown.forEach((iso) => {
    const b = document.createElement("button");
    b.textContent = String(iso);
    b.className = iso === state.manual.iso ? "active" : "";
    b.disabled = state.frozen || state.isoLocked;
    b.addEventListener("click", () => {
      if (performance.now() - (state.lastSwipe || 0) < 250) return;
      state.manual.iso = iso;
      persist();
      refresh();
    });
    $("iso-wheel").append(b);
  });
}
function refresh() {
  const selectedFilm = film();
  if (!selectedFilm) return;
  const reading = measurement();
  if (!state.frozen || state.exposure?.iso !== state.manual.iso)
    state.exposure = finite(reading)
      ? solve(state.mode, state.manual, reading, state.compensation)
      : { ...state.manual };
  if (!state.exposure) state.exposure = { ...state.manual };
  document.querySelectorAll("[data-mode]").forEach((b) => {
    b.setAttribute("aria-pressed", b.dataset.mode === state.mode);
    b.disabled = state.frozen;
  });
  $("continuous").setAttribute("aria-pressed", !state.single);
  $("single").setAttribute("aria-pressed", state.single);
  $("continuous").disabled = state.frozen;
  $("single").disabled = state.frozen;
  $("comp").disabled = state.frozen || state.mode === "M";
  $("comp").value = Math.round(state.compensation * 3);
  $("comp-label").textContent = state.mode === "M" ? "曝光偏差" : "曝光补偿";
  const residual = finite(reading)
    ? reading - exposureEV(state.exposure)
    : null;
  $("comp-value").textContent =
    state.mode === "M"
      ? (finite(residual) ? signed(residual) : "—") + " EV"
      : signed(state.compensation) + " EV";
  if (
    state.mode !== "M" &&
    finite(residual) &&
    Math.abs(residual - state.compensation) > 0.25
  )
    $("comp-label").textContent =
      "超限 " + signed(residual - state.compensation) + " EV";
  $("pair-iso").textContent = "ISO " + number(state.manual.iso);
  $("iso-lock").textContent = state.isoLocked
    ? "已锁定 · 解锁"
    : "锁定至 " + selectedFilm.iso;
  $("iso-lock").disabled = state.frozen;
  $("preview-toggle").setAttribute("aria-pressed", state.preview);
  $("spots-toggle").setAttribute("aria-pressed", state.frozen);
  $("preview-status").textContent = state.frozen
    ? "冻结点测光"
    : state.preview
      ? absolute()
        ? "胶片预览"
        : "预览待标定"
      : "";
  $("histogram").classList.toggle("uncalibrated", !calibration());
  $("meter-reading").classList.toggle("calibrated", !!calibration());
  $("meter-reading").textContent = finite(reading)
    ? `${reading.toFixed(1)} EV100 · ${state.single ? "单次 · " : ""}${calibration() ? "已校准" : "估测"}`
    : state.image
      ? state.single
        ? "单次 · 等待有效测光"
        : "相对亮度 · 参数待设置"
      : "— EV100";
  $("meter-configure").hidden = absolute() || !state.raw;
  const targetField = target(),
    source = state.source;
  $("finder-info").textContent = source
    ? `目标 ${number((2 * Math.atan(targetField.x) * 180) / Math.PI)}° × ${number((2 * Math.atan(targetField.y) * 180) / Math.PI)}°${state.missing ? " · 画面未补齐" : ""}`
    : "手机视角待标定";
  $("scan-start").hidden =
    !state.raw || !source || !!state.pano || !state.missing;
  renderPairs();
  renderIso();
  drawHistogram();
  drawView();
}

function renderFilmList() {
  const query = $("film-search").value.trim().toLowerCase();
  const items = allFilms()
    .filter((f) => `${f.brand} ${f.name}`.toLowerCase().includes(query))
    .sort(
      (a, b) =>
        a.brand.localeCompare(b.brand, "zh-CN") ||
        a.name.localeCompare(b.name, "zh-CN", { numeric: true }),
    );
  $("film-select").replaceChildren();
  let group;
  for (const item of items) {
    if (!group || group.label !== item.brand) {
      group = document.createElement("optgroup");
      group.label = item.brand;
      $("film-select").append(group);
    }
    const o = document.createElement("option");
    o.value = item.id;
    o.textContent = `${item.name} · ISO ${item.iso}`;
    o.selected = item.id === state.filmId;
    group.append(o);
  }
  if (!items.length) {
    const o = document.createElement("option");
    o.textContent = "无匹配型号";
    o.disabled = true;
    $("film-select").append(o);
  }
}
function refreshSettings() {
  const p = state.profile;
  $("capture-mode").value = p.mode || "auto";
  $("settings").classList.toggle("capture-reference", p.mode === "reference");
  $("capture-aperture").value = p.aperture || "";
  $("capture-confirmed").checked = !!p.confirmed;
  refreshCaptureControls(camera.settings, true);
  $("camera-hfov").value = p.hfov ? number(p.hfov) : "";
  $("camera-equivalent").value =
    p.optics?.mode === "equivalent" ? p.optics.equivalentMm : "";
  $("camera-fov-info").textContent =
    p.optics?.mode === "equivalent"
      ? "根据 35 mm 等效焦距，以原生 4:3 画面按当前预览比例中心裁切估算。可直接输入实测水平视角覆盖估算。"
      : p.hfov
        ? "使用水平视角；旋转或改变预览比例时按中心裁切估算。实测标定可修正电子防抖等额外裁切。"
        : "尚未设置手机镜头视角，无法判断目标画面是否需要拼接。";
  const phone = lensSetup.devices.find((d) => d.id === p.optics?.phoneId);
  const phoneLens = phone?.lenses.find((l) => l.id === p.optics?.lensId);
  $("lens-profile-info").textContent = completeOptics(p.optics)
    ? `${[phone?.name, phoneLens?.name].filter(Boolean).join(" · ") || "当前镜头"} · f/${p.aperture} · ${p.optics.mode === "equivalent" ? `等效 ${number(p.optics.equivalentMm)} mm · ` : ""}预览水平 ${number(p.hfov)}°，已保存。`
    : "首次使用每个镜头时填写视角和光圈，之后自动恢复。";
  $("target-orientation").value = state.orientation;
  $("log-counts").checked = state.log;
  if (film()) {
    renderFilmList();
    $("film-info").textContent =
      film().info +
      (film().color?.density.red
        ? " · 分层色彩来自厂家光谱与密度图，手机 RGB 光谱重建为近似。"
        : "");
    $("film-source").hidden = !film().url;
    $("film-source").href = film().url || "#";
    $("custom-iso").value = state.manual.iso;
    $("custom-iso").disabled = state.isoLocked;
    $("set-iso").disabled = state.isoLocked;
    $("settings-iso-lock").textContent = state.isoLocked
      ? "解锁 ISO"
      : "锁定至 " + film().iso;
  }
  $("color-style").value = state.style;
  $("style-info").textContent =
    state.style === "aero"
      ? "可见光近似映射，无法还原真实红外反射。"
      : state.style === "purple"
        ? "可见光画面的 LomoChrome Purple 风格近似。"
        : "";
  const cal = calibration();
  $("calibration-status").textContent = cal
    ? `已保存 · ${cal.stops.toFixed(1)} EV · ${cal.channel} · ISO ${cal.iso}`
    : "当前镜头和采集 ISO 未校准。直方图以红框标识。";
}
function openSettings() {
  refreshSettings();
  if (!$("settings").open) $("settings").showModal();
}
function refreshCaptureControls(settings = camera.settings, reset = false) {
  const p = state.profile;
  const automatic = $("capture-mode").value === "auto";
  const reported = camera.stream ? trackExposure(settings) : null;
  $("capture-iso").readOnly = automatic;
  $("capture-time").readOnly = automatic;
  $("capture-iso-label").textContent = automatic
    ? "手机 ISO · 自动读取"
    : "手机 ISO";
  $("capture-time-label").textContent = automatic
    ? "快门（秒）· 自动读取"
    : "快门（秒）";
  $("capture-fixed-controls").hidden = automatic;
  $("capture-iso").placeholder = automatic ? "相机未提供" : "例如 100";
  $("capture-time").placeholder = automatic ? "相机未提供" : "1/100";
  if (automatic) {
    $("capture-iso").value = reported ? number(reported.iso) : "";
    $("capture-time").value = reported
      ? String(Number(reported.t.toPrecision(9)))
      : "";
  } else if (reset) {
    $("capture-iso").value = p.iso || reported?.iso || "";
    $("capture-time").value = p.seconds || reported?.t || "";
  }
  const capture = camera.stream ? captureExposure(settings, p) : null;
  $("capture-info").textContent = capture
    ? `${capture.source} · 相机 f/${capture.a} · ${timeLabel(capture.t)} · ISO ${number(capture.iso)}。用于场景 EV100、直方图与曝光组合，每 0.5 秒刷新参数。${settings.exposureMode !== "manual" ? "自动曝光参数可能滞后于画面。" : ""}${state.frozen ? "冻结测光保留冻结时的参数。" : ""}`
    : !camera.stream
      ? "开启相机后自动读取快门与 ISO，只需填写手机光圈。"
      : automatic && !(Number(p.aperture) >= 0.5 && Number(p.aperture) <= 64)
        ? reported
          ? "已读取相机快门与 ISO，请填写手机光圈后开始绝对 EV100 测光。"
          : "请填写手机光圈；相机暂未提供有效快门与 ISO，先显示相对亮度。"
        : automatic
          ? "相机暂未提供有效快门与 ISO，先显示相对亮度；不会沿用上次参数。"
          : p.mode === "fixed"
            ? "此模式仅接受浏览器确认已固定的曝光。可切回自动读取，直接使用相机报告的快门与 ISO。"
            : p.mode === "reference"
              ? finite(sceneBase(settings).value)
                ? "已使用当前已知 EV 标定。改变相机采集曝光后需重新标定。"
                : "请实际固定相机曝光，确认后设置已知 EV 标定。"
              : "请填写并确认固定采集参数；参数须与相机实际曝光一致。";
}
function captureProfileFromInputs() {
  state.profile.mode = $("capture-mode").value;
  state.profile.aperture = Number($("capture-aperture").value);
  if (state.profile.mode !== "auto")
    Object.assign(state.profile, {
      iso: Number($("capture-iso").value),
      seconds: parseTime($("capture-time").value),
      confirmed: $("capture-confirmed").checked,
    });
  if (!finite(state.profile.seconds)) state.profile.seconds = null;
  rememberOptics();
  refreshCaptureControls();
  persist();
  processImage();
}

async function startCamera({ throwErrors = false } = {}) {
  $("start-camera").disabled = true;
  $("start-message").textContent = "正在请求相机权限…";
  calibrationToken++;
  state.frozen = false;
  state.frozenResponse = null;
  state.held = null;
  state.spots = [];
  drawSpots();
  cancelScan();
  state.raw = null;
  state.image = null;
  state.hist = null;
  state.reading = null;
  state.cameraEV = null;
  $("image-frame").style.display = "none";
  $("welcome").hidden = false;
  refresh();
  try {
    await camera.start($("camera-select").value);
  } catch (error) {
    const message =
      error.name === "NotAllowedError"
        ? "相机权限被拒绝。请在浏览器网站设置中允许相机后重试。"
        : error.name === "NotFoundError"
          ? "没有找到可用相机。请连接相机后重试。"
          : error.message;
    $("welcome").hidden = false;
    $("start-message").textContent = message;
    $("camera-status").textContent = message;
    toast(message);
    if (throwErrors) throw new Error(message);
  } finally {
    $("start-camera").disabled = false;
  }
}
camera.addEventListener("started", (event) => {
  updateProfile(event.detail.settings);
  // Starting a new session may reset the camera's previous exposure lock.
  state.profile.confirmed = false;
  $("welcome").hidden = true;
  $("live-state").classList.add("running");
  $("live-state").innerHTML = "<i></i> LIVE";
  $("camera-status").textContent =
    `${event.detail.settings.width} × ${event.detail.settings.height} · 当前镜头已开启`;
  refreshSettings();
});
camera.addEventListener("devices", (event) => {
  const labelMatches = event.detail.filter(
    (d) => d.label && d.label === camera.track?.label,
  );
  const active =
    event.detail.find((d) => d.deviceId === camera.settings.deviceId) ||
    (labelMatches.length === 1 ? labelMatches[0] : null);
  activeCameraChoice = active?.deviceId || $("camera-select").value;
  $("camera-select").replaceChildren();
  event.detail.forEach((device, i) => {
    const option = document.createElement("option");
    option.value = device.deviceId;
    option.textContent = device.label || `镜头 ${i + 1}`;
    option.selected = device.deviceId === activeCameraChoice;
    $("camera-select").append(option);
  });
  const selected = $("camera-select").selectedOptions[0];
  $("camera-shortcut").textContent = selected?.textContent || "选择镜头";
  lensSetup.refreshCameraList();
});
camera.addEventListener("settings", (event) => {
  if (camera.stream) updateProfile(event.detail);
  $("track-info").textContent = JSON.stringify(
    { settings: event.detail, capabilities: camera.capabilities },
    null,
    2,
  );
  refreshCaptureControls(event.detail);
});
camera.addEventListener("frame", (event) => {
  if (state.frozen) return;
  state.raw = event.detail.image;
  lastFrameTime = event.detail.time;
  if (state.scan && state.pano && !state.runningCalibration) {
    const result = state.pano.addFrame(
      state.raw,
      state.pose,
      sceneBase().value,
    );
    updateScan(result);
  }
  processImage();
});
camera.addEventListener("stopped", () => {
  $("live-state").classList.remove("running");
  $("live-state").innerHTML = "<i></i> OFF";
  if (!state.raw) $("welcome").hidden = false;
});
camera.addEventListener("ended", () => {
  state.reading = null;
  state.held = null;
  state.cameraEV = null;
  refresh();
  toast("相机已断开，请重新开启");
  $("welcome").hidden = false;
});
camera.addEventListener("muted", (event) => {
  $("live-state").lastChild.textContent = event.detail ? " 暂停" : " LIVE";
});

function toggleFreeze() {
  if (!state.image) {
    toast("请先开启相机");
    return;
  }
  state.frozen = !state.frozen;
  if (state.frozen) {
    warnEstimated();
    state.frozenResponse = Array.from(
      calibration()?.response || defaultResponse,
    );
    state.spots = [];
    state.nextSpot = 1;
  } else {
    state.frozenResponse = null;
    state.spots = [];
    processImage();
  }
  drawSpots();
  refresh();
}
function drawSpots() {
  $("spots").replaceChildren();
  $("spot-list").replaceChildren();
  $("spot-list").hidden = !state.frozen;
  if (!state.frozen) return;
  state.spots.forEach((point) => {
    const marker = document.createElement("span");
    marker.className = "spot";
    marker.textContent = point.id;
    marker.style.left = point.x * 100 + "%";
    marker.style.top = point.y * 100 + "%";
    marker.style.color = spotColor(point.id);
    $("spots").append(marker);
    const label = document.createElement("button");
    label.textContent = `${point.id} · ${point.ev.toFixed(1)} ${absolute() ? "EV100" : "EV"} ×`;
    label.style.color = spotColor(point.id);
    label.title = "删除测光点";
    label.addEventListener("click", () => {
      state.spots = state.spots.filter((p) => p.id !== point.id);
      drawSpots();
      drawHistogram();
    });
    $("spot-list").append(label);
  });
  const exit = document.createElement("button");
  exit.textContent = state.spots.length
    ? "退出冻结"
    : "点击画面添加测光点 · 退出";
  exit.addEventListener("click", toggleFreeze);
  $("spot-list").append(exit);
}

async function enableOrientation() {
  try {
    if (
      typeof DeviceOrientationEvent !== "undefined" &&
      typeof DeviceOrientationEvent.requestPermission === "function"
    ) {
      const permission = await DeviceOrientationEvent.requestPermission();
      if (permission !== "granted") {
        state.pose = null;
        window.removeEventListener("deviceorientation", orientationChanged);
        return false;
      }
    }
    if (!("DeviceOrientationEvent" in window)) {
      state.pose = null;
      return false;
    }
    window.addEventListener("deviceorientation", orientationChanged);
    return true;
  } catch {
    state.pose = null;
    return false;
  }
}
function orientationChanged(event) {
  state.pose = orientationMatrix(
    event.alpha,
    event.beta,
    event.gamma,
    screen.orientation?.angle || 0,
  );
}
async function startScan() {
  if (!state.raw || !state.source) {
    toast("请先开启相机并标定当前视角");
    return;
  }
  await enableOrientation();
  if (state.frozen) toggleFreeze();
  state.pano = new Panorama({
    target: target(),
    source: state.source,
    width: 720,
    height: 720,
    response: response(),
  });
  state.panoAspect = target().x / target().y;
  state.pano.setCenter(state.pose);
  state.scan = true;
  state.manualCrop = false;
  state.offset = { x: 0, y: 0 };
  $("scan-panel").hidden = false;
  $("scan-start").hidden = true;
  state.pano.addFrame(state.raw, state.pose, sceneBase().value);
  updateScan(state.pano.guide());
  processImage();
  toast("中心已确认。缓慢移动，保持画面重叠，补齐覆盖图。");
}
function updateScan(info = state.pano?.guide()) {
  if (!info) return;
  const guide = state.pano.guide();
  let direction = "范围已补齐";
  if (guide.next) {
    const dx = guide.next.x - guide.aim.x,
      dy = guide.next.y - guide.aim.y;
    direction = Math.abs(dx) > 0.03 ? (dx > 0 ? "向右" : "向左") : "";
    if (Math.abs(dy) > 0.03) direction += dy > 0 ? "向下" : "向上";
    direction = direction
      ? "缓慢" + direction + "转动"
      : "继续保持重叠，补拍边缘";
  }
  $("scan-progress").value = guide.coverage;
  $("scan-status").textContent = state.scan
    ? `${Math.round(guide.coverage * 100)}% 覆盖 · ${guide.captures} 帧 · ${guide.needsOverlap ? "请退回已采集区域恢复重叠" : direction}${state.pose ? "" : " · 图像配准"}`
    : state.manualCrop
      ? "二次构图 · 拖动调整已采集画面"
      : state.pose
        ? "二次构图 · 转动手机或拖动画面"
        : "二次构图 · 拖动画面";
  $("scan-capture").hidden = !state.scan;
  $("scan-finish").textContent = state.scan ? "完成 / 二次构图" : "继续补拍";
  const canvas = $("coverage-map"),
    ctx = canvas.getContext("2d");
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  const w = canvas.width,
    h = canvas.height;
  guide.tiles?.forEach((covered, i) => {
    ctx.fillStyle = covered ? "#6dd8a0" : "#303436";
    ctx.fillRect(
      ((i % 9) * w) / 9 + 1,
      (Math.floor(i / 9) * h) / 7 + 1,
      w / 9 - 2,
      h / 7 - 2,
    );
  });
  if (guide.next) {
    const x = clamp(guide.next.x, 0, 1) * w,
      y = clamp(guide.next.y, 0, 1) * h;
    ctx.strokeStyle = "#ffbd59";
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.arc(x, y, 5, 0, Math.PI * 2);
    ctx.stroke();
  }
  const x = clamp(guide.aim.x, 0, 1) * w,
    y = clamp(guide.aim.y, 0, 1) * h;
  ctx.strokeStyle = "#fff";
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  ctx.moveTo(x - 5, y);
  ctx.lineTo(x + 5, y);
  ctx.moveTo(x, y - 5);
  ctx.lineTo(x, y + 5);
  ctx.stroke();
  canvas.setAttribute(
    "aria-label",
    `拼接覆盖 ${Math.round(guide.coverage * 100)}%，白色十字为当前方向，黄色圆圈为待补拍区域，${direction}`,
  );
}
function cancelScan() {
  state.pano = null;
  state.scan = false;
  state.manualCrop = false;
  state.offset = { x: 0, y: 0 };
  $("scan-panel").hidden = true;
}

function nextFrame() {
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      camera.removeEventListener("frame", handle);
      reject(new Error("相机画面暂停，请重试"));
    }, 3000);
    function handle(event) {
      clearTimeout(timeout);
      resolve(event.detail);
    }
    camera.addEventListener("frame", handle, { once: true });
  });
}
async function collectBracket(seconds, token) {
  const session = calibrationSession;
  if (
    !session ||
    token !== calibrationToken ||
    session.key !== state.profileKey
  )
    throw new Error("镜头或校准流程已改变，请重新开始");
  if (!finite(seconds) || seconds <= 0)
    throw new Error("请输入本档实际快门时间");
  if (
    Number($("capture-aperture").value) !== session.aperture ||
    Number($("calibration-iso").value) !== session.iso
  )
    throw new Error("校准期间须保持同一手机光圈和 ISO，请重新开始");
  if (session.manual && !$("capture-confirmed").checked)
    throw new Error("请先确认相机已实际固定 ISO 与光圈，并调整至本档快门");
  if (
    brackets.some((b) => Math.abs(Math.log2(b.actualSeconds / seconds)) < 0.01)
  )
    throw new Error("已采集这个快门时间，请实际改变快门后再采样");
  if (session.sampling) throw new Error("正在采集本档，请稍候");
  session.sampling = true;
  $("calibration-sample").disabled = true;
  try {
    const first = await nextFrame(),
      repeat = await nextFrame();
    if (token !== calibrationToken || session.key !== state.profileKey)
      throw new Error("校准已取消或镜头已切换");
    for (const frame of [first, repeat]) {
      const settings = frame.settings || {};
      if (
        session.manual &&
        settings.exposureMode &&
        settings.exposureMode !== "manual"
      )
        throw new Error("浏览器报告相机仍在自动曝光，请实际固定曝光后重试");
      if (
        settings.iso > 0 &&
        Math.abs(Math.log2(settings.iso / session.iso)) > 0.1
      )
        throw new Error("相机实际 ISO 与校准 ISO 不一致");
      if (
        settings.exposureTime > 0 &&
        Math.abs(Math.log2(settings.exposureTime / 10000 / seconds)) > 0.15
      )
        throw new Error("相机实际快门与本档输入不一致，请等待曝光稳定后重试");
    }
    brackets.push({
      seconds:
        (seconds * session.iso) / (100 * session.aperture * session.aperture),
      actualSeconds: seconds,
      samples: sampleGrid(first.image),
      repeat: sampleGrid(repeat.image),
    });
  } finally {
    session.sampling = false;
    $("calibration-sample").disabled = false;
  }
  $("calibration-progress").textContent =
    `已采集 ${brackets.length} 档 · ${timeLabel(seconds)}`;
}
async function finishCalibration() {
  try {
    const session = calibrationSession;
    if (!session || session.key !== state.profileKey)
      throw new Error("请先在当前镜头开始一次校准");
    if (session.sampling) throw new Error("请等待本档两帧采样完成后再拟合");
    if (
      Number($("calibration-iso").value) !== session.iso ||
      Number($("capture-aperture").value) !== session.aperture
    )
      throw new Error("校准期间 ISO 或光圈已改变，请重新开始");
    const result = fitResponse(brackets, {
      method: session.method,
      step: session.step,
    });
    const iso = session.iso;
    state.profile.calibrations ||= {};
    state.profile.calibrations[number(iso)] = {
      ...result,
      response: Array.from(result.response),
      iso,
      date: new Date().toISOString(),
    };
    if (session.manual)
      Object.assign(state.profile, {
        mode: "manual",
        iso,
        aperture: session.aperture,
        seconds: brackets.at(-1).actualSeconds,
        confirmed: true,
        referenceBase: null,
      });
    persist();
    $("calibration-progress").textContent =
      `校准已保存，${result.stops.toFixed(1)} EV · 预览通道估测`;
    $("manual-calibration").hidden = true;
    refreshSettings();
    processImage();
    toast("当前镜头校准已保存");
  } catch (error) {
    $("calibration-progress").textContent = error.message;
    toast(error.message);
  }
}
async function startCalibration() {
  if (!camera.stream) {
    toast("请先开启相机");
    return;
  }
  if (state.frozen) toggleFreeze();
  cancelScan();
  const a = Number($("capture-aperture").value),
    iso = Number($("calibration-iso").value);
  if (
    !(
      finite(a) &&
      a >= 0.5 &&
      a <= 64 &&
      finite(iso) &&
      iso >= 0.001 &&
      iso <= 10000000
    )
  ) {
    toast("请填写有效的手机采集光圈和校准 ISO");
    return;
  }
  brackets = [];
  const token = ++calibrationToken;
  calibrationSession = {
    key: state.profileKey,
    aperture: a,
    iso,
    method: $("calibration-method").value,
    step: Number($("calibration-step").value),
    manual: false,
  };
  const cap = camera.capabilities;
  if (!cap.exposureMode?.includes("manual") || !cap.exposureTime) {
    calibrationSession.manual = true;
    $("manual-calibration").hidden = false;
    $("calibration-progress").textContent =
      "未开放自动扫描。请实际固定相机 ISO 与光圈，勾选“已确认相机曝光固定”，每档实际改变快门并输入对应秒数。至少采集 5 档、跨越 4 EV，每档采样两帧。仅改输入框无法完成校准。";
    return;
  }
  $("manual-calibration").hidden = true;
  state.runningCalibration = true;
  $("calibrate-start").disabled = true;
  const original = { ...camera.settings };
  const originalTrack = camera.track;
  try {
    const range = cap.exposureTime;
    if (
      !(
        finite(range.min) &&
        finite(range.max) &&
        range.min > 0 &&
        range.max / range.min >= 16
      )
    )
      throw new Error("浏览器开放的曝光跨度不足 4 EV");
    for (let i = 0; i <= 12; i++) {
      if (token !== calibrationToken) throw new Error("校准已取消");
      const seconds = (range.min * (range.max / range.min) ** (i / 12)) / 10000;
      const actual = await camera.fixedExposure(seconds, iso);
      if (Math.abs(Math.log2(actual.iso / iso)) > 0.1)
        throw new Error("浏览器未固定所选 ISO，请重新设置");
      if (i === 0) {
        calibrationSession.iso = actual.iso;
        $("calibration-iso").value = String(actual.iso);
      } else if (
        Math.abs(Math.log2(actual.iso / calibrationSession.iso)) > 0.01
      )
        throw new Error("扫描期间相机 ISO 发生变化，请重试");
      await delay(400);
      await collectBracket(actual.seconds, token);
    }
    await finishCalibration();
  } catch (error) {
    $("calibration-progress").textContent = error.message;
    toast(error.message);
  } finally {
    if (camera.track === originalTrack)
      try {
        if (original.exposureMode === "manual")
          await camera.fixedExposure(
            original.exposureTime / 10000,
            original.iso,
          );
        else
          await camera.track.applyConstraints({
            advanced: [{ exposureMode: original.exposureMode || "continuous" }],
          });
        camera.readSettings();
        if (
          state.profile.mode === "reference" &&
          original.exposureMode !== "manual"
        ) {
          state.profile.confirmed = false;
          persist();
          toast("曝光扫描已改变相机状态，请重新做已知 EV 标定");
        }
      } catch {
        state.profile.confirmed = false;
        toast("请重新确认手机采集曝光");
      }
    state.runningCalibration = false;
    $("calibrate-start").disabled = false;
  }
}

function bindEvents() {
  $("settings-toggle").addEventListener("click", openSettings);
  $("camera-shortcut").addEventListener("click", openSettings);
  $("meter-configure").addEventListener("click", openSettings);
  document
    .querySelectorAll("[data-close]")
    .forEach((b) =>
      b.addEventListener("click", () => $(b.dataset.close).close()),
    );
  $("notice-ok").addEventListener("click", () => $("notice").close());
  $("start-camera").addEventListener("click", startCamera);
  $("camera-start-settings").addEventListener("click", startCamera);
  $("camera-stop").addEventListener("click", () => {
    calibrationToken++;
    camera.stop();
    state.frozen = false;
    state.raw = null;
    state.image = null;
    state.hist = null;
    state.reading = null;
    state.cameraEV = null;
    state.held = null;
    state.spots = [];
    cancelScan();
    $("image-frame").style.display = "none";
    $("welcome").hidden = false;
    drawSpots();
    refresh();
    refreshCaptureControls();
  });
  $("format").addEventListener("change", (event) => {
    if (state.frozen) toggleFreeze();
    state.format =
      event.target.value === "custom"
        ? state.customFormat
        : FORMATS[Number(event.target.value)];
    cancelScan();
    persist();
    processImage();
  });
  $("focal").addEventListener("change", (event) => {
    const v = Number(event.target.value);
    if (v >= 1 && v <= 3000) {
      if (state.frozen) toggleFreeze();
      state.focal = v;
      cancelScan();
      persist();
      processImage();
    } else event.target.value = state.focal;
  });
  $("target-orientation").addEventListener("change", (event) => {
    if (state.frozen) toggleFreeze();
    state.orientation = event.target.value;
    cancelScan();
    persist();
    processImage();
  });
  $("set-format").addEventListener("click", () => {
    const factor = $("format-unit").value === "inch" ? 25.4 : 1,
      w = Number($("custom-width").value) * factor,
      h = Number($("custom-height").value) * factor;
    if (!(w >= 0.1 && w <= 1500 && h >= 0.1 && h <= 1500)) {
      toast("画幅尺寸须在 0.1–1500 mm 内");
      return;
    }
    if (state.frozen) toggleFreeze();
    state.customFormat = state.format = { name: "自定义", w, h };
    let option = $("format").querySelector("[value=custom]");
    if (!option) {
      option = document.createElement("option");
      option.value = "custom";
      $("format").append(option);
    }
    option.textContent = `自定义 ${number(w)} × ${number(h)}`;
    $("format").value = "custom";
    cancelScan();
    persist();
    processImage();
    toast("已选用自定义画幅");
  });
  $("capture-mode").addEventListener("change", () => {
    state.profile.mode = $("capture-mode").value;
    $("settings").classList.toggle(
      "capture-reference",
      state.profile.mode === "reference",
    );
    refreshCaptureControls(camera.settings, true);
    persist();
    processImage();
  });
  for (const id of [
    "capture-aperture",
    "capture-iso",
    "capture-time",
    "capture-confirmed",
  ])
    $(id).addEventListener("change", () => {
      captureProfileFromInputs();
      $("settings").classList.toggle(
        "capture-reference",
        state.profile.mode === "reference",
      );
    });
  $("apply-capture").addEventListener("click", async () => {
    captureProfileFromInputs();
    const p = state.profile;
    if (!(p.seconds > 0 && p.iso > 0 && p.aperture > 0)) {
      toast("请填写有效的手机光圈、ISO 和快门");
      return;
    }
    try {
      const actual = await camera.fixedExposure(p.seconds, p.iso);
      Object.assign(p, {
        mode: "fixed",
        iso: actual.iso,
        seconds: actual.seconds,
        confirmed: true,
      });
      persist();
      refreshSettings();
      processImage();
      toast("已固定手机采集曝光");
    } catch (error) {
      toast(error.message);
    }
  });
  $("set-reference").addEventListener("click", () => {
    captureProfileFromInputs();
    if (!state.raw || !state.profile.confirmed) {
      toast("请先开启相机并确认相机已固定曝光");
      return;
    }
    if (
      camera.settings.exposureMode &&
      camera.settings.exposureMode !== "manual"
    ) {
      toast("浏览器报告相机仍在自动曝光，请先实际固定曝光");
      return;
    }
    const value = Number($("reference-ev").value);
    if (!finite(value) || value < -25 || value > 40) {
      toast("参考 EV 无效");
      return;
    }
    state.profile.referenceBase =
      value - meter(histogram(state.raw), response());
    state.profile.referenceSignature = referenceSignature(
      camera.settings,
      state.profile,
    );
    state.profile.mode = "reference";
    persist();
    refreshSettings();
    processImage();
    toast("参考 EV 已标定");
  });
  $("camera-hfov").addEventListener("change", () => {
    const hfov = Number($("camera-hfov").value);
    if (hfov >= 0.1 && hfov <= 175) {
      if (state.frozen) toggleFreeze();
      state.profile.hfov = hfov;
      state.profile.optics = {
        mode: "horizontal",
        hfov,
        aperture: Number(state.profile.aperture),
        width: camera.settings.width || 1280,
        height: camera.settings.height || 720,
        zoom: camera.settings.zoom || 1,
      };
      rememberOptics();
      persist();
      cancelScan();
      refreshSettings();
      processImage();
    } else toast("水平视角须在 0.1–175° 内");
  });
  $("camera-equivalent").addEventListener("change", () => {
    const equivalentMm = Number($("camera-equivalent").value);
    if (!(equivalentMm >= 1 && equivalentMm <= 2000)) {
      toast("等效焦距须在 1–2000 mm 内");
      return;
    }
    if (state.frozen) toggleFreeze();
    cancelScan();
    state.profile.optics = {
      mode: "equivalent",
      equivalentMm,
      aperture: Number(state.profile.aperture),
      width: camera.settings.width || 1280,
      height: camera.settings.height || 720,
      zoom: camera.settings.zoom || 1,
    };
    state.profile.hfov = opticsField(
      state.profile.optics,
      state.profile.optics.width,
      state.profile.optics.height,
      state.profile.optics.zoom,
    ).hfov;
    rememberOptics();
    persist();
    refreshSettings();
    processImage();
  });
  $("configure-lens").addEventListener("click", () => lensSetup.open());
  $("calibrate-fov").addEventListener("click", () => {
    const w = Number($("scene-width").value),
      d = Number($("scene-distance").value);
    if (!(w > 0 && d > 0)) {
      toast("请填写覆盖宽度和距离");
      return;
    }
    $("camera-hfov").value = number(
      (2 * Math.atan(w / (2 * d)) * 180) / Math.PI,
    );
    $("camera-hfov").dispatchEvent(new Event("change"));
  });
  $("preview-toggle").addEventListener("click", () => {
    state.preview = !state.preview;
    if (state.preview) warnEstimated();
    persist();
    refresh();
  });
  $("spots-toggle").addEventListener("click", toggleFreeze);
  document.querySelectorAll("[data-mode]").forEach((b) =>
    b.addEventListener("click", () => {
      state.manual = { ...state.exposure };
      state.mode = b.dataset.mode;
      persist();
      refresh();
    }),
  );
  $("comp").addEventListener("input", (event) => {
    state.compensation = Number(event.target.value) / 3;
    persist();
    refresh();
  });
  $("continuous").addEventListener("click", () => {
    state.single = false;
    state.held = null;
    refresh();
  });
  $("single").addEventListener("click", () => {
    state.single = true;
    state.held = state.reading;
    refresh();
    if (!finite(state.held)) toast("绝对测光需要设置手机采集参数");
  });
  const toggleLock = () => {
    if (state.frozen) return;
    state.isoLocked = !state.isoLocked;
    if (state.isoLocked) state.manual.iso = film().iso;
    persist();
    refreshSettings();
    refresh();
  };
  $("iso-lock").addEventListener("click", toggleLock);
  $("settings-iso-lock").addEventListener("click", toggleLock);
  $("set-iso").addEventListener("click", () => {
    const value = Number($("custom-iso").value);
    if (state.isoLocked || state.frozen) return;
    if (!(value >= 0.001 && value <= 10000000)) {
      toast("ISO 须为 0.001–10000000");
      return;
    }
    state.manual.iso = value;
    persist();
    refresh();
  });
  $("film-search").addEventListener("input", renderFilmList);
  $("film-select").addEventListener("change", (e) =>
    chooseFilm(e.target.value),
  );
  $("color-style").addEventListener("change", (e) => {
    state.style = e.target.value;
    persist();
    refreshSettings();
    refresh();
  });
  $("log-counts").addEventListener("change", (e) => {
    state.log = e.target.checked;
    persist();
    drawHistogram();
  });
  $("hist-reset").addEventListener("click", () => {
    state.histWindow = null;
    drawHistogram();
  });
  $("resize-layout").addEventListener("change", (e) =>
    $("instrument").classList.toggle("resizing", e.target.checked),
  );
  $("layout-reset").addEventListener("click", () => {
    delete state.layouts[state.layoutKey];
    persist();
    layout();
  });
  $("settings-reset").addEventListener("click", resetSettings);
  $("scan-start").addEventListener("click", startScan);
  $("scan-cancel").addEventListener("click", () => {
    cancelScan();
    processImage();
  });
  $("scan-capture").addEventListener("click", () => {
    if (state.raw && state.pano)
      updateScan(state.pano.addFrame(state.raw, state.pose, sceneBase().value));
  });
  $("scan-finish").addEventListener("click", () => {
    if (!state.pano) return;
    state.scan = !state.scan;
    updateScan();
    processImage();
    if (!state.scan && state.pano.guide().coverage < 0.98)
      toast("覆盖尚有空缺，可继续补拍");
  });
  $("calibrate-start").addEventListener("click", startCalibration);
  $("calibration-sample").addEventListener("click", async () => {
    const t = parseTime($("bracket-time").value);
    if (!finite(t)) {
      toast("快门格式为秒数或 1/125");
      return;
    }
    try {
      await collectBracket(t, calibrationToken);
    } catch (e) {
      toast(e.message);
    }
  });
  $("calibration-fit").addEventListener("click", finishCalibration);
  $("calibration-cancel").addEventListener("click", () => {
    calibrationToken++;
    brackets = [];
    $("manual-calibration").hidden = true;
  });
  $("calibration-clear").addEventListener("click", () => {
    const cal = calibration();
    if (cal) delete state.profile.calibrations[number(cal.iso)];
    persist();
    refreshSettings();
    processImage();
  });
  bindPresets();
  bindGestures();
  window.addEventListener("resize", layout);
  new ResizeObserver(() => {
    drawHistogram();
    if (state.image) fitView(state.image);
    if (state.exposure) renderPairs();
  }).observe($("panels"));
  document.addEventListener("visibilitychange", () => {
    camera.pause(document.hidden);
    if (state.pano) state.pano.resumeTracking();
  });
  window.addEventListener("pagehide", () => camera.stop());
}

function bindPresets() {
  $("preset-new").addEventListener("click", () => {
    editingPreset = null;
    fillPreset();
  });
  $("preset-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const preset = {
      id: editingPreset || crypto.randomUUID(),
      name: $("preset-name").value.trim(),
      digital: $("preset-type").value === "digital",
      total: Number($("preset-total").value),
      above: Number($("preset-above").value),
      iso: Number($("preset-iso").value),
    };
    try {
      CustomPresets.decode(CustomPresets.encode([preset]));
      state.presets = CustomPresets.merge(state.presets, [preset]);
      chooseFilm(preset.id);
      toast("预设已保存并选用");
    } catch (error) {
      toast(error.message);
    }
  });
  $("presets-export").addEventListener("click", () => {
    const blob = new Blob([CustomPresets.encode(state.presets)], {
        type: "application/json;charset=utf-8",
      }),
      url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "LightData-presets.json";
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  });
  $("presets-import").addEventListener("click", () =>
    $("presets-file").click(),
  );
  $("presets-file").addEventListener("change", async (event) => {
    const file = event.target.files[0];
    if (!file) return;
    try {
      if (file.size > 2 * 1024 * 1024) throw new Error("预设文件超过 2 MiB");
      const presets = CustomPresets.decode(await file.text());
      state.presets = CustomPresets.merge(state.presets, presets);
      if (presets.some((p) => p.id === state.filmId)) {
        if (state.isoLocked) state.manual.iso = film().iso;
      }
      persist();
      refreshSettings();
      refresh();
      $("preset-status").textContent =
        `已读取 ${presets.length} 个预设，可与安卓版互通`;
      toast("预设已导入");
    } catch (error) {
      toast(error.message);
    } finally {
      event.target.value = "";
    }
  });
}

function bindGestures() {
  for (const [id, which] of [
    ["split-main", 0],
    ["split-secondary", 1],
  ]) {
    const separator = $(id);
    let origin;
    separator.addEventListener("pointerdown", (event) => {
      if (!$("resize-layout").checked) return;
      separator.setPointerCapture(event.pointerId);
      origin = {
        x: event.clientX,
        y: event.clientY,
        values: [
          ...(state.layouts[state.layoutKey] || [
            state.layoutKey.startsWith("square") ? 0.5 : 0.6,
            state.layoutKey.startsWith("square") ? 0.5 : 0.46,
          ]),
        ],
      };
    });
    separator.addEventListener("pointermove", (event) => {
      if (!origin) return;
      const horizontal = which === 0 ? state.side : !state.side,
        rect = $(
          which === 0 ? "panels" : "instruments",
        ).getBoundingClientRect(),
        delta = horizontal
          ? (event.clientX - origin.x) / rect.width
          : (event.clientY - origin.y) / rect.height;
      const values = [...origin.values];
      values[which] = clamp(values[which] + delta, 0.22, 0.78);
      state.layouts[state.layoutKey] = values;
      document.documentElement.style.setProperty(
        which === 0 ? "--p" : "--q",
        values[which] * 100 + "%",
      );
      refresh();
    });
    const finish = () => {
      if (origin) {
        origin = null;
        persist();
      }
    };
    separator.addEventListener("pointerup", finish);
    separator.addEventListener("pointercancel", finish);
    separator.addEventListener("keydown", (event) => {
      if (
        !["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown"].includes(event.key)
      )
        return;
      event.preventDefault();
      const values = [...(state.layouts[state.layoutKey] || [0.6, 0.46])];
      values[which] = clamp(
        values[which] +
          (["ArrowLeft", "ArrowUp"].includes(event.key) ? -0.02 : 0.02),
        0.22,
        0.78,
      );
      state.layouts[state.layoutKey] = values;
      persist();
      layout();
    });
  }
  const pointers = new Map();
  let gesture;
  const snapshot = () => {
    const ps = [...pointers.values()],
      a = ps[0],
      b = ps[1];
    return {
      cx: b ? (a.x + b.x) / 2 : a.x,
      cy: b ? (a.y + b.y) / 2 : a.y,
      dx: b ? Math.abs(a.x - b.x) : 0,
      dy: b ? Math.abs(a.y - b.y) : 0,
    };
  };
  $("chart").addEventListener("pointerdown", (event) => {
    event.preventDefault();
    $("chart").setPointerCapture(event.pointerId);
    pointers.set(event.pointerId, { x: event.offsetX, y: event.offsetY });
    gesture = snapshot();
    if (!state.histWindow) state.histWindow = { ...state.plot.axis };
  });
  $("chart").addEventListener("pointermove", (event) => {
    if (!pointers.has(event.pointerId)) return;
    pointers.set(event.pointerId, { x: event.offsetX, y: event.offsetY });
    const next = snapshot(),
      axis = state.histWindow,
      width = state.plot.w;
    let span = axis.span;
    if (pointers.size >= 2) {
      if (gesture.dx > 12 && next.dx > 12)
        span = clamp((axis.span * gesture.dx) / next.dx, 2, 64);
      if (gesture.dy > 12 && next.dy > 12)
        axis.gain = clamp((axis.gain * next.dy) / gesture.dy, 0.25, 16);
    }
    const anchor = clamp((gesture.cx - state.plot.x) / width, 0, 1);
    axis.left = clamp(
      axis.left +
        axis.span * anchor -
        span * (anchor + (next.cx - gesture.cx) / width),
      -64,
      64 - span,
    );
    axis.span = span;
    gesture = next;
    drawHistogram();
  });
  const release = (event) => {
    pointers.delete(event.pointerId);
    gesture = pointers.size ? snapshot() : null;
  };
  $("chart").addEventListener("pointerup", release);
  $("chart").addEventListener("pointercancel", release);
  $("chart").addEventListener("dblclick", () => {
    state.histWindow = null;
    drawHistogram();
  });
  $("chart").addEventListener(
    "wheel",
    (event) => {
      event.preventDefault();
      const axis = state.histWindow || { ...state.plot.axis };
      const span = clamp(axis.span * Math.exp(event.deltaY * 0.002), 2, 64),
        anchor = clamp((event.offsetX - state.plot.x) / state.plot.w, 0, 1);
      axis.left += axis.span * anchor - span * anchor;
      axis.span = span;
      state.histWindow = axis;
      drawHistogram();
    },
    { passive: false },
  );
  let viewDown;
  $("view").addEventListener("pointerdown", (e) => {
    viewDown = { x: e.clientX, y: e.clientY, offset: { ...state.offset } };
    $("view").setPointerCapture(e.pointerId);
  });
  $("view").addEventListener("pointermove", (e) => {
    if (!viewDown || !state.pano || state.scan || state.frozen) return;
    const rect = $("view").getBoundingClientRect();
    state.manualCrop = true;
    state.offset = {
      x: clamp(
        viewDown.offset.x - ((e.clientX - viewDown.x) / rect.width) * 2,
        -1,
        1,
      ),
      y: clamp(
        viewDown.offset.y - ((e.clientY - viewDown.y) / rect.height) * 2,
        -1,
        1,
      ),
    };
    updateScan();
    processImage();
  });
  $("view").addEventListener("pointerup", (e) => {
    if (
      viewDown &&
      state.frozen &&
      Math.hypot(e.clientX - viewDown.x, e.clientY - viewDown.y) < 8
    ) {
      const r = $("view").getBoundingClientRect(),
        x = clamp((e.clientX - r.left) / r.width, 0, 1),
        y = clamp((e.clientY - r.top) / r.height, 0, 1),
        ev = spot(state.image, x, y, response(), state.cameraEV || 0);
      if (finite(ev)) {
        state.spots.push({ id: state.nextSpot++, x, y, ev });
        drawSpots();
        drawHistogram();
        fitView(state.image);
      } else toast("此处尚未采集，请先补拍");
    }
    viewDown = null;
  });
  $("view").addEventListener("pointercancel", () => (viewDown = null));
  function scrub(element, isIso) {
    let drag;
    let remainder = 0;
    element.addEventListener("pointerdown", (e) => {
      if (state.frozen) return;
      drag = {
        x: e.clientX,
        row:
          e.clientY - element.getBoundingClientRect().top >
          element.clientHeight / 2,
      };
      remainder = 0;
    });
    element.addEventListener("pointermove", (e) => {
      if (!drag) return;
      const delta = e.clientX - drag.x;
      drag.x = e.clientX;
      remainder -= delta;
      const steps = Math.trunc(remainder / 20);
      if (!steps) return;
      element.setPointerCapture(e.pointerId);
      remainder -= steps * 20;
      state.lastSwipe = performance.now();
      if (isIso) {
        if (state.isoLocked) return;
        const values = [...new Set([...ISOS, state.manual.iso])].sort(
          (a, b) => a - b,
        );
        state.manual.iso =
          values[
            clamp(
              values.indexOf(state.manual.iso) + steps,
              0,
              values.length - 1,
            )
          ];
      } else {
        const aperture = state.mode === "A" || (state.mode === "M" && drag.row),
          values = aperture ? APERTURES : SHUTTERS,
          key = aperture ? "a" : "t";
        state.manual[key] =
          values[
            clamp(
              values.indexOf(closest(values, state.exposure[key])) + steps,
              0,
              values.length - 1,
            )
          ];
      }
      persist();
      refresh();
    });
    const end = () => (drag = null);
    element.addEventListener("pointerup", end);
    element.addEventListener("pointercancel", end);
  }
  scrub($("exposure-table"), false);
  scrub($("iso-wheel"), true);
}

async function initialize() {
  bindEvents();
  const lensReady = lensSetup.initialize();
  FORMATS.forEach((format, index) => {
    const option = document.createElement("option");
    option.value = index;
    option.textContent = format.name;
    option.title = `${format.w} × ${format.h} mm`;
    option.selected = format.name === state.format.name;
    $("format").append(option);
  });
  if (state.customFormat) {
    const option = document.createElement("option");
    option.value = "custom";
    option.textContent = `自定义 ${number(state.customFormat.w)} × ${number(state.customFormat.h)}`;
    option.selected = state.format.name === "自定义";
    $("format").append(option);
  }
  $("focal").value = state.focal;
  try {
    state.films = await loadFilms();
    if (!allFilms().some((f) => f.id === state.filmId))
      state.filmId =
        state.films.find((f) => f.name === "HP5 PLUS")?.id || state.films[0].id;
    if (state.isoLocked) state.manual.iso = film().iso;
    state.exposure = { ...state.manual };
    fillPreset();
    await lensReady;
    if (saved.histogramSettingsVersion !== HISTOGRAM_SETTINGS_VERSION)
      persist();
    refreshSettings();
    layout();
    refresh();
    if (camera.stream) lensSetup.consider();
    else if (
      !Object.values(state.lenses).some(completeOptics) &&
      !Object.values(state.devices).some((p) =>
        makeOptics(
          { mode: "horizontal", aperture: p.aperture, hfov: p.hfov },
          {},
        ),
      )
    )
      lensSetup.open();
  } catch (error) {
    $("start-message").textContent = "胶片数据加载失败，请刷新页面重试。";
    toast(error.message);
  }
}
initialize();
