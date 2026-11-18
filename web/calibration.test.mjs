import test from "node:test";
import assert from "node:assert/strict";
import {
  fitResponse,
  sampleGrid,
  referenceOffset,
  CalibrationIssue,
} from "./calibration.js";

const count = 32 * 24;
function brackets({
  low = -10,
  high = 6,
  gamma = 2.2,
  irradiance = () => 0.18,
} = {}) {
  return Array.from({ length: high - low + 1 }, (_, i) => {
    const ev = low + i;
    const samples = Array.from(
      { length: count },
      (_, j) =>
        Math.round(Math.min(1, irradiance(j) * 2 ** ev) ** (1 / gamma) * 255) /
        255,
    );
    return { seconds: 2 ** ev / 125, samples, repeat: samples.slice() };
  });
}

test("gray card recovers a monotone response and normalizes code117 to Zone V", () => {
  const profile = fitResponse(brackets({ gamma: 1.8 }).reverse(), {
    method: "灰卡",
  });
  assert.equal(profile.response.length, 256);
  assert.equal(profile.response[117], 0);
  assert.ok(Math.abs(profile.response[150] - profile.response[75] - 1.8) < 0.1);
  assert.ok(
    profile.response.slice(1).every((v, i) => v >= profile.response[i]),
  );
  assert.ok(profile.stops > 10 && profile.stops < 13);
  assert.equal(profile.channel, "浏览器预览估测");
});

test("field fit follows stable scene ranks through an exposure bracket", () => {
  const profile = fitResponse(
    brackets({ gamma: 2, irradiance: (i) => 0.025 * 2 ** ((4 * i) / count) }),
  );
  assert.ok(Math.abs(profile.response[160] - profile.response[80] - 2) < 0.15);
  assert.ok(profile.stops > 9);
});

test("step card checks orientation and the entered EV interval", () => {
  const frames = brackets({
    irradiance: (i) => 0.01125 * 2 ** Math.floor(((i % 32) * 9) / 32),
  });
  const profile = fitResponse(frames, { method: "阶梯卡", step: 1 });
  assert.ok(
    Math.abs(profile.response[160] - profile.response[80] - 2.2) < 0.12,
  );
  assert.throws(
    () => fitResponse(frames, { method: "阶梯卡", step: 2 }),
    CalibrationIssue,
  );
  assert.throws(
    () =>
      fitResponse(
        frames.map((f) => ({
          ...f,
          samples: [...f.samples].reverse(),
          repeat: [...f.repeat].reverse(),
        })),
        { method: "阶梯卡" },
      ),
    CalibrationIssue,
  );
});

test("zero or narrow exposure span, changing geometry, flicker and missing clipping are rejected", () => {
  const frames = brackets();
  assert.throws(
    () =>
      fitResponse(
        frames.map((f) => ({ ...f, seconds: 0.1 })),
        { method: "灰卡" },
      ),
    CalibrationIssue,
  );
  assert.throws(
    () => fitResponse(brackets({ low: -3, high: 0 }), { method: "灰卡" }),
    CalibrationIssue,
  );
  assert.throws(
    () => fitResponse(brackets({ low: -12, high: 0 }), { method: "灰卡" }),
    CalibrationIssue,
  );
  assert.throws(
    () => fitResponse(frames.map((f) => ({ ...f, repeat: [0.1] }))),
    CalibrationIssue,
  );
  const moving = brackets({
    irradiance: (i) => 0.015 * 2 ** ((5 * i) / count),
  });
  assert.throws(
    () =>
      fitResponse(
        moving.map((f, i) =>
          i === 10
            ? { ...f, repeat: f.repeat.map((v) => Math.min(1, v + 0.09)) }
            : f,
        ),
      ),
    CalibrationIssue,
  );
});

test("transparent images cannot enter a calibration and reference offsets use acquisition EV", () => {
  assert.throws(
    () => sampleGrid({ width: 1, height: 1, data: new Uint8ClampedArray(4) }),
    CalibrationIssue,
  );
  const image = {
    width: 1,
    height: 1,
    data: new Uint8ClampedArray([117, 117, 117, 255]),
  };
  const offset = referenceOffset(image, 12, 11);
  assert.ok(Math.abs(offset - 1) < 0.03);
  assert.throws(() => referenceOffset(image, 12, null), CalibrationIssue);
});
