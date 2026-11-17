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
    throw new Error("需要有效的相机和目标视角");
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
      throw new Error("拼接需要有效的相机和目标视角");
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
    this.previousSensor = null;
    this.reference = null;
    this.needsOverlap = false;
    this.lastSamplePose = null;
    this.lastRegistration = null;
  }
  get coverage() {
    return this.count / (this.width * this.height);
  }
  get ready() {
    return this.count === this.width * this.height;
  }
  setCenter(pose = null) {
    this.orientation = [...IDENTITY];
    this.previousSensor = validPose(pose) ? Array.from(pose) : null;
  }
  resumeTracking() {
    this.previousSensor = null;
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
    if (this.count > before) this.captures++;
    return this.count > before;
  }

  addFrame(frame, pose = null, cameraEV = null) {
    if (!frame?.data || frame.data.length !== frame.width * frame.height * 4)
      return { ...this.guide(), accepted: false, reason: "无效画面" };
    if (
      this.reference &&
      Number.isFinite(this.cameraEV) &&
      !Number.isFinite(cameraEV)
    )
      return {
        ...this.guide(),
        accepted: false,
        reason: "该帧缺少曝光参数，请恢复采集曝光或重新开始",
      };
    const image = new TrackingImage(frame);
    const sensor = validPose(pose) ? Array.from(pose) : null;
    const inertial =
      sensor && this.previousSensor
        ? relative(this.previousSensor, sensor)
        : null;
    const stationarySensor =
      inertial && inertial[0] + inertial[4] + inertial[8] > 2.999995;
    const visual =
      this.reference && (!inertial || stationarySensor)
        ? this.reference.motionTo(image, this.source)
        : null;
    this.needsOverlap = !!this.reference && !inertial && !visual;
    if (this.needsOverlap)
      return {
        ...this.guide(),
        accepted: false,
        reason: "画面缺少可靠重叠，请缓慢移回已采集区域",
      };
    const rotation = visual?.matrix || inertial;
    if (rotation) this.orientation = multiply(this.orientation, rotation);
    this.reference = image;
    this.previousSensor = sensor;
    if (this.captures === 0)
      this.cameraEV = Number.isFinite(cameraEV) ? cameraEV : null;
    const change = this.lastSamplePose
      ? relative(this.lastSamplePose, this.orientation)
      : null;
    let accepted = false;
    if (
      !this.ready &&
      (!change || change[0] + change[4] + change[8] < 2.99992)
    ) {
      accepted = this.writeFrame(frame, cameraEV);
      this.lastSamplePose = [...this.orientation];
    }
    return {
      ...this.guide(),
      accepted,
      registered: this.lastRegistration?.matched || false,
    };
  }

  guide() {
    const r = this.orientation,
      z = Math.max(0.001, r[8]);
    const aim = {
      x: 0.5 - (0.5 * r[2]) / (this.bounds.x * z),
      y: 0.5 + (0.5 * r[5]) / (this.bounds.y * z),
    };
    let next = null,
      closest = Infinity;
    const tiles = [];
    for (let row = 0; row < 7; row++)
      for (let col = 0; col < 9; col++) {
        let covered = 0,
          count = 0,
          first = null;
        const left = Math.ceil((this.width * col) / 9),
          right = Math.ceil((this.width * (col + 1)) / 9);
        const top = Math.ceil((this.height * row) / 7),
          bottom = Math.ceil((this.height * (row + 1)) / 7);
        for (let y = top; y < bottom; y++)
          for (let x = left; x < right; x++) {
            count++;
            if (this.pixels[(y * this.width + x) * 4 + 3]) covered++;
            else if (
              !first ||
              (x - (left + right) / 2) ** 2 + (y - (top + bottom) / 2) ** 2 <
                first.distance
            ) {
              first = {
                x: (x + 0.5) / this.width,
                y: (y + 0.5) / this.height,
                distance:
                  (x - (left + right) / 2) ** 2 + (y - (top + bottom) / 2) ** 2,
              };
            }
          }
        tiles.push(covered === count);
        if (first) {
          const score = Math.hypot(
            (first.x - aim.x) * this.bounds.x,
            (first.y - aim.y) * this.bounds.y,
          );
          if (score < closest) {
            closest = score;
            next = { x: first.x, y: first.y };
          }
        }
      }
    return {
      coverage: this.coverage,
      ready: this.ready,
      captures: this.captures,
      needsOverlap: this.needsOverlap,
      aim,
      next,
      tiles,
    };
  }

  atlas() {
    return imageData(this.pixels.slice(), this.width, this.height);
  }

  limitedPose(pose) {
    const fits = (matrix) => {
      for (const x of [-this.target.x, this.target.x])
        for (const y of [-this.target.y, this.target.y]) {
          const z = matrix[8] - matrix[6] * x - matrix[7] * y;
          if (
            z <= 0 ||
            Math.abs(matrix[0] * x + matrix[1] * y - matrix[2]) >
              this.bounds.x * z ||
            Math.abs(matrix[3] * x + matrix[4] * y - matrix[5]) >
              this.bounds.y * z
          )
            return false;
        }
      return true;
    };
    if (fits(pose)) return pose;
    const angle = Math.acos(
      clamp((pose[0] + pose[4] + pose[8] - 1) / 2, -1, 1),
    );
    if (angle < 1e-5 || Math.abs(Math.sin(angle)) < 1e-5) return IDENTITY;
    const divisor = 2 * Math.sin(angle);
    const x = (pose[7] - pose[5]) / divisor,
      y = (pose[2] - pose[6]) / divisor,
      z = (pose[3] - pose[1]) / divisor;
    const rotate = (a) => {
      const c = Math.cos(a),
        s = Math.sin(a),
        t = 1 - c;
      return [
        c + t * x * x,
        t * x * y - s * z,
        t * x * z + s * y,
        t * x * y + s * z,
        c + t * y * y,
        t * y * z - s * x,
        t * x * z - s * y,
        t * y * z + s * x,
        c + t * z * z,
      ];
    };
    let low = 0,
      high = angle;
    for (let i = 0; i < 15; i++) {
      const mid = (low + high) / 2;
      if (fits(rotate(mid))) low = mid;
      else high = mid;
    }
    return rotate(low);
  }

  /** Reframe only within the acquired 15% margin. Offset x/y are normalized -1…1. */
  view(pose = null, offset = null) {
    let orientation = this.orientation;
    if (validPose(pose) && this.previousSensor)
      orientation = multiply(
        this.orientation,
        relative(this.previousSensor, pose),
      );
    const ox = clamp(Number(offset?.x) || 0, -1, 1),
      oy = clamp(Number(offset?.y) || 0, -1, 1);
    if (offset)
      orientation = yawPitch(
        -Math.atan(this.target.x * (MARGIN - 1)) * ox,
        -Math.atan(this.target.y * (MARGIN - 1)) * oy,
      );
    const matrix = this.limitedPose(orientation);
    const width = Math.max(1, Math.round(this.width / MARGIN)),
      height = Math.max(1, Math.round(this.height / MARGIN));
    const projection = new Projection(
      matrix,
      this.bounds,
      this.width,
      this.height,
    );
    const data = new Uint8ClampedArray(width * height * 4);
    let missing = 0;
    for (let y = 0; y < height; y++)
      for (let x = 0; x < width; x++) {
        const xx = ((2 * (x + 0.5)) / width - 1) * this.target.x;
        const yy = (1 - (2 * (y + 0.5)) / height) * this.target.y;
        const position = projection.project(xx, yy);
        if (!position) {
          missing++;
          continue;
        }
        const u = clamp(Math.floor(position[0]), 0, this.width - 1),
          v = clamp(Math.floor(position[1]), 0, this.height - 1);
        const index = (v * this.width + u) * 4,
          output = (y * width + x) * 4;
        data[output] = this.pixels[index];
        data[output + 1] = this.pixels[index + 1];
        data[output + 2] = this.pixels[index + 2];
        data[output + 3] = this.pixels[index + 3];
        if (!data[output + 3]) missing++;
      }
    return {
      image: imageData(data, width, height),
      cameraEV: this.cameraEV,
      coverage: 1 - missing / (width * height),
      missing: missing > 0,
      complete: this.ready,
    };
  }
}
