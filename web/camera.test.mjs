import test from "node:test";
import assert from "node:assert/strict";
import { captureExposure, parseTime, referenceSignature } from "./camera.js";
import { exposureEV } from "./core.js";

test("time input accepts seconds and fractions, rejects invalid denominators", () => {
  assert.equal(parseTime("1/125"), 1 / 125);
  assert.equal(parseTime("12.5"), 12.5);
  for (const value of ["", "0", "-1", "1/0", "a", "1/2/3"])
    assert.ok(Number.isNaN(parseTime(value)));
});

test("unknown AE remains relative and explicit browser AE choice follows changing settings", () => {
  const settings = { exposureMode: "continuous", exposureTime: 100, iso: 100 };
  const profile = { mode: "auto", aperture: 2 };
  assert.equal(captureExposure(settings, profile), null);
  assert.equal(captureExposure({}, { ...profile, mode: "reported" }), null);
  const first = captureExposure(settings, { ...profile, mode: "reported" });
  const second = captureExposure(
    { ...settings, exposureTime: 200 },
    { ...profile, mode: "reported" },
  );
  assert.equal(first.t, 0.01);
  assert.ok(Math.abs(exposureEV(first) - exposureEV(second) - 1) < 1e-12);
  assert.equal(captureExposure(settings, { mode: "reported" }), null);
});

test("fixed metadata and confirmed manual camera exposure use acquisition ISO", () => {
  const settings = { exposureMode: "manual", exposureTime: 200, iso: 80 };
  assert.deepEqual(captureExposure(settings, { mode: "auto", aperture: 1.8 }), {
    t: 0.02,
    a: 1.8,
    iso: 80,
    source: "固定参数 · 预览估测",
  });
  assert.equal(
    captureExposure({}, { mode: "manual", aperture: 2, seconds: 0.1, iso: 80 }),
    null,
  );
  assert.equal(
    captureExposure(
      {},
      { mode: "manual", aperture: 2, seconds: 0.1, iso: 80, confirmed: true },
    ).iso,
    80,
  );
  const manual = {
    mode: "manual",
    aperture: 2,
    seconds: 0.1,
    iso: 80,
    confirmed: true,
  };
  assert.equal(captureExposure({ exposureMode: "continuous" }, manual), null);
  assert.equal(
    captureExposure(
      { exposureMode: "manual", exposureTime: 200, iso: 80 },
      manual,
    ),
    null,
  );
  assert.equal(
    captureExposure(
      { exposureMode: "manual", exposureTime: 1000, iso: 160 },
      manual,
    ),
    null,
  );
  assert.equal(
    captureExposure(
      { exposureMode: "manual", exposureTime: 1000, iso: 80 },
      manual,
    ).t,
    0.1,
  );
});

test("known-EV references expire when metadata or acquisition inputs change", () => {
  const settings = { exposureMode: "manual", exposureTime: 1000, iso: 80 };
  const profile = { aperture: 2, seconds: 0.1, iso: 80 };
  const signature = referenceSignature(settings, profile);
  assert.equal(
    referenceSignature({ ...settings, width: 640 }, { ...profile }),
    signature,
  );
  assert.notEqual(
    referenceSignature({ ...settings, exposureTime: 500 }, profile),
    signature,
  );
  assert.notEqual(
    referenceSignature(settings, { ...profile, aperture: 2.8 }),
    signature,
  );
  assert.notEqual(
    referenceSignature(settings, { ...profile, iso: 160 }),
    signature,
  );
});
