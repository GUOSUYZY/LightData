// Device matching is local. Generic / reduced UAs never identify an iPhone generation.
const normalize = (value) =>
  String(value || "")
    .trim()
    .toLowerCase()
    .replace(/[\s_-]+/g, " ");
const validAperture = (value) =>
  Number.isFinite(Number(value)) && Number(value) >= 0.5 && Number(value) <= 64;
const validAngle = (value) =>
  Number.isFinite(Number(value)) &&
  Number(value) >= 0.1 &&
  Number(value) <= 175;
const validFocal = (value) =>
  Number.isFinite(Number(value)) && Number(value) >= 1 && Number(value) <= 2000;
const radians = (degrees) => (degrees * Math.PI) / 180;
const degrees = (angle) => (angle * 180) / Math.PI;

export function modelFromUA(ua) {
  const android = String(ua || "").match(/Android[^;)]*;\s*([^)]*)/i)?.[1];
  if (!android) return "";
  const parts = android
    .split(";")
    .map((v) => v.trim())
    .filter((v) => v && !/^(wv|[a-z]{2}[-_][a-z]{2})$/i.test(v));
  if (!parts.length) return "";
  const model = parts
    .at(-1)
    .replace(/\s+Build\/.*$/i, "")
    .replace(/\s+wv$/i, "")
    .trim();
  return /^(K|wv|[a-z]{2}[-_][a-z]{2})$/i.test(model) ? "" : model;
}

export async function deviceIdentity(nav = globalThis.navigator) {
  let timer;
  try {
    const hints = await Promise.race([
      nav.userAgentData?.getHighEntropyValues?.(["model", "platform"]),
      new Promise((resolve) => {
        timer = setTimeout(() => resolve(null), 1200);
      }),
    ]);
    if (hints?.model?.trim())
      return { model: hints.model.trim(), source: "UA Client Hints" };
  } catch {
    // Safari, privacy restrictions, and denied high-entropy hints use the UA fallback.
  } finally {
    clearTimeout(timer);
  }
  const model = modelFromUA(nav?.userAgent);
  return { model, source: model ? "User-Agent" : "" };
}

export function matchDevice(devices, identity) {
  const model = normalize(identity?.model);
  if (!model) return null;
  const hardwareCode = identity.model.replace(
    /^(?:samsung|huawei|honor|xiaomi|oppo|vivo)\s+/i,
    "",
  );
  const matches = devices.filter(
    (device) =>
      [device.name, device.nameEn, ...(device.aliases || [])].some(
        (name) => normalize(name) === model,
      ) ||
      (device.modelPatterns || []).some((pattern) =>
        new RegExp(pattern, "i").test(hardwareCode),
      ),
  );
  return matches.length === 1 ? matches[0] : null;
}

// Only descriptive camera labels count. IDs such as "camera2 0" cannot name a lens.
export function matchLens(device, label, facingMode) {
  const text = String(label || "").toLowerCase();
  if (/\b(dual|triple|logical|virtual)\b|多摄|双摄|三摄/.test(text))
    return null;
  const front =
    /front|user|facetime|前置|前摄/.test(text) || facingMode === "user";
  let role;
  if (/ultra[\s_-]*wide|超广角|超广/.test(text)) role = "ultrawide";
  else if (/telephoto|tele\b|长焦|望远/.test(text)) role = "telephoto";
  else if (/\bwide\b|\bmain\b|主摄|广角/.test(text)) role = "wide";
  if (!front && !role) return null;
  const candidates = device.lenses.filter(
    (lens) =>
      (lens.facing || "environment") === (front ? "user" : "environment") &&
      (!role || lens.role === role),
  );
  return candidates.length === 1 ? candidates[0] : null;
}

export function lensEquivalent(lens) {
  if (validFocal(lens?.equivalentMm)) return Number(lens.equivalentMm);
  if (validAngle(lens?.diagonalFov))
    return Math.hypot(36, 24) / (2 * Math.tan(radians(lens.diagonalFov / 2)));
  return null;
}

export function completeOptics(optics) {
  return (
    !!optics &&
    validAperture(optics.aperture) &&
    (optics.mode === "equivalent"
      ? validFocal(optics.equivalentMm)
      : optics.mode === "horizontal" && validAngle(optics.hfov))
  );
}

// Equivalent focal lengths refer to the full 4:3 phone sensor diagonal. Estimate
// the active preview by a centered crop, then apply the browser's zoom factor.
// Unknown electronic stabilization / vendor crops still require measured HFOV.
export function opticsField(optics, width, height, zoom = 1) {
  if (
    !optics ||
    ![width, height, zoom].every((n) => Number.isFinite(n) && n > 0)
  )
    return null;
  let long, short;
  if (optics.mode === "equivalent" && validFocal(optics.equivalentMm)) {
    const diagonal = Math.hypot(36, 24) / (2 * Number(optics.equivalentMm));
    long = diagonal * 0.8;
    short = diagonal * 0.6;
    long /= zoom;
    short /= zoom;
  } else if (
    optics.mode === "horizontal" &&
    validAngle(optics.hfov) &&
    optics.width > 0 &&
    optics.height > 0
  ) {
    const x = Math.tan(radians(Number(optics.hfov) / 2));
    const y = (x * optics.height) / optics.width;
    const ratio = (optics.zoom > 0 ? optics.zoom : 1) / zoom;
    long = Math.max(x, y) * ratio;
    short = Math.min(x, y) * ratio;
  } else return null;
  const aspect = Math.max(width, height) / Math.min(width, height);
  // Reuse measured fields conservatively: never invent pixels beyond the measured area.
  if (aspect >= long / short) short = long / aspect;
  else long = short * aspect;
  const x = width >= height ? long : short;
  const y = width >= height ? short : long;
  return {
    x,
    y,
    hfov: degrees(2 * Math.atan(x)),
    vfov: degrees(2 * Math.atan(y)),
  };
}

export function makeOptics(values, settings = {}) {
  const optics = {
    mode: values.mode === "equivalent" ? "equivalent" : "horizontal",
    aperture: Number(values.aperture),
    width: Number(settings.width) || 1280,
    height: Number(settings.height) || 720,
    zoom: Number(settings.zoom) || 1,
    phoneId: values.phoneId || "",
    lensId: values.lensId || "",
  };
  if (optics.mode === "equivalent")
    optics.equivalentMm = Number(values.equivalentMm);
  else optics.hfov = Number(values.hfov);
  return completeOptics(optics) ? optics : null;
}
