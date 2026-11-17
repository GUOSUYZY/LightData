import { t } from "./i18n.js";
// Browser previews have already passed through the camera's image processing.
// This estimates that preview response; it does not report sensor RAW range.
const clamp = (v, low, high) => Math.max(low, Math.min(high, v));
const toLinear = (v) =>
  v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
const toSrgb = (v) =>
  v <= 0.0031308 ? 12.92 * v : 1.055 * v ** (1 / 2.4) - 0.055;
const linearCodes = Float64Array.from({ length: 256 }, (_, i) =>
  toLinear(i / 255),
);
const initialResponse = Float64Array.from({ length: 256 }, (_, i) =>
  Math.log2(toLinear(Math.max(0.5, i) / 255) / 0.18),
);
const quantile = (values, q) => values[Math.round(q * (values.length - 1))];
const sorted = (values) => Array.from(values).sort((a, b) => a - b);
const median = (values) => quantile(sorted(values), 0.5);

export class CalibrationIssue extends Error {
  constructor(message) {
    super(message);
    this.name = "CalibrationIssue";
  }
}

/** Sample a 32 × 24 normalized grayscale grid in the unfiltered camera frame. */
export function sampleGrid(image) {
  if (!image?.data || image.width < 1 || image.height < 1)
    throw new CalibrationIssue(t("没有可用的相机画面"));
  const result = new Float64Array(32 * 24);
  for (let y = 0; y < 24; y++)
    for (let x = 0; x < 32; x++) {
      const px = Math.min(
        image.width - 1,
        Math.floor(((x + 0.5) * image.width) / 32),
      );
      const py = Math.min(
        image.height - 1,
        Math.floor(((y + 0.5) * image.height) / 24),
      );
      const i = (py * image.width + px) * 4;
      if (image.data[i + 3] === 0)
        throw new CalibrationIssue(t("校准画面含缺失区域"));
      const luminance =
        0.2126 * linearCodes[image.data[i]] +
        0.7152 * linearCodes[image.data[i + 1]] +
        0.0722 * linearCodes[image.data[i + 2]];
      result[y * 32 + x] = toSrgb(luminance);
    }
  return result;
}

function lowerBound(values, value) {
  let low = 0;
  let high = values.length;
  while (low < high) {
    const middle = (low + high) >>> 1;
    if (values[middle] < value) low = middle + 1;
    else high = middle;
  }
  return low;
}

function validateFrames(frames) {
  const count = frames[0]?.samples?.length;
  const valid =
    count >= 32 &&
    frames.length >= 5 &&
    frames.every(
      (frame) =>
        Number.isFinite(frame.seconds) &&
        frame.seconds > 0 &&
        frame.samples?.length === count &&
        frame.repeat?.length === count &&
        Array.from(frame.samples).every(
          (v) => Number.isFinite(v) && v >= 0 && v <= 1,
        ) &&
        Array.from(frame.repeat).every(
          (v) => Number.isFinite(v) && v >= 0 && v <= 1,
        ),
    );
  if (!valid || new Set(frames.map((f) => f.seconds)).size < 5)
    throw new CalibrationIssue(t("有效曝光样本不足，请重新采集"));
  const ordered = [...frames].sort((a, b) => a.seconds - b.seconds);
  if (Math.log2(ordered.at(-1).seconds / ordered[0].seconds) < 4)
    throw new CalibrationIssue(t("实际曝光跨度不足 4 EV，请扩大扫描范围"));
  return ordered;
}

function motionCheck(grids, distributions) {
  for (let i = 1; i < grids.length; i++) {
    const before = distributions[i - 1];
    const after = distributions[i];
    if (
      quantile(before, 0.9) - quantile(before, 0.1) < 0.15 ||
      quantile(after, 0.9) - quantile(after, 0.1) < 0.15
    )
      continue;
    let common = 0;
    let shifted = 0;
    for (let j = 0; j < grids[i].length; j++) {
      const left = grids[i - 1][j];
      const right = grids[i][j];
      if (left < 0.08 || left > 0.88 || right < 0.08 || right > 0.88) continue;
      common++;
      const low = lowerBound(before, left - 0.012);
      const high = lowerBound(before, left + 0.012);
      const lowAfter = lowerBound(after, right - 0.012);
      const highAfter = lowerBound(after, right + 0.012);
      if (Math.max(low - highAfter, lowAfter - high) > 0.16 * before.length)
        shifted++;
    }
    if (common >= grids[i].length / 8 && shifted > 0.22 * common)
      throw new CalibrationIssue(t("相邻曝光间画面发生移动，请固定手机后重试"));
  }
}

/** Pool adjacent violators, preserving sample weight in the monotonic inverse response. */
function monotone(means, weights) {
  const levels = [];
  const mass = [];
  const ends = [];
  for (let i = 0; i < means.length; i++) {
    levels.push(means[i]);
    mass.push(weights[i]);
    ends.push(i);
    while (levels.length > 1 && levels.at(-1) < levels.at(-2)) {
      const last = levels.length - 1;
      const combined = mass[last - 1] + mass[last];
      levels[last - 1] +=
        ((levels[last] - levels[last - 1]) * mass[last]) / combined;
      mass[last - 1] = combined;
      ends[last - 1] = i;
      levels.pop();
      mass.pop();
      ends.pop();
    }
  }
  const result = new Float64Array(means.length);
  let start = 0;
  for (let i = 0; i < levels.length; i++) {
    result.fill(levels[i], start, ends[i] + 1);
    start = ends[i] + 1;
  }
  return result;
}

function tracksFor(method, grids, distributions) {
  if (method === "阶梯卡") {
    if (grids[0].length !== 32 * 24)
      throw new CalibrationIssue(t("阶梯卡取样网格不完整"));
    return Array.from({ length: 9 }, (_, j) => {
      const col = Math.floor(((j + 0.5) * 32) / 9);
      return grids.map((grid) =>
        median(Array.from({ length: 8 }, (_, i) => grid[(8 + i) * 32 + col])),
      );
    });
  }
  const count = method === "灰卡" ? 1 : 19;
  return Array.from({ length: count }, (_, j) =>
    distributions.map((d) => quantile(d, (j + 1) / (count + 1))),
  );
}

/**
 * Port of LightData's preview bracket fit. The shutter/ISO/aperture must be actual
 * fixed-camera acquisition parameters, never the target film exposure. For varying
 * aperture or ISO, the caller supplies effective seconds = t*ISO/(100*N²).
 * Failed fits throw and leave the caller's previously stored profile untouched.
 */
export function fitResponse(frames, { method = "现场", step = 1 } = {}) {
  if (
    !["灰卡", "阶梯卡", "现场"].includes(method) ||
    (method === "阶梯卡" && (!Number.isFinite(step) || step < 0.1 || step > 3))
  )
    throw new CalibrationIssue(t("校准参数无效"));
  const ordered = validateFrames(frames);
  const grids = ordered.map((b) =>
    Array.from(b.samples, (v, i) => (v + b.repeat[i]) / 2),
  );
  const distributions = grids.map(sorted);
  let ref = 0;
  for (let i = 1; i < distributions.length; i++) {
    if (
      Math.abs(quantile(distributions[i], 0.5) - 0.46) <
      Math.abs(quantile(distributions[ref], 0.5) - 0.46)
    )
      ref = i;
  }
  if (
    quantile(distributions[0], 0.05) > 0.08 ||
    quantile(distributions.at(-1), 0.95) < 0.97
  )
    throw new CalibrationIssue(
      t("未同时覆盖暗部和饱和高光，请调整照明或曝光范围"),
    );
  if (
    method === "现场" &&
    quantile(distributions[ref], 0.95) - quantile(distributions[ref], 0.05) <
      0.18
  )
    throw new CalibrationIssue(
      t("现场明暗跨度不足，请选择同时包含阴影和亮部的场景"),
    );
  if (
    method === "灰卡" &&
    quantile(distributions[ref], 0.85) - quantile(distributions[ref], 0.15) >
      0.17
  )
    throw new CalibrationIssue(t("灰卡受光不均或未充满画面"));
  for (const bracket of ordered) {
    const changed = Array.from(bracket.samples).filter(
      (v, i) => Math.abs(v - bracket.repeat[i]) > 0.065,
    ).length;
    if (changed > 0.12 * bracket.samples.length)
      throw new CalibrationIssue(t("检测到移动或光源闪烁，请固定手机"));
  }
  if (method !== "灰卡") motionCheck(grids, distributions);

  const tracks = tracksFor(method, grids, distributions);
  const times = ordered.map((b) => Math.log2(b.seconds / ordered[ref].seconds));
  const valid = tracks.map((track) =>
    track
      .map((v, i) => (v > 2 / 255 && v <= 0.985 ? i : -1))
      .filter((i) => i >= 0),
  );
  if (method === "阶梯卡") {
    const ascending = tracks
      .slice(0, -1)
      .every((track, j) =>
        track.some(
          (v, i) => v >= 0.04 && v <= 0.88 && tracks[j + 1][i] > v + 0.008,
        ),
      );
    if (valid.some((v) => v.length < 2) || !ascending)
      throw new CalibrationIssue(t("请将九级阶梯自暗至亮对齐取景画面"));
  }

  let lut = initialResponse.slice();
  let firstCode = 0;
  let lastCode = 255;
  for (let iteration = 0; iteration < 12; iteration++) {
    const bins = Array.from({ length: 256 }, () => []);
    tracks.forEach((track, j) => {
      if (valid[j].length < 2) return;
      const offset =
        method === "阶梯卡"
          ? j * step
          : median(
              valid[j].map((i) => lut[Math.round(track[i] * 255)] - times[i]),
            );
      for (const i of valid[j])
        bins[Math.round(track[i] * 255)].push(offset + times[i]);
    });
    const codes = bins
      .map((bin, i) => (bin.length ? i : -1))
      .filter((i) => i >= 0);
    if (codes.length < 5 || codes[0] > 80 || codes.at(-1) < 150)
      throw new CalibrationIssue(t("有效灰阶未覆盖中灰两侧，请调整照明"));
    firstCode = codes[0];
    lastCode = codes.at(-1);
    const values = monotone(
      codes.map((c) => median(bins[c])),
      codes.map((c) => bins[c].length),
    );
    lut = Float64Array.from({ length: 256 }, (_, b) => {
      const position = lowerBound(codes, b);
      if (codes[position] === b) return values[position];
      const right = clamp(position, 1, codes.length - 1);
      const left = right - 1;
      const width = Math.log(codes[right] / codes[left]);
      const fraction = Math.log(Math.max(1, b) / codes[left]) / width;
      return clamp(
        values[left] + fraction * (values[right] - values[left]),
        values[0] - 1,
        values.at(-1) + 1,
      );
    });
    const zero = lut[117];
    lut = lut.map((v) => v - zero);
  }

  const residuals = [];
  const chartOffsets = [];
  tracks.forEach((track, j) => {
    const offsets = valid[j]
      .filter((i) => track[i] >= 0.04 && track[i] <= 0.95)
      .map((i) => lut[Math.round(track[i] * 255)] - times[i]);
    if (offsets.length < 2) return;
    const center = median(offsets);
    residuals.push(...offsets.map((v) => Math.abs(v - center)));
    if (method === "阶梯卡") chartOffsets.push(center - j * step);
  });
  if (!residuals.length || quantile(sorted(residuals), 0.9) > 0.65)
    throw new CalibrationIssue(t("曝光序列响应不一致，请固定手机并使用稳定光源"));
  if (
    chartOffsets.length &&
    Math.max(...chartOffsets) - Math.min(...chartOffsets) > 0.65
  )
    throw new CalibrationIssue(t("阶梯间隔与输入 EV 不符，请确认参考卡"));

  const darkest = ordered[0];
  const darkCut = quantile(distributions[0], 0.25);
  const darkPixels = grids[0]
    .map((v, i) => (v <= darkCut ? i : -1))
    .filter((i) => i >= 0);
  const variance =
    darkPixels.reduce(
      (sum, i) => sum + (darkest.samples[i] - darkest.repeat[i]) ** 2,
      0,
    ) /
    (2 * darkPixels.length);
  const floor = quantile(distributions[0], 0.05) + 3 * Math.sqrt(variance);
  const floorCode = clamp(Math.max(firstCode, Math.ceil(floor * 255)), 1, 254);
  const stops = lut[lastCode] - lut[floorCode];
  if (!(stops >= 3 && stops <= 22))
    throw new CalibrationIssue(t("可用范围无法可靠拟合，请调整场景重试"));
  return {
    response: Array.from(lut),
    stops,
    channel: "浏览器预览估测",
    samples: ordered.length,
    method,
    minExposure: ordered[0].seconds,
    maxExposure: ordered.at(-1).seconds,
    createdAt: new Date().toISOString(),
  };
}

/** Establish absolute scene EV from a known uniform reference at the current exposure. */
export function referenceOffset(
  image,
  knownSceneEV,
  cameraEV,
  response = initialResponse,
) {
  if (!Number.isFinite(knownSceneEV) || !Number.isFinite(cameraEV))
    throw new CalibrationIssue(t("参考 EV 与采集曝光均需为有效数值"));
  const grid = sampleGrid(image);
  const distribution = sorted(grid);
  if (quantile(distribution, 0.85) - quantile(distribution, 0.15) > 0.17)
    throw new CalibrationIssue(t("参考卡未充满画面或受光不均"));
  const sum = grid.reduce(
    (value, v) => value + 2 ** response[Math.round(v * 255)],
    0,
  );
  return knownSceneEV - cameraEV - Math.log2(sum / grid.length);
}
