import test from "node:test";
import assert from "node:assert/strict";
import {
  Panorama,
  cropFrame,
  orientationMatrix,
  yawPitch,
  scaleColor,
} from "./panorama.js";

const source = { x: 0.7, y: 0.6 };
const target = { x: 1, y: 0.4 };
const identity = yawPitch(0);
test("pano analysis can downsample while the native crop never exceeds its atlas", () => {
  const p = new Panorama({ target, source, width: 115, height: 115 });
  p.pixels.fill(255);
  const full = p.view(null, { x: 0, y: 0 });
  const mini = p.view(null, { x: 0, y: 0 }, 40);
  const oversized = p.view(null, { x: 0, y: 0 }, 800);
  assert.deepEqual([full.image.width, full.image.height], [100, 40]);
  assert.deepEqual([mini.image.width, mini.image.height], [40, 16]);
  assert.deepEqual([oversized.image.width, oversized.image.height], [100, 40]);
  assert.equal(mini.missing, false);
});
function render(field, yaw, ev = 0, bias = 0) {
  const width = 224,
    height = Math.round((width * field.y) / field.x);
  const data = new Uint8ClampedArray(width * height * 4);
  for (let y = 0; y < height; y++)
    for (let x = 0; x < width; x++) {
      const u = (((x - bias + 0.5) * 2) / width - 1) * field.x;
      const v = (1 - ((y + 0.5) * 2) / height) * field.y;
      const z = Math.cos(yaw) + Math.sin(yaw) * u;
      const xx = (Math.cos(yaw) * u - Math.sin(yaw)) / z;
      const yy = v / z;
      const value = Math.max(
        0,
        Math.min(
          255,
          Math.round(
            (128 +
              30 * Math.sin(36 * xx + 4 * yy) +
              24 * Math.cos(55 * yy + 12 * xx) +
              18 * Math.sin(17 * xx * yy)) *
              2 ** (-ev / 2.2),
          ),
        ),
      );
      const i = (y * width + x) * 4;
      data[i] = value;
      data[i + 1] = value;
      data[i + 2] = value;
      data[i + 3] = 255;
    }
  return { data, width, height };
}

test("missing target field stays transparent and a complete crop preserves aspect", () => {
  const frame = render(source, 0);
  const wide = cropFrame(frame, source, target);
  assert.equal(wide.data[Math.floor(wide.height / 2) * wide.width * 4 + 3], 0);
  assert.equal(
    wide.data[
      (Math.floor(wide.height / 2) * wide.width + Math.floor(wide.width / 2)) *
        4 +
        3
    ],
    255,
  );
  assert.ok(Math.abs(wide.width / wide.height - target.x / target.y) < 0.02);
  const small = cropFrame(frame, source, { x: 0.3, y: 0.2 });
  assert.ok(small.data.filter((_, i) => i % 4 === 3).every((v) => v === 255));
});

test("dark blue compensation is computed in linear RGB", () => {
  assert.deepEqual(scaleColor(0, 0, 32, 8), [0, 0, 95]);
  assert.deepEqual(scaleColor(100, 80, 50, 1), [100, 80, 50]);
});

test("sensor frames fill 115% atlas and reframing remains inside acquired pixels", () => {
  const pano = new Panorama({ target, source, width: 288 });
  pano.setCenter(identity);
  const start = pano.addFrame(render(source, 0), identity, 8);
  assert.ok(start.coverage > 0.58 && start.coverage < 0.64);
  assert.equal(start.captures, 1);
  assert.notEqual(start.next, null);
  for (const angle of [0.24, 0.43, -0.24, -0.43])
    pano.addFrame(render(source, angle, 1, 3), yawPitch(angle), 9);
  assert.equal(pano.coverage, 1);
  assert.equal(pano.ready, true);
  assert.equal(pano.guide().next, null);
  const view = pano.view(identity);
  assert.equal(view.cameraEV, 8);
  assert.equal(view.missing, false);
  assert.ok(Math.abs(view.image.width / view.image.height - 2.5) < 0.03);
  for (const angle of [-0.035, 0.035, 0.7, 1.2])
    assert.equal(pano.view(yawPitch(angle)).missing, false);
});

test("image overlap tracks a scan without a motion sensor", () => {
  const pano = new Panorama({ target, source, width: 224 });
  pano.addFrame(render(source, 0), null, null);
  const angles = Array.from({ length: 12 }, (_, i) => (i + 1) * 0.04).concat(
    Array.from({ length: 24 }, (_, i) => (11 - i) * 0.04),
  );
  for (const angle of angles) pano.addFrame(render(source, angle), null, null);
  assert.ok(pano.guide().captures > 3);
  assert.equal(pano.coverage, 1);
  assert.equal(pano.view(null).cameraEV, null);
});

test("textureless overlap is rejected and never erases acquired data", () => {
  const pano = new Panorama({ target, source, width: 224 });
  const first = render(source, 0);
  pano.addFrame(first, null, 8);
  const before = pano.coverage;
  const data = new Uint8ClampedArray(first.data.length).fill(62);
  for (let i = 3; i < data.length; i += 4) data[i] = 255;
  const result = pano.addFrame({ ...first, data }, null, 8);
  assert.equal(result.needsOverlap, true);
  assert.equal(pano.coverage, before);
  assert.equal(
    pano.addFrame(render(source, 0.045), null, null).accepted,
    false,
  );
  assert.equal(pano.coverage, before);
});

test("DeviceOrientation conversion is finite and display remapping is invertible", () => {
  assert.equal(orientationMatrix(null, 0, 0), null);
  assert.ok(
    orientationMatrix(0, 0, 0).every(
      (v, i) => Math.abs(v - identity[i]) < 1e-12,
    ),
  );
  const quarter = orientationMatrix(0, 0, 0, 90);
  assert.ok(Math.abs(quarter[1] - 1) < 1e-12);
  assert.ok(Math.abs(quarter[3] + 1) < 1e-12);
  const turn = orientationMatrix(60, 80, 25, 270);
  const norm = Math.hypot(turn[0], turn[3], turn[6]);
  assert.ok(Math.abs(norm - 1) < 1e-12);
});
