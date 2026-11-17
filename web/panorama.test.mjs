import test from "node:test";
import assert from "node:assert/strict";
import { Panorama, cropFrame, orientationMatrix, yawPitch, scaleColor, turnFrame, turnPoint } from "./panorama.js";
import { histogram, spot } from "./core.js";

const source = { x: .7, y: .6 }, target = { x: 1, y: .4 }, identity = yawPitch(0);
const radians = degrees => degrees * Math.PI / 180;
const never = () => assert.fail("This path must not decode an image");

// A stationary scene through a rectilinear camera. Positive yaw looks left in
// the original camera's coordinates, independently of the panorama API.
function render(field, yaw = 0, ev = 0, bias = 0, width = 224) {
  const height = Math.round(width * field.y / field.x);
  const data = new Uint8ClampedArray(width * height * 4);
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
    const u = ((x - bias + .5) * 2 / width - 1) * field.x;
    const v = (1 - (y + .5) * 2 / height) * field.y;
    const z = Math.cos(yaw) + Math.sin(yaw) * u;
    const xx = (Math.cos(yaw) * u - Math.sin(yaw)) / z, yy = v / z;
    const value = Math.max(0, Math.min(255, Math.round((128 +
      30 * Math.sin(36 * xx + 4 * yy) + 24 * Math.cos(55 * yy + 12 * xx) +
      18 * Math.sin(17 * xx * yy)) * 2 ** (-ev / 2.2))));
    const i = (y * width + x) * 4;
    data[i] = data[i + 1] = data[i + 2] = value;
    data[i + 3] = 255;
  }
  return { data, width, height };
}
function solidFrame(field = source, value = 100) {
  const frame = render(field);
  for (let i = 0; i < frame.data.length; i += 4)
    frame.data[i] = frame.data[i + 1] = frame.data[i + 2] = value;
  return frame;
}
function settle(pano, frame, pose = identity, time = 0, cameraEV = 8, step = 100) {
  return Array.from({ length: 5 }, (_, i) => pano.addFrame(frame, pose, cameraEV, time + i * step));
}
function completePanorama(width = 115, center = identity) {
  const field = { x: 1.2, y: .6 }, frame = render(field);
  for (let y = 0; y < frame.height; y++) for (let x = 0; x < frame.width; x++) {
    const i = (y * frame.width + x) * 4;
    frame.data[i] = Math.round(x * 255 / (frame.width - 1));
    frame.data[i + 1] = Math.round(y * 255 / (frame.height - 1));
    frame.data[i + 2] = 32;
  }
  const pano = new Panorama({ target, source: field, width, height: width });
  pano.setCenter(center);
  settle(pano, frame, center);
  assert.equal(pano.ready, true, "Fixture must acquire the complete atlas");
  return pano;
}
function opaqueCount(image) {
  let count = 0;
  for (let i = 3; i < image.data.length; i += 4) if (image.data[i]) count++;
  return count;
}
function assertTransparentBlack(image) {
  for (let i = 0; i < image.data.length; i += 4) if (!image.data[i + 3])
    assert.deepEqual(Array.from(image.data.subarray(i, i + 4)), [0, 0, 0, 0]);
}
function centerCode(image, channel = 0) {
  return image.data[(Math.floor(image.height / 2) * image.width + Math.floor(image.width / 2)) * 4 + channel];
}
function closePoint(actual, expected, tolerance = 1e-10) {
  assert.ok(actual && expected);
  assert.ok(Math.abs(actual.x - expected.x) < tolerance, `x: ${actual.x} vs ${expected.x}`);
  assert.ok(Math.abs(actual.y - expected.y) < tolerance, `y: ${actual.y} vs ${expected.y}`);
}
function matrixAngle(a, b) {
  const trace = a.reduce((sum, value, i) => sum + value * b[i], 0);
  return Math.acos(Math.max(-1, Math.min(1, (trace - 1) / 2)));
}

test("analysis downsampling and rotated display never exceed atlas resolution", () => {
  const pano = completePanorama(), full = pano.view();
  const mini = pano.view(null, null, 40), oversized = pano.view(null, null, 800);
  assert.deepEqual([full.image.width, full.image.height], [100, 40]);
  assert.deepEqual([mini.image.width, mini.image.height], [40, 16]);
  assert.deepEqual([oversized.image.width, oversized.image.height], [100, 40]);
  assert.equal(mini.missing, false);
  const rotated = pano.view(null, null, 40, 90);
  assert.deepEqual([rotated.image.width, rotated.image.height], [16, 40]);
});
test("missing direct-crop field stays transparent and preserves target aspect", () => {
  const frame = render(source), wide = cropFrame(frame, source, target);
  assert.equal(wide.data[Math.floor(wide.height / 2) * wide.width * 4 + 3], 0);
  assert.equal(wide.data[(Math.floor(wide.height / 2) * wide.width + Math.floor(wide.width / 2)) * 4 + 3], 255);
  assertTransparentBlack(wide);
  assert.ok(Math.abs(wide.width / wide.height - target.x / target.y) < .02);
  const small = cropFrame(frame, source, { x: .3, y: .2 });
  assert.equal(opaqueCount(small), small.width * small.height);
});
test("dark-blue exposure normalization is performed in linear RGB", () => {
  assert.deepEqual(scaleColor(0, 0, 32, 8), [0, 0, 95]);
  assert.deepEqual(scaleColor(100, 80, 50, 1), [100, 80, 50]);
});
test("sensor motion projects existing pixels without decoding or registration", () => {
  const pano = new Panorama({ target, source, width: 160 });
  pano.setCenter(identity);
  settle(pano, render(source));
  const initial = pano.atlas(), count = pano.captures;
  pano.register = () => assert.fail("Moving sensor must not register a frame");
  for (let i = 1; i <= 12; i++) {
    const pose = yawPitch(i * .025);
    const info = pano.capture({ pose, time: 400 + i * 100, frame: never, trackingFrame: never, cameraEV: 8 });
    assert.equal(info.settling, true);
    assert.equal(info.accepted, false);
    pano.view(pose);
  }
  assert.equal(pano.captures, count);
  assert.deepEqual(pano.atlas().data, initial.data);
});
test("350 ms of sub-0.45-degree jitter captures once without repeating the same direction", () => {
  const pano = new Panorama({ target, source, width: 160 });
  pano.setCenter(identity);
  let reads = 0, registrations = 0;
  const register = pano.register.bind(pano);
  pano.register = (...args) => { registrations++; return register(...args); };
  const tick = (time, degrees) => pano.capture({ pose: yawPitch(radians(degrees)), time, cameraEV: 8,
    frame: () => { reads++; return render(source, radians(degrees)); }, trackingFrame: never });
  for (const [time, degrees] of [[0, 0], [100, .2], [200, -.2], [300, .3], [349, -.1]]) {
    const info = tick(time, degrees);
    assert.equal(info.settling, true);
    assert.equal(info.accepted, false);
    assert.equal(reads, 0);
  }
  const first = tick(350, .1);
  assert.equal(first.settling, false);
  assert.equal(first.accepted, true);
  for (let time = 450; time <= 1450; time += 100) tick(time, .15);
  assert.equal(reads, 1);
  assert.equal(registrations, 1);
  assert.equal(pano.captures, 1);
});
test("addFrame shares the stable-keyframe gate rather than bypassing it", () => {
  const pano = new Panorama({ target, source, width: 120 });
  pano.setCenter(identity);
  const results = settle(pano, render(source));
  assert.deepEqual(results.map(r => r.accepted), [false, false, false, false, true]);
  assert.equal(pano.captures, 1);
});
test("slow cumulative motion never qualifies as stillness from adjacent-frame changes", () => {
  const pano = new Panorama({ target, source, width: 120 });
  pano.setCenter(identity);
  for (let i = 0; i <= 30; i++) {
    const info = pano.capture({ pose: yawPitch(radians(i * .2)), time: i * 100,
      frame: never, trackingFrame: never, cameraEV: 8 });
    assert.equal(info.settling, true);
  }
  assert.equal(pano.captures, 0);
  assert.equal(pano.coverage, 0);
});
test("an interrupted clock interval requires a new stable interval", () => {
  const pano = new Panorama({ target, source, width: 120 });
  pano.setCenter(identity);
  const frame = render(source);
  for (const time of [0, 100, 200, 1000, 1100, 1200, 1300])
    assert.equal(pano.addFrame(frame, identity, 8, time).accepted, false);
  assert.equal(pano.addFrame(frame, identity, 8, 1400).accepted, true);
});
test("foreground changes with a stationary sensor cannot rotate or expand the atlas", () => {
  const pano = new Panorama({ target, source, width: 160 });
  pano.setCenter(identity);
  settle(pano, render(source));
  const initial = pano.atlas();
  for (let i = 1; i <= 15; i++) {
    const info = pano.capture({ pose: identity, time: 400 + i * 100,
      frame: () => solidFrame(source, i * 13), trackingFrame: never, cameraEV: 8 });
    assert.equal(info.accepted, false);
    closePoint(info.aim, { x: .5, y: .5 });
  }
  assert.equal(pano.captures, 1);
  assert.deepEqual(pano.atlas().data, initial.data);
});
test("overlapping stable sensor frames acquire the 115% atlas and invalidate old crops", () => {
  const pano = new Panorama({ target, source, width: 288 });
  pano.setCenter(identity);
  const start = settle(pano, render(source)).at(-1);
  assert.ok(start.coverage > .58 && start.coverage < .64);
  assert.equal(start.captures, 1);
  assert.notEqual(start.next, null);
  assert.equal(pano.view(identity).missing, true);
  let time = 500;
  for (const angle of [.24, .43, -.24, -.43]) {
    settle(pano, render(source, angle, 1, 3), yawPitch(angle), time, 9);
    time += 500;
  }
  assert.equal(pano.coverage, 1);
  assert.equal(pano.ready, true);
  assert.equal(pano.guide().next, null);
  const view = pano.view(identity);
  assert.equal(view.cameraEV, 8);
  assert.equal(view.missing, false);
  assert.ok(Math.abs(view.image.width / view.image.height - 2.5) < .03);
});
test("sensorless thumbnail tracking is throttled to at most one read per 140 ms", () => {
  const pano = new Panorama({ target, source, width: 120 });
  let thumbnails = 0;
  const tick = time => pano.capture({ time, collect: false, frame: never,
    trackingFrame: () => { thumbnails++; return render(source, 0, 0, 0, 112); } });
  for (const time of [0, 20, 100, 139]) tick(time);
  assert.equal(thumbnails, 1);
  tick(140);
  assert.equal(thumbnails, 2);
});
test("sensorless scanning tracks overlapping thumbnails and fills the atlas after pauses", () => {
  const pano = new Panorama({ target, source, width: 224 });
  pano.setCenter();
  let time = 0, thumbnails = 0, fullFrames = 0;
  const tick = angle => {
    const info = pano.capture({ time, cameraEV: null,
      frame: () => { fullFrames++; return render(source, angle); },
      trackingFrame: () => { thumbnails++; return render(source, angle, 0, 0, 112); } });
    time += 200;
    return info;
  };
  for (let i = 0; i < 3; i++) tick(0);
  const angles = Array.from({ length: 12 }, (_, i) => (i + 1) * .04).concat(
    Array.from({ length: 24 }, (_, i) => (11 - i) * .04));
  for (const angle of angles) {
    tick(angle);
    if (Math.round(angle / .04) % 3 === 0) { tick(angle); tick(angle); }
  }
  assert.equal(thumbnails, time / 200);
  assert.ok(fullFrames > 3 && fullFrames < thumbnails);
  assert.ok(pano.captures > 3);
  assert.equal(pano.coverage, 1);
  assert.equal(pano.view().cameraEV, null);
});
test("lost texture cannot qualify as a stable keyframe or erase observed pixels", () => {
  const pano = new Panorama({ target, source, width: 160 });
  settle(pano, render(source), null, 0, 8, 200);
  const before = pano.atlas();
  for (let time = 1000; time <= 2000; time += 200) {
    const info = pano.capture({ time, cameraEV: 8, frame: never,
      trackingFrame: () => solidFrame(source, 62) });
    assert.equal(info.needsOverlap, true);
    assert.equal(info.accepted, false);
    assert.equal(info.settling, true);
  }
  assert.deepEqual(pano.atlas().data, before.data);
});
test("missing exposure metadata rejects a new direction before decoding and permits recovery", () => {
  const pano = new Panorama({ target, source, width: 160 });
  pano.setCenter(identity);
  settle(pano, render(source));
  const before = pano.atlas(), pose = yawPitch(.12);
  let info;
  for (let time = 500; time <= 900; time += 100)
    info = pano.capture({ time, pose, cameraEV: null, frame: never, trackingFrame: never });
  assert.equal(info.accepted, false);
  assert.match(info.reason, /曝光/);
  assert.equal(pano.cameraEV, 8);
  assert.deepEqual(pano.atlas().data, before.data);
  info = pano.capture({ time: 1000, pose, cameraEV: 9,
    frame: () => render(source, .12, 1), trackingFrame: never });
  assert.equal(info.accepted, true);
  assert.equal(pano.cameraEV, 8);
});
test("disconnected sensor islands are rejected before residual registration", () => {
  const field = { x: .2, y: .2 };
  const pano = new Panorama({ target: { x: 2, y: 1 }, source: field, width: 160 });
  pano.setCenter(identity);
  settle(pano, solidFrame(field));
  const before = pano.atlas(), coverage = pano.coverage;
  pano.register = () => assert.fail("Disconnected frame must not be registered");
  const results = settle(pano, solidFrame(field), yawPitch(.8), 500);
  assert.equal(results.at(-1).accepted, false);
  assert.equal(results.at(-1).needsOverlap, true);
  assert.equal(pano.coverage, coverage);
  assert.equal(pano.captures, 1);
  assert.deepEqual(pano.atlas().data, before.data);
  pano.capture({ pose: yawPitch(.8), time: 1000, cameraEV: 8, frame: never, trackingFrame: never });
});
test("actual yaw and pitch select the correct side of the acquired scene", () => {
  const pano = completePanorama(288), middle = pano.view(identity).image;
  assert.ok(centerCode(pano.view(yawPitch(.2)).image) < centerCode(middle));
  assert.ok(centerCode(pano.view(yawPitch(-.2)).image) > centerCode(middle));
  assert.ok(centerCode(pano.view(yawPitch(0, .15)).image, 1) < centerCode(middle, 1));
  assert.ok(centerCode(pano.view(yawPitch(0, -.15)).image, 1) > centerCode(middle, 1));
});
test("an arbitrary sensor origin preserves relative yaw and pitch projection", () => {
  const origin = orientationMatrix(37, 74, -21);
  const local = yawPitch(.24, .15);
  // Independent ordinary matrix multiplication creates a physical sensor pose;
  // the panorama must remove the world basis instead of transposing the result.
  const sensor = Array.from({ length: 9 }, (_, i) =>
    [0, 1, 2].reduce((sum, k) =>
      sum + origin[Math.floor(i / 3) * 3 + k] * local[k * 3 + i % 3], 0));
  const reference = completePanorama(288), pano = completePanorama(288, origin);
  assert.deepEqual(pano.view(sensor).image.data, reference.view(local).image.data);
  closePoint(pano.guide(0, sensor).aim, reference.guide(0, local).aim);
  pano.capture({ pose: sensor, time: 500, collect: false, frame: never, trackingFrame: never });
  assert.deepEqual(pano.view(sensor).image.data, reference.view(local).image.data);
});
test("completed-mode tracking updates a stable crop without decoding or collecting", () => {
  const pano = new Panorama({ target, source, width: 160 });
  pano.setCenter(identity);
  settle(pano, render(source));
  const before = pano.atlas(), centered = pano.view(identity).image;
  const pose = yawPitch(.12);
  let info;
  for (let time = 500; time <= 1000; time += 100)
    info = pano.capture({ pose, time, collect: false, frame: never, trackingFrame: never, cameraEV: 8 });
  assert.equal(info.settling, false);
  assert.equal(info.accepted, false);
  assert.notDeepEqual(pano.view(pose).image.data, centered.data);
  assert.deepEqual(pano.atlas().data, before.data);
  const resumed = pano.capture({ pose, time: 1100, cameraEV: 8,
    frame: () => render(source, .12), trackingFrame: never });
  assert.equal(resumed.accepted, true);
});
test("actual view leaves progressively larger black regions, turns transparent behind, and returns home", () => {
  const pano = completePanorama(288), initial = pano.atlas(), centered = pano.view(identity);
  let previous = centered.coverage;
  for (const angle of [.15, .35, .7, 1.2]) {
    const view = pano.view(yawPitch(angle));
    assert.ok(view.coverage < previous, `${angle}: ${view.coverage} vs ${previous}`);
    assert.equal(view.missing, true);
    assertTransparentBlack(view.image);
    previous = view.coverage;
  }
  const behind = pano.view(yawPitch(Math.PI));
  assert.equal(behind.coverage, 0);
  assert.equal(behind.missing, true);
  assert.ok(behind.image.data.every(v => v === 0));
  assert.equal(pano.view(identity).coverage, 1);
  assert.deepEqual(pano.view(identity).image.data, centered.image.data);
  assert.deepEqual(pano.atlas().data, initial.data);
});
test("transparent projection regions do not enter histograms or spot measurements", () => {
  const pano = completePanorama(288), image = pano.view(yawPitch(.7)).image, hist = histogram(image);
  assert.equal(hist.reduce((a, b) => a + b, 0), opaqueCount(image));
  const tampered = { ...image, data: image.data.slice() };
  for (let i = 0; i < tampered.data.length; i += 4) if (!tampered.data[i + 3])
    tampered.data[i] = tampered.data[i + 1] = tampered.data[i + 2] = 255;
  assert.deepEqual(histogram(tampered), hist);
  let emptySpot, realSpot;
  for (let y = 3; y < image.height - 3 && (!emptySpot || !realSpot); y++)
    for (let x = 3; x < image.width - 3; x++) {
      const count = Array.from({ length: 25 }, (_, i) =>
        image.data[((y + Math.floor(i / 5) - 2) * image.width + x + i % 5 - 2) * 4 + 3]).filter(Boolean).length;
      if (!count) emptySpot = { x: (x + .5) / image.width, y: (y + .5) / image.height };
      if (count === 25) realSpot = { x: (x + .5) / image.width, y: (y + .5) / image.height };
    }
  assert.ok(emptySpot && realSpot);
  assert.ok(Number.isNaN(spot(image, emptySpot.x, emptySpot.y)));
  assert.ok(Number.isFinite(spot(image, realSpot.x, realSpot.y)));
  const behind = pano.view(yawPitch(Math.PI)).image;
  assert.equal(histogram(behind).reduce((a, b) => a + b, 0), 0);
  assert.ok(Number.isNaN(spot(behind, .5, .5)));
  const black = { width: 1, height: 1, data: new Uint8ClampedArray([0, 0, 0, 255]) };
  assert.equal(histogram(black)[0], 1); // Real black is a sample; an unobserved ray is not.
});
test("home arrow points toward center for either yaw and is absent at center", () => {
  const pano = completePanorama();
  assert.equal(pano.guide(0, identity).home, null);
  const left = pano.guide(0, yawPitch(1.2)), right = pano.guide(0, yawPitch(-1.2));
  assert.ok(left.aim.x < 0 && left.home.x > .99);
  assert.ok(right.aim.x > 1 && right.home.x < -.99);
  assert.ok(Math.abs(left.home.y) < 1e-12);
  const behind = pano.guide(0, yawPitch(Math.PI));
  assert.ok(behind.home && Object.values(behind.home).every(Number.isFinite));
});
test("quarter-turn helpers rotate identifiable pixels and points without losing alpha", () => {
  const frame = { width: 3, height: 2, data: new Uint8ClampedArray(24) };
  for (let i = 0; i < 6; i++) frame.data.set([i + 1, 20 + i, 40 + i, i * 51], i * 4);
  const pixels = image => Array.from({ length: image.width * image.height }, (_, i) => image.data[i * 4]);
  const expected = [[1, 2, 3, 4, 5, 6], [4, 1, 5, 2, 6, 3], [6, 5, 4, 3, 2, 1], [3, 6, 2, 5, 1, 4]];
  const points = [{ x: .2, y: .7 }, { x: .3, y: .2 }, { x: .8, y: .3 }, { x: .7, y: .8 }];
  for (const [i, turn] of [0, 90, 180, 270].entries()) {
    const rotated = turnFrame(frame, turn);
    assert.deepEqual(pixels(rotated), expected[i]);
    assert.deepEqual([rotated.width, rotated.height], i % 2 ? [2, 3] : [3, 2]);
    assert.deepEqual(turnFrame(rotated, -turn).data, frame.data);
    closePoint(turnPoint(points[0], turn), points[i]);
  }
});
test("display rotation keeps projected pixels, map markers, and home arrows consistent", () => {
  const pano = completePanorama(288), pose = yawPitch(1.2, .15);
  const view = pano.view(pose).image, guide = pano.guide(0, pose);
  assert.ok(guide.next === null && guide.home);
  for (const turn of [0, 90, 180, 270]) {
    const rotated = pano.view(pose, null, Infinity, turn).image;
    assert.deepEqual(rotated, turnFrame(view, turn));
    assert.deepEqual(histogram(rotated), histogram(view));
    const mapped = pano.guide(turn, pose);
    closePoint(mapped.aim, turnPoint(guide.aim, turn));
    const home = turnPoint({ x: .5 + guide.home.x / 2, y: .5 + guide.home.y / 2 }, turn);
    closePoint(mapped.home, { x: 2 * home.x - 1, y: 2 * home.y - 1 });
  }
  const incomplete = new Panorama({ target, source, width: 120 });
  incomplete.setCenter(identity);
  settle(incomplete, render(source));
  const next = incomplete.guide().next;
  assert.ok(next);
  for (const turn of [90, 180, 270]) closePoint(incomplete.guide(turn).next, turnPoint(next, turn));
});
test("sensor loss falls back to image tracking and restoration preserves the current view", () => {
  const pano = new Panorama({ target, source, width: 160 });
  pano.setCenter(identity);
  settle(pano, render(source));
  pano.capture({ pose: yawPitch(.18), time: 500, collect: false, frame: never, trackingFrame: never });
  const beforeLoss = [...pano.orientation];
  pano.capture({ time: 700, collect: false, frame: never, trackingFrame: () => render(source, .18) });
  assert.ok(matrixAngle(pano.orientation, beforeLoss) < 1e-7);
  pano.capture({ time: 900, collect: false, frame: never, trackingFrame: () => render(source, .22) });
  assert.ok(matrixAngle(pano.orientation, beforeLoss) > .02);
  const tracked = [...pano.orientation], beforeReturn = pano.view().image;
  pano.capture({ pose: yawPitch(1), time: 1100, collect: false, frame: never, trackingFrame: never });
  assert.ok(matrixAngle(pano.orientation, tracked) < 1e-7);
  assert.deepEqual(pano.view(yawPitch(1)).image.data, beforeReturn.data);
  pano.capture({ pose: yawPitch(1.05), time: 1300, collect: false, frame: never, trackingFrame: never });
  assert.ok(Math.abs(matrixAngle(pano.orientation, tracked) - .05) < 1e-7);
});
test("dragging starts at the current view and screen-relative pan is rotation-consistent", () => {
  const reference = completePanorama(288);
  reference.capture({ pose: yawPitch(.3), time: 500, collect: false, frame: never, trackingFrame: never });
  const before = reference.view(yawPitch(.3));
  reference.pan(0, 0);
  assert.deepEqual(reference.view().image.data, before.image.data);
  assert.ok(matrixAngle(reference.orientation, yawPitch(.3)) < 1e-7);
  reference.pan(.02, -.01);
  assert.ok(matrixAngle(reference.orientation, yawPitch(.3)) < .03);
  assert.ok(matrixAngle(reference.orientation, identity) > .25);
  const deltas = [[.02, -.01], [.01, .02], [-.02, .01], [-.01, -.02]];
  for (const [i, turn] of [0, 90, 180, 270].entries()) {
    const pano = completePanorama(288);
    pano.capture({ pose: yawPitch(.3), time: 500, collect: false, frame: never, trackingFrame: never });
    pano.pan(...deltas[i], turn);
    assert.ok(matrixAngle(pano.orientation, reference.orientation) < 1e-7);
  }
});
test("DeviceOrientation is finite, orthonormal, and uses inverse display-axis rotation", () => {
  assert.equal(orientationMatrix(null, 0, 0), null);
  assert.equal(orientationMatrix(0, Infinity, 0), null);
  assert.ok(orientationMatrix(0, 0, 0).every((v, i) => Math.abs(v - identity[i]) < 1e-12));
  const quarter = orientationMatrix(0, 0, 0, 90);
  assert.ok(Math.abs(quarter[1] - 1) < 1e-12);
  assert.ok(Math.abs(quarter[3] + 1) < 1e-12);
  for (const turn of [0, 90, 180, 270]) {
    const pose = orientationMatrix(60, 80, 25, turn);
    for (let col = 0; col < 3; col++)
      assert.ok(Math.abs(Math.hypot(pose[col], pose[col + 3], pose[col + 6]) - 1) < 1e-12);
    assert.ok(Math.abs(pose[0] * pose[1] + pose[3] * pose[4] + pose[6] * pose[7]) < 1e-12);
  }
});
