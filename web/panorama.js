import { t } from "./i18n.js";
// Rectilinear atlas. Geometry uses rays (x, y, -1) and camera-to-world matrices.
const IDENTITY = Object.freeze([1, 0, 0, 0, 1, 0, 0, 0, 1]);
const MARGIN = 1.15;
const clamp = (v, low, high) => Math.max(low, Math.min(high, v));
const toLinear = (v) =>
  v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
const toSrgb = (v) =>
  v <= 0.0031308 ? 12.92 * v : 1.055 * v ** (1 / 2.4) - 0.055;
const linearCodes = Float64Array.from({ length: 256 }, (_, i) =>
  toLinear(i / 255),
);
const grayCodes = Uint8ClampedArray.from({ length: 65536 }, (_, i) =>
  Math.round(toSrgb(i / 65535) * 255),
);
const defaultResponse = Float64Array.from({ length: 256 }, (_, i) =>
  Math.log2(toLinear(Math.max(0.5, i) / 255) / 0.18),
);

function luminance(data, i) {
  return grayCodes[
    Math.round(
      (0.2126 * linearCodes[data[i]] +
        0.7152 * linearCodes[data[i + 1]] +
        0.0722 * linearCodes[data[i + 2]]) *
        65535,
    )
  ];
}

function multiply(a, b) {
  return Array.from(
    { length: 9 },
    (_, i) =>
      a[Math.floor(i / 3) * 3] * b[i % 3] +
      a[Math.floor(i / 3) * 3 + 1] * b[(i % 3) + 3] +
      a[Math.floor(i / 3) * 3 + 2] * b[(i % 3) + 6],
  );
}

function relative(to, from) {
  return Array.from(
    { length: 9 },
    (_, i) =>
      to[Math.floor(i / 3)] * from[i % 3] +
      to[Math.floor(i / 3) + 3] * from[(i % 3) + 3] +
      to[Math.floor(i / 3) + 6] * from[(i % 3) + 6],
  );
}

export function yawPitch(yaw, pitch = 0) {
  const a = Math.cos(yaw),
    b = Math.sin(yaw),
    c = Math.cos(pitch),
    d = Math.sin(pitch);
  return [a, b * d, b * c, 0, c, -d, -b, a * d, a * c];
}

/** W3C DeviceOrientation Z-X'-Y'' angles, corrected for screen orientation. */
export function orientationMatrix(alpha, beta, gamma, screenAngle = 0) {
  if (![alpha, beta, gamma, screenAngle].every(Number.isFinite)) return null;
  const rad = Math.PI / 180;
  const a = alpha * rad,
    b = beta * rad,
    c = gamma * rad;
  const z = [
    Math.cos(a),
    -Math.sin(a),
    0,
    Math.sin(a),
    Math.cos(a),
    0,
    0,
    0,
    1,
  ];
  const x = [
    1,
    0,
    0,
    0,
    Math.cos(b),
    -Math.sin(b),
    0,
    Math.sin(b),
    Math.cos(b),
  ];
  const y = [
    Math.cos(c),
    0,
    Math.sin(c),
    0,
    1,
    0,
    -Math.sin(c),
    0,
    Math.cos(c),
  ];
  const angle = screenAngle * rad;
  const display = [
    Math.cos(angle),
    Math.sin(angle),
    0,
    -Math.sin(angle),
    Math.cos(angle),
    0,
    0,
    0,
    1,
  ];
  return multiply(multiply(multiply(z, x), y), display);
}

function imageData(data, width, height) {
  return { data, width, height };
}
function validPose(pose) {
  return pose?.length === 9 && Array.from(pose).every(Number.isFinite);
}
function moved(a, b, degrees) {
  return a.reduce((sum, v, i) => sum + v * b[i], 0) <
    1 + 2 * Math.cos(degrees * Math.PI / 180);
}
export function turnPoint(point, degrees = 0) {
  if (!point) return null;
  switch ((degrees % 360 + 360) % 360) {
    case 90: return { x: 1 - point.y, y: point.x };
    case 180: return { x: 1 - point.x, y: 1 - point.y };
    case 270: return { x: point.y, y: 1 - point.x };
    default: return { ...point };
  }
}
/** Normalize a browser-rotated video frame to the axes used at confirmation. */
export function turnFrame(frame, degrees = 0) {
  const turn = (degrees % 360 + 360) % 360;
  if (!turn) return frame;
  const transpose = turn === 90 || turn === 270;
  const width = transpose ? frame.height : frame.width;
  const height = transpose ? frame.width : frame.height;
  const data = new Uint8ClampedArray(frame.data.length);
  const input = new Uint32Array(frame.data.buffer, frame.data.byteOffset, frame.width * frame.height);
  const output = new Uint32Array(data.buffer);
  for (let y = 0; y < frame.height; y++)
    for (let x = 0; x < frame.width; x++) {
      const dx = turn === 90 ? frame.height - 1 - y : turn === 180 ? frame.width - 1 - x : y;
      const dy = turn === 90 ? x : turn === 180 ? frame.height - 1 - y : frame.width - 1 - x;
      output[dy * width + dx] = input[y * frame.width + x];
    }
  return imageData(data, width, height);
}
function validField(field) {
  return (
    field &&
    Number.isFinite(field.x) &&
    Number.isFinite(field.y) &&
    field.x > 0 &&
    field.y > 0
  );
}

/** Direct source crop, retaining unobserved target areas as transparent pixels. */
export function cropFrame(frame, source, target, maxEdge = 720) {
  if (!validField(source) || !validField(target))
    throw new Error(t("需要有效的相机和目标视角"));
  const wide = (frame.width * target.x) / source.x;
  const high = (frame.height * target.y) / source.y;
  const scale = Math.min(1, maxEdge / Math.max(wide, high));
  const width = Math.max(1, Math.round(wide * scale));
  const height = Math.max(1, Math.round(high * scale));
  const data = new Uint8ClampedArray(width * height * 4);
  for (let y = 0; y < height; y++) {
    const sy = Math.floor(
      ((y + 0.5) / height - 0.5) * high + frame.height * 0.5,
    );
    if (sy < 0 || sy >= frame.height) continue;
    for (let x = 0; x < width; x++) {
      const sx = Math.floor(
        ((x + 0.5) / width - 0.5) * wide + frame.width * 0.5,
      );
      if (sx < 0 || sx >= frame.width) continue;
      const a = (sy * frame.width + sx) * 4,
        b = (y * width + x) * 4;
      data[b] = frame.data[a];
      data[b + 1] = frame.data[a + 1];
      data[b + 2] = frame.data[a + 2];
      data[b + 3] = frame.data[a + 3];
    }
  }
  return imageData(data, width, height);
}

/** Exact linear-light color transformation shared by registration and atlas writing. */
export function scaleColor(r, g, b, gain) {
  return [r, g, b].map(
    (v) =>
      grayCodes[clamp(Math.round(linearCodes[v] * gain * 65535), 0, 65535)],
  );
}

class Projection {
  constructor(matrix, source, width, height) {
    this.r = matrix;
    this.width = width;
    this.height = height;
    this.fx = width / (2 * source.x);
    this.fy = height / (2 * source.y);
  }
  project(x, y) {
    const r = this.r;
    const depth = r[8] - r[6] * x - r[7] * y;
    if (depth <= 0.001) return null;
    return [
      this.width * 0.5 + ((r[0] * x + r[1] * y - r[2]) * this.fx) / depth,
      this.height * 0.5 - ((r[3] * x + r[4] * y - r[5]) * this.fy) / depth,
    ];
  }
}

class TrackingImage {
  constructor(frame) {
    this.width = Math.min(112, frame.width);
    this.height = clamp(
      Math.round((this.width * frame.height) / frame.width),
      1,
      160,
    );
    this.data = new Uint8ClampedArray(this.width * this.height);
    for (let y = 0; y < this.height; y++)
      for (let x = 0; x < this.width; x++) {
        const px = Math.min(
          frame.width - 1,
          Math.floor(((x + 0.5) * frame.width) / this.width),
        );
        const py = Math.min(
          frame.height - 1,
          Math.floor(((y + 0.5) * frame.height) / this.height),
        );
        this.data[y * this.width + x] = luminance(
          frame.data,
          (py * frame.width + px) * 4,
        );
      }
  }
  motionTo(next, field) {
    const { width, height, data } = this;
    if (
      width !== next.width ||
      height !== next.height ||
      Math.min(width, height) < 16
    )
      return null;
    // An unchanged low-texture center still permits the first stable capture.
    // It contributes no motion and therefore cannot grow the atlas on its own.
    let difference = 0, total = 0, squared = 0;
    for (let i = 0; i < data.length; i++) {
      difference += Math.abs(data[i] - next.data[i]);
      total += data[i];
      squared += data[i] * data[i];
    }
    if (difference < data.length * .01 || (difference < data.length * 1.2 &&
        squared / data.length - (total / data.length) ** 2 < 9))
      return { matrix: [...IDENTITY], confidence: 1 };
    const fx = width / (2 * field.x),
      fy = height / (2 * field.y);
    const step = Math.max(2, Math.floor(Math.min(width, height) / 14));
    const rotation = (dx, dy) =>
      yawPitch(Math.atan(dx / fx), Math.atan(dy / fy));
    const score = (dx, dy) => {
      const r = rotation(dx, dy);
      const regions = Array.from({ length: 9 }, () => new Float64Array(6));
      for (let y = Math.floor(step / 2); y < height; y += step) {
        for (let x = Math.floor(step / 2); x < width; x += step) {
          const u = (x + 0.5 - width * 0.5) / fx,
            v = (height * 0.5 - y - 0.5) / fy;
          const z = r[8] - r[2] * u - r[5] * v;
          if (z <= 0) continue;
          const px =
            width * 0.5 - 0.5 + ((r[0] * u + r[3] * v - r[6]) * fx) / z;
          const py =
            height * 0.5 - 0.5 - ((r[1] * u + r[4] * v - r[7]) * fy) / z;
          if (px < 0 || py < 0 || px >= width - 1 || py >= height - 1) continue;
          const ix = Math.floor(px),
            iy = Math.floor(py),
            tx = px - ix,
            ty = py - iy;
          const index = iy * width + ix;
          const top = next.data[index] * (1 - tx) + next.data[index + 1] * tx;
          const bottom =
            next.data[index + width] * (1 - tx) +
            next.data[index + width + 1] * tx;
          const a = data[y * width + x],
            b = top * (1 - ty) + bottom * ty;
          const stats =
            regions[
              Math.floor((y * 3) / height) * 3 + Math.floor((x * 3) / width)
            ];
          stats[0]++;
          stats[1] += a;
          stats[2] += b;
          stats[3] += a * a;
          stats[4] += b * b;
          stats[5] += a * b;
        }
      }
      const scores = regions
        .flatMap((s) => {
          const count = s[0];
          if (count < 8) return [];
          const varA = s[3] - (s[1] * s[1]) / count,
            varB = s[4] - (s[2] * s[2]) / count;
          if (Math.min(varA, varB) < 9 * count) return [];
          return [(s[5] - (s[1] * s[2]) / count) / Math.sqrt(varA * varB)];
        })
        .sort((a, b) => a - b);
      return scores.length >= 4 ? scores[Math.floor(scores.length / 2)] : -1;
    };
    let bx = 0,
      by = 0,
      best = score(0, 0);
    if (best > 0.985) return { matrix: rotation(0, 0), confidence: best };
    const evaluate = (x, y) => {
      const value = score(x, y) - 0.0003 * (Math.abs(x) + Math.abs(y));
      if (value > best) {
        best = value;
        bx = x;
        by = y;
      }
    };
    const radius = clamp(Math.floor(Math.min(width, height) / 5), 4, 24);
    for (let y = -radius; y <= radius; y += 3)
      for (let x = -radius; x <= radius; x += 3) evaluate(x, y);
    const cx = bx,
      cy = by;
    for (let y = -3; y <= 3; y++)
      for (let x = -3; x <= 3; x++) evaluate(cx + x, cy + y);
    const fineX = bx,
      fineY = by;
    for (let y = -2; y <= 2; y++)
      for (let x = -2; x <= 2; x++)
        evaluate(fineX + x * 0.35, fineY + y * 0.35);
    return best > 0.85 ? { matrix: rotation(bx, by), confidence: best } : null;
  }
}

export class Panorama {
  constructor({
    target,
    source,
    width = 720,
    height = width,
    response = defaultResponse,
  }) {
    if (!validField(source) || !validField(target))
      throw new Error(t("拼接需要有效的相机和目标视角"));
    this.target = { ...target };
    this.source = { ...source };
    this.bounds = { x: target.x * MARGIN, y: target.y * MARGIN };
    const scale = Math.min(
      clamp(width, 64, 1440) / this.bounds.x,
      clamp(height, 64, 1440) / this.bounds.y,
    );
    this.width = Math.max(1, Math.round(this.bounds.x * scale));
    this.height = Math.max(1, Math.round(this.bounds.y * scale));
    this.pixels = new Uint8ClampedArray(this.width * this.height * 4);
    this.response = response;
    this.xs = Float64Array.from(
      { length: this.width },
      (_, x) => ((2 * (x + 0.5)) / this.width - 1) * this.bounds.x,
    );
    this.ys = Float64Array.from(
      { length: this.height },
      (_, y) => (1 - (2 * (y + 0.5)) / this.height) * this.bounds.y,
    );
    this.count = 0;
    this.captures = 0;
    this.cameraEV = null;
    this.orientation = [...IDENTITY];
    this.sensorOrigin = null;
    this.originPose = [...IDENTITY];
    this.reference = null;
    this.needsOverlap = false;
    this.lastAttempt = null;
    this.lastRegistration = null;
    this.anchor = null;
    this.lastTime = null;
    this.lastTrackTime = null;
    this.settling = true;
    this.disconnected = false;
    this.views = new Map();
    this.refreshTiles();
  }
  get coverage() {
    return this.count / (this.width * this.height);
  }
  get ready() {
    return this.count === this.width * this.height;
  }
  setCenter(pose = null) {
    this.orientation = [...IDENTITY];
    this.originPose = [...IDENTITY];
    this.sensorOrigin = validPose(pose) ? Array.from(pose) : null;
    this.reference = null;
    this.anchor = null;
    this.lastTime = this.lastTrackTime = null;
    this.views.clear();
  }
  resumeTracking() {
    this.sensorOrigin = null;
    this.reference = null;
    this.anchor = null;
    this.lastTime = this.lastTrackTime = null;
    this.settling = true;
  }

  gains(delta) {
    if (Math.abs(delta) < 0.002) return new Float64Array(256).fill(1);
    const calibrated = this.response.some(
      (v, i) => Math.abs(v - defaultResponse[i]) > 1e-5,
    );
    if (!calibrated) return new Float64Array(256).fill(2 ** delta);
    return Float64Array.from({ length: 256 }, (_, y) => {
      const desired = this.response[y] + delta;
      let left = 0,
        right = 255;
      while (left < right) {
        const middle = (left + right) >>> 1;
        if (this.response[middle] < desired) left = middle + 1;
        else right = middle;
      }
      const below = Math.max(0, left - 1),
        span = this.response[left] - this.response[below];
      const fraction =
        span > 0 ? clamp((desired - this.response[below]) / span, 0, 1) : 0;
      return (
        toLinear((below + fraction) / 255) / toLinear(Math.max(0.5, y) / 255)
      );
    });
  }

  register(frame, projection, gain) {
    if (this.count === 0) return { x: 0, y: 0, matched: false };
    const radius = clamp(
      Math.floor(Math.min(frame.width, frame.height) / 25),
      4,
      20,
    );
    const positions = [],
      expected = [];
    const step = Math.max(
      4,
      Math.floor(Math.min(this.width, this.height) / 28),
    );
    for (let y = Math.floor(step / 2); y < this.height - 2; y += step) {
      for (let x = Math.floor(step / 2); x < this.width - 2; x += step) {
        const i = (y * this.width + x) * 4;
        if (
          !this.pixels[i + 3] ||
          !this.pixels[i + 11] ||
          !this.pixels[i + 8 * this.width + 3]
        )
          continue;
        const value = luminance(this.pixels, i);
        if (
          Math.max(
            Math.abs(value - luminance(this.pixels, i + 8)),
            Math.abs(value - luminance(this.pixels, i + 8 * this.width)),
          ) < 5
        )
          continue;
        const position = projection.project(this.xs[x], this.ys[y]);
        if (!position) continue;
        const u = Math.floor(position[0]),
          v = Math.floor(position[1]);
        if (
          u < radius ||
          v < radius ||
          u >= frame.width - radius ||
          v >= frame.height - radius
        )
          continue;
        positions.push((v * frame.width + u) * 4);
        expected.push(value);
      }
    }
    if (positions.length < 12) return { x: 0, y: 0, matched: false };
    // Use the same per-RGB-channel transform below as writeFrame, including clipping.
    const transformed = new Uint8ClampedArray(frame.width * frame.height);
    for (let p = 0; p < transformed.length; p++) {
      const i = p * 4,
        g = gain[luminance(frame.data, i)];
      const color = scaleColor(
        frame.data[i],
        frame.data[i + 1],
        frame.data[i + 2],
        g,
      );
      transformed[p] =
        grayCodes[
          Math.round(
            (0.2126 * linearCodes[color[0]] +
              0.7152 * linearCodes[color[1]] +
              0.0722 * linearCodes[color[2]]) *
              65535,
          )
        ];
    }
    const error = (dx, dy) => {
      const offset = dy * frame.width + dx;
      let loss = 0;
      for (let i = 0; i < positions.length; i++)
        loss += Math.min(
          36,
          Math.abs(transformed[positions[i] / 4 + offset] - expected[i]),
        );
      return loss / positions.length + 0.05 * (Math.abs(dx) + Math.abs(dy));
    };
    const baseline = error(0, 0);
    let best = baseline,
      dx = 0,
      dy = 0;
    const evaluate = (x, y) => {
      const value = error(x, y);
      if (value < best) {
        best = value;
        dx = x;
        dy = y;
      }
    };
    for (let y = -radius; y <= radius; y += 3)
      for (let x = -radius; x <= radius; x += 3) evaluate(x, y);
    const bx = dx,
      by = dy;
    for (
      let y = Math.max(-radius, by - 2);
      y <= Math.min(radius, by + 2);
      y++
    ) {
      for (
        let x = Math.max(-radius, bx - 2);
        x <= Math.min(radius, bx + 2);
        x++
      )
        evaluate(x, y);
    }
    return best < baseline * 0.83 && best + 1 < baseline
      ? { x: dx, y: dy, matched: true, residual: best }
      : { x: 0, y: 0, matched: false, residual: baseline };
  }

  writeFrame(frame, cameraEV) {
    const projection = new Projection(
      relative(this.orientation, IDENTITY),
      this.source,
      frame.width,
      frame.height,
    );
    // A sensor can jump to a disconnected direction. Require real geometric
    // overlap even when texture is insufficient for the residual registration.
    if (this.count) {
      let overlap = false;
      const step = Math.max(1, Math.floor(Math.min(this.width, this.height) / 64));
      for (let y = 0; y < this.height && !overlap; y += step)
        for (let x = 0; x < this.width; x += step) {
          if (!this.pixels[(y * this.width + x) * 4 + 3]) continue;
          const p = projection.project(this.xs[x], this.ys[y]);
          if (p && p[0] >= frame.width * .03 && p[0] < frame.width * .97 &&
              p[1] >= frame.height * .03 && p[1] < frame.height * .97 &&
              frame.data[(Math.floor(p[1]) * frame.width + Math.floor(p[0])) * 4 + 3]) {
            overlap = true;
            break;
          }
        }
      if (!overlap) {
        this.disconnected = true;
        return false;
      }
    }
    this.disconnected = false;
    const gain = this.gains(
      Number.isFinite(cameraEV) && Number.isFinite(this.cameraEV)
        ? cameraEV - this.cameraEV
        : 0,
    );
    const correction = this.register(frame, projection, gain);
    this.lastRegistration = correction;
    const before = this.count;
    for (let y = 0; y < this.height; y++)
      for (let x = 0; x < this.width; x++) {
        const destination = (y * this.width + x) * 4;
        if (this.pixels[destination + 3]) continue;
        const position = projection.project(this.xs[x], this.ys[y]);
        if (!position) continue;
        const u = Math.floor(position[0] + correction.x),
          v = Math.floor(position[1] + correction.y);
        if (u < 0 || v < 0 || u >= frame.width || v >= frame.height) continue;
        const i = (v * frame.width + u) * 4;
        if (!frame.data[i + 3]) continue;
        const g = gain[luminance(frame.data, i)];
        const color = scaleColor(
          frame.data[i],
          frame.data[i + 1],
          frame.data[i + 2],
          g,
        );
        this.pixels[destination] = color[0];
        this.pixels[destination + 1] = color[1];
        this.pixels[destination + 2] = color[2];
        this.pixels[destination + 3] = 255;
        this.count++;
      }
    if (this.count > before) {
      this.captures++;
      this.views.clear();
      this.refreshTiles();
    }
    return this.count > before;
  }

  orientationFor(pose = null) {
    return validPose(pose) && this.sensorOrigin
      ? multiply(this.originPose, relative(this.sensorOrigin, pose))
      : this.orientation;
  }

  /** Sensor updates do no image readback. Only a stable, new direction decodes
   * a keyframe. Without sensors, a throttled thumbnail supplies visual motion. */
  capture({ pose = null, time = performance.now(), frame, trackingFrame = frame,
      cameraEV = null, collect = true } = {}) {
    let decoded;
    const image = () => decoded ??= typeof frame === "function" ? frame() : frame;
    this.needsOverlap = false;
    let reason = "", accepted = false;
    if (validPose(pose)) {
      if (!this.sensorOrigin) {
        this.originPose = [...this.orientation];
        this.sensorOrigin = Array.from(pose);
        this.anchor = null;
      }
      this.orientation = this.orientationFor(pose);
      this.reference = null;
      this.lastTrackTime = null;
    } else {
      if (this.sensorOrigin) {
        this.sensorOrigin = null;
        this.reference = null;
        this.anchor = null;
      }
      if (this.lastTrackTime == null || time - this.lastTrackTime >= 140 || time < this.lastTrackTime) {
        this.lastTrackTime = time;
        const pixels = typeof trackingFrame === "function" ? trackingFrame() : trackingFrame;
        if (pixels?.data) {
          const next = new TrackingImage(pixels);
          const delta = this.reference?.motionTo(next, this.source);
          this.needsOverlap = !!this.reference && !delta;
          if (delta) this.orientation = multiply(this.orientation, delta.matrix);
          if (!this.needsOverlap) this.reference = next;
        } else this.needsOverlap = true;
        this.trackingLost = this.needsOverlap;
      } else this.needsOverlap = this.trackingLost || false;
    }
    if (this.needsOverlap || !this.anchor || this.lastTime == null ||
        time < this.lastTime || time - this.lastTime > 500 ||
        moved(this.anchor, this.orientation, .45)) {
      this.anchor = [...this.orientation];
      this.since = time;
    }
    this.lastTime = time;
    this.settling = time - this.since < 350;
    if (collect && !this.ready && !this.needsOverlap && !this.settling &&
        (!this.lastAttempt || moved(this.lastAttempt, this.orientation, 1))) {
      if (this.count && Number.isFinite(this.cameraEV) && !Number.isFinite(cameraEV)) {
        reason = "该帧缺少曝光参数，请恢复采集曝光或重新开始";
      } else {
        const pixels = image();
        if (!pixels?.data || pixels.data.length !== pixels.width * pixels.height * 4) {
          reason = "无效画面";
        } else {
          if (!this.captures) this.cameraEV = Number.isFinite(cameraEV) ? cameraEV : null;
          accepted = this.writeFrame(pixels, cameraEV);
          this.lastAttempt = [...this.orientation];
        }
      }
    }
    return { ...this.guide(), accepted, reason,
      registered: accepted && (this.lastRegistration?.matched || false) };
  }

  addFrame(frame, pose = null, cameraEV = null, time = performance.now()) {
    return this.capture({ frame, pose, cameraEV, time });
  }

  /** Dragging is a fallback relative to the current view, never to atlas center. */
  pan(dx, dy, displayTurn = 0) {
    const p = turnPoint({ x: .5 + dx / 2, y: .5 + dy / 2 }, -displayTurn);
    this.orientation = multiply(this.orientation,
      yawPitch(-(2 * p.x - 1) * Math.atan(this.target.x),
               -(2 * p.y - 1) * Math.atan(this.target.y)));
    this.resumeTracking();
  }

  // Missing representatives are rebuilt only after an atlas write, not at video rate.
  refreshTiles() {
    this.tiles = [];
    this.holes = [];
    for (let row = 0; row < 7; row++)
      for (let col = 0; col < 9; col++) {
        const left = Math.ceil(this.width * col / 9), right = Math.ceil(this.width * (col + 1) / 9);
        const top = Math.ceil(this.height * row / 7), bottom = Math.ceil(this.height * (row + 1) / 7);
        let missing = null, distance = Infinity;
        for (let y = top; y < bottom; y++)
          for (let x = left; x < right; x++) {
            if (this.pixels[(y * this.width + x) * 4 + 3]) continue;
            const d = (x - (left + right) / 2) ** 2 + (y - (top + bottom) / 2) ** 2;
            if (d < distance) {
              distance = d;
              missing = { x: (x + .5) / this.width, y: (y + .5) / this.height };
            }
          }
        this.tiles.push(!missing);
        if (missing) this.holes.push(missing);
      }
  }

  homeDirection(orientation = this.orientation) {
    const position = new Projection(orientation, this.bounds, this.width, this.height).project(0, 0);
    if (position && position[0] >= 0 && position[1] >= 0 &&
        position[0] < this.width && position[1] < this.height &&
        this.pixels[(Math.floor(position[1]) * this.width + Math.floor(position[0])) * 4 + 3]) return null;
    const r = relative(orientation, IDENTITY);
    let x = -r[2], y = r[5];
    if (Math.hypot(x, y) < 1e-6) {
      if (r[8] > 0) return null; // No first frame yet; the user is still at center.
      x = 1; y = 0; // The antipode has no unique shortest direction.
    }
    const size = Math.hypot(x, y);
    return { x: x / size, y: y / size };
  }

  guide(displayTurn = 0, pose = null) {
    const r = this.orientationFor(pose), z = Math.max(.001, r[8]);
    const aim = { x: .5 - .5 * r[2] / (this.bounds.x * z),
                  y: .5 + .5 * r[5] / (this.bounds.y * z) };
    let next = null, closest = Infinity;
    for (const hole of this.holes) {
      const distance = Math.hypot((hole.x - aim.x) * this.bounds.x, (hole.y - aim.y) * this.bounds.y);
      if (distance < closest) { closest = distance; next = hole; }
    }
    const home = this.homeDirection(r);
    const turnedHome = home && turnPoint({ x: .5 + home.x / 2, y: .5 + home.y / 2 }, displayTurn);
    return { coverage: this.coverage, ready: this.ready, captures: this.captures,
      needsOverlap: this.needsOverlap || this.disconnected,
      settling: this.settling, aim: turnPoint(aim, displayTurn), next: turnPoint(next, displayTurn),
      tiles: this.tiles, displayTurn,
      home: turnedHome && { x: 2 * turnedHome.x - 1, y: 2 * turnedHome.y - 1 } };
  }

  atlas() {
    return imageData(this.pixels.slice(), this.width, this.height);
  }

  get nativeViewEdge() {
    return Math.max(1, Math.round(Math.max(this.width, this.height) / MARGIN));
  }

  /** Actual orientation determines the crop. Unobserved / behind-camera rays
   * remain RGBA zero for display black and exclusion from all measurements. */
  view(pose = null, offset = null, maxEdge = Infinity, displayTurn = 0) {
    let matrix = this.orientationFor(pose);
    if (offset) matrix = multiply(matrix, yawPitch(
      -(Number(offset.x) || 0) * Math.atan(this.target.x),
      -(Number(offset.y) || 0) * Math.atan(this.target.y)));
    const nativeWidth = Math.max(1, Math.round(this.width / MARGIN));
    const nativeHeight = Math.max(1, Math.round(this.height / MARGIN));
    const scale = Math.min(1, maxEdge / Math.max(nativeWidth, nativeHeight));
    const baseWidth = Math.max(1, Math.round(nativeWidth * scale));
    const baseHeight = Math.max(1, Math.round(nativeHeight * scale));
    const turn = (displayTurn % 360 + 360) % 360;
    const key = `${baseWidth}x${baseHeight}:${turn}`;
    const cached = this.views.get(key);
    if (cached && !moved(cached.matrix, matrix, .06)) return cached.result;
    const transpose = turn === 90 || turn === 270;
    const width = transpose ? baseHeight : baseWidth, height = transpose ? baseWidth : baseHeight;
    const data = new Uint8ClampedArray(width * height * 4), output = new Uint32Array(data.buffer);
    const input = new Uint32Array(this.pixels.buffer);
    const fx = this.width / (2 * this.bounds.x), fy = this.height / (2 * this.bounds.y);
    const step = 2 * this.target.x / baseWidth, left = .5 * step - this.target.x;
    const stepU = matrix[0] * step * fx, stepV = -matrix[3] * step * fy, stepZ = -matrix[6] * step;
    let valid = 0;
    for (let y = 0; y < baseHeight; y++) {
      const yy = (1 - 2 * (y + .5) / baseHeight) * this.target.y;
      let z = matrix[8] - matrix[6] * left - matrix[7] * yy;
      let u = (matrix[0] * left + matrix[1] * yy - matrix[2]) * fx;
      let v = -(matrix[3] * left + matrix[4] * yy - matrix[5]) * fy;
      for (let x = 0; x < baseWidth; x++, z += stepZ, u += stepU, v += stepV) {
        if (z <= .001) continue;
        const ax = this.width * .5 + u / z, ay = this.height * .5 + v / z;
        if (ax < 0 || ay < 0 || ax >= this.width || ay >= this.height) continue;
        const index = Math.floor(ay) * this.width + Math.floor(ax);
        if (!this.pixels[index * 4 + 3]) continue;
        const dx = turn === 90 ? baseHeight - 1 - y : turn === 180 ? baseWidth - 1 - x : turn === 270 ? y : x;
        const dy = turn === 90 ? x : turn === 180 ? baseHeight - 1 - y : turn === 270 ? baseWidth - 1 - x : y;
        output[dy * width + dx] = input[index];
        valid++;
      }
    }
    const result = { image: imageData(data, width, height), cameraEV: this.cameraEV,
      coverage: valid / (width * height), missing: valid < width * height, complete: this.ready };
    if (this.views.size > 5) this.views.clear();
    this.views.set(key, { matrix: [...matrix], result });
    return result;
  }
}
