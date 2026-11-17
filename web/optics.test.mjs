import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import {
  modelFromUA,
  deviceIdentity,
  matchDevice,
  matchLens,
  lensEquivalent,
  completeOptics,
  makeOptics,
  opticsField,
} from "./optics.js";

const catalog = JSON.parse(
  fs.readFileSync(new URL("./data/cameras.json", import.meta.url)),
);
const devices = catalog.devices;
const near = (actual, expected) =>
  assert.ok(Math.abs(actual - expected) < 1e-9, `${actual} != ${expected}`);

test("UA parsing rejects reduced Android and generic iOS but reads Build and WebView models", () => {
  assert.equal(
    modelFromUA("Mozilla/5.0 (Linux; Android 10; K) Chrome/143.0"),
    "",
  );
  assert.equal(
    modelFromUA("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)"),
    "",
  );
  assert.equal(
    modelFromUA("Mozilla/5.0 (Linux; Android 15; SM-F9660 Build/ABC; wv)"),
    "SM-F9660",
  );
  assert.equal(
    modelFromUA("Mozilla/5.0 (Linux; Android 15; zh-cn; Xiaomi 15 Build/ABC)"),
    "Xiaomi 15",
  );
});

test("Client Hints model takes precedence; rejected hints fall back without blocking", async () => {
  const ua = "Mozilla/5.0 (Linux; Android 10; K)";
  const identity = await deviceIdentity({
    userAgent: ua,
    userAgentData: {
      getHighEntropyValues: async (keys) => {
        assert.deepEqual(keys, ["model", "platform"]);
        return { model: "SM-F9660" };
      },
    },
  });
  assert.equal(identity.source, "UA Client Hints");
  assert.equal(matchDevice(devices, identity).id, "samsung-fold7");
  const fallback = await deviceIdentity({
    userAgent: ua.replace("K)", "SM-F9560)"),
    userAgentData: {
      getHighEntropyValues: async () => {
        throw new Error("denied");
      },
    },
  });
  assert.equal(matchDevice(devices, fallback).id, "samsung-fold6");
  assert.equal((await deviceIdentity({ userAgent: ua })).model, "");
});

test("model matching is exact, anchored and rejects ambiguous catalog entries", () => {
  assert.equal(matchDevice(devices, { model: "iphone" }), null);
  assert.equal(matchDevice(devices, { model: "Xiaomi 15 unknown" }), null);
  assert.equal(matchDevice(devices, { model: "SM-F9660-unknown" }), null);
  assert.equal(
    matchDevice(devices, { model: "SAMSUNG SM-F9660" }).id,
    "samsung-fold7",
  );
  assert.equal(matchDevice(devices, { model: "Xiaomi 15" }).id, "xiaomi-15");
  assert.equal(
    matchDevice([...devices, devices.find((d) => d.id === "samsung-fold7")], { model: "SM-F9660" }),
    null,
  );
});

test("official Android hardware codes and shared Apple variants match the intended optical group", () => {
  for (const [model, id] of [
    ["23127PN0CG", "xiaomi-14"],
    ["24030PN60G", "xiaomi-14-ultra"],
    ["24129PN74G", "xiaomi-15"],
    ["25010PN30G", "xiaomi-15-ultra"],
    ["CPH2651", "oppo-find-x8"],
    ["PKB110", "oppo-find-x8"],
    ["CPH2659", "oppo-find-x8-pro"],
    ["PKC110", "oppo-find-x8-pro"],
    ["V2413", "vivo-x200-pro"],
    ["V2405A", "vivo-x200-pro"],
    ["V2405DA", "vivo-x200-pro"],
    ["HUAWEI HLS-AL00", "huawei-mate90"],
    ["HONOR SDY-AN00", "honor-70-pro"],
    ["iPhone 16 Pro Max", "apple-iphone-16-pro"],
    ["iPhone 14 Plus", "apple-iphone-14"],
  ]) {
    assert.equal(matchDevice(devices, { model }).id, id);
    assert.equal(matchDevice(devices, { model: model + " unknown" }), null);
  }
});

test("lens matching uses explicit labels and never opaque camera order", () => {
  const fold = devices.find((d) => d.id === "samsung-fold7");
  assert.equal(matchLens(fold, "camera2 0, facing back", "environment"), null);
  assert.equal(matchLens(fold, "Back Dual Wide Camera", "environment"), null);
  assert.equal(
    matchLens(fold, "Back Ultra Wide Camera", "environment").id,
    "ultrawide",
  );
  assert.equal(matchLens(fold, "后置主摄", "environment").id, "wide");
  assert.equal(matchLens(fold, "Front Wide Camera", "user"), null);
  const s25 = devices.find((d) => d.id === "samsung-s25-ultra");
  assert.equal(matchLens(s25, "Back Telephoto Camera", "environment"), null);
});

test("unspecified manufacturer FOV is never silently converted to a focal length", () => {
  assert.equal(lensEquivalent({ manufacturerFov: 85, fovAxis: "unspecified" }), null);
  const equivalent = lensEquivalent({ diagonalFov: 90 });
  near(equivalent, Math.hypot(36, 24) / 2);
  assert.equal(lensEquivalent({ equivalentMm: 24 }), 24);
});

test("equivalent focal length preserves the 35mm diagonal on a 4:3 sensor", () => {
  const field = opticsField({ mode: "equivalent", equivalentMm: 24 }, 400, 300);
  near(Math.hypot(field.x, field.y), Math.hypot(36, 24) / 48);
  near(field.x / field.y, 4 / 3);
  // It is not the horizontal angle of a 36x24 full-frame sensor.
  assert.ok(field.hfov < (2 * Math.atan(36 / 48) * 180) / Math.PI);
});

test("preview aspect crops the short axis, rotation swaps both angles", () => {
  const optics = { mode: "equivalent", equivalentMm: 24 };
  const sensor = opticsField(optics, 400, 300);
  const wide = opticsField(optics, 1280, 720);
  const portrait = opticsField(optics, 720, 1280);
  near(wide.x, sensor.x);
  assert.ok(wide.y < sensor.y);
  near(portrait.x, wide.y);
  near(portrait.y, wide.x);
  near(portrait.hfov, wide.vfov);
});

test("equivalent field includes zoom in tangent space rather than degrees", () => {
  const optics = { mode: "equivalent", equivalentMm: 24 };
  const plain = opticsField(optics, 1280, 720);
  const zoomed = opticsField(optics, 1280, 720, 2);
  near(zoomed.x, plain.x / 2);
  near(zoomed.y, plain.y / 2);
  assert.notEqual(zoomed.hfov, plain.hfov / 2);
});

test("measured horizontal field survives rotation and relative zoom without changing its anchor", () => {
  const optics = makeOptics(
    { mode: "horizontal", hfov: 70, aperture: 2.3 },
    { width: 1280, height: 720, zoom: 2 },
  );
  const field = opticsField(optics, 1280, 720, 2);
  near(field.hfov, 70);
  const portrait = opticsField(optics, 720, 1280, 2);
  near(portrait.vfov, field.hfov);
  near(portrait.hfov, field.vfov);
  near(opticsField(optics, 1280, 720, 4).x, field.x / 2);
});

test("measured preview aspect reuse stays inside its known coverage", () => {
  const optics = makeOptics(
    { mode: "horizontal", hfov: 70, aperture: 2 },
    { width: 1280, height: 720 },
  );
  const reference = opticsField(optics, 1280, 720);
  for (const [w, h] of [
    [400, 300],
    [300, 400],
    [1920, 800],
  ]) {
    const field = opticsField(optics, w, h);
    assert.ok(Math.max(field.x, field.y) <= reference.x + 1e-10);
    assert.ok(Math.min(field.x, field.y) <= reference.y + 1e-10);
  }
});

test("onboarding requires both aperture and an independently valid field", () => {
  assert.equal(
    makeOptics({ mode: "horizontal", hfov: 70, aperture: "" }),
    null,
  );
  assert.equal(
    makeOptics({ mode: "horizontal", hfov: 180, aperture: 2 }),
    null,
  );
  assert.equal(
    makeOptics({ mode: "equivalent", equivalentMm: 0, aperture: 2 }),
    null,
  );
  assert.equal(
    makeOptics({ mode: "equivalent", equivalentMm: 24, aperture: Infinity }),
    null,
  );
  assert.equal(completeOptics({ mode: "oops", hfov: 70, aperture: 2 }), false);
  assert.ok(
    makeOptics({ mode: "equivalent", equivalentMm: 23, aperture: 1.62 }),
  );
  assert.equal(
    opticsField({ mode: "equivalent", equivalentMm: 24 }, 0, 720),
    null,
  );
  assert.equal(
    opticsField({ mode: "equivalent", equivalentMm: 24 }, Infinity, 720),
    null,
  );
});

test("catalog contains unique, sourced, positive optical entries, with compilable model patterns", () => {
  const ids = new Set();
  for (const device of devices) {
    assert.ok(!ids.has(device.id));
    ids.add(device.id);
    const lenses = new Set();
    for (const pattern of device.modelPatterns || []) {
      assert.ok(pattern.startsWith("^") && pattern.endsWith("$"));
      new RegExp(pattern);
    }
    for (const lens of device.lenses) {
      assert.ok(!lenses.has(lens.id));
      lenses.add(lens.id);
      assert.ok(lens.aperture >= 0.5 && lens.aperture <= 64);
      assert.equal(new URL(lens.source || device.source).protocol, "https:");
      assert.ok(lensEquivalent(lens) || lens.coverageUnknown === true ||
        (lens.manufacturerFov > 0 && lens.fovAxis === "unspecified"),
        `${device.id}/${lens.id}: unknown coverage must be explicit`);
      if (!lensEquivalent(lens)) {
        assert.ok(lens.note && lens.noteEn, `${device.id}/${lens.id}: manual setup explanation`);
      }
      if (lens.equivalentMm) {
        for (const [w, h] of [
          [1280, 720],
          [720, 1280],
        ]) {
          const field = opticsField(
            { mode: "equivalent", equivalentMm: lens.equivalentMm },
            w,
            h,
          );
          assert.ok(field.hfov > 0 && field.hfov < 175);
        }
      }
    }
  }
});

test("catalog eligibility records launch prices and preserves legacy support without guessing models", () => {
  const brands = new Set();
  for (const device of devices) {
    brands.add(device.brand);
    assert.equal(matchDevice(devices, { model: device.name })?.id, device.id,
      `${device.name}: ambiguous display name`);
    assert.ok(device.msrpCny > 4000, `${device.id}: qualifying China launch MSRP`);
    assert.ok(device.priceNote, `${device.id}: qualifying configuration`);
    assert.equal(new URL(device.priceSource).protocol, "https:");
    assert.ok(device.legacy || (device.launchYear >= 2022 && device.launchYear <= 2026), device.id);
    if (device.modelPatterns?.length) assert.ok(device.modelSources?.length, device.id);
  }
  assert.deepEqual([...brands].sort(), ["Apple", "HONOR", "HUAWEI", "OPPO", "Samsung", "Xiaomi", "vivo"].sort());
  for (const model of ["iPhone", "HUAWEI", "HONOR", "Android", "K"]) {
    assert.equal(matchDevice(devices, { model }), null);
  }
});

test("Samsung presets fill documented equivalents while unverified lenses stay manual", () => {
  const fold = matchDevice(devices, { model: "SM-F9660" });
  assert.deepEqual(fold.lenses.map(lensEquivalent), [24, 13, 67]);
  assert.ok(fold.lenses.every((lens) => new URL(lens.equivalentSource).protocol === "https:"));
  for (const id of ["samsung-s24-ultra", "samsung-s25-ultra"]) {
    const savedLensIds = devices.find((d) => d.id === id).lenses.map((lens) => lens.id);
    assert.ok(savedLensIds.includes("telephoto-3x") && savedLensIds.includes("telephoto-5x"), id);
  }
  const unknown = devices.flatMap((device) => device.lenses).filter((lens) => !lensEquivalent(lens));
  assert.ok(unknown.length > 0);
  for (const lens of unknown) {
    assert.equal(makeOptics({ mode: "equivalent", equivalentMm: lensEquivalent(lens), aperture: lens.aperture }), null);
    assert.ok(lens.noteEn, lens.id);
  }
});

test("brand-prefixed Huawei and Honor codes match only when the catalog gives a unique optical model", () => {
  // These actual codes are shared by variants with different optical paths.
  for (const code of ["SGT-AL10", "HUAWEI SGT-AL10", "ALN-AL10", "HONOR MEP-AN00"]) {
    assert.equal(matchDevice(devices, { model: code }), null, code);
  }
  for (const [brand, code] of [["HUAWEI", "NEW-AL00"], ["HONOR", "NEW-AN00"]]) {
    const fixtures = [{ id: brand, name: brand + " Example", modelPatterns: ["^" + code + "$"], lenses: [] }];
    assert.equal(matchDevice(fixtures, { model: brand + " " + code })?.id, brand);
    assert.equal(matchDevice([...fixtures, { ...fixtures[0], id: brand + " Other" }], { model: code }), null);
  }
});
