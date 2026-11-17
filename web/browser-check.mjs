import { chromium, expect } from "@playwright/test";
import http from "node:http";
import fs from "node:fs/promises";
import path from "node:path";
import os from "node:os";
import { fileURLToPath } from "node:url";

const root = path.dirname(fileURLToPath(import.meta.url));
const mime = {
  ".html": "text/html",
  ".js": "application/javascript",
  ".css": "text/css",
  ".json": "application/json",
  ".png": "image/png",
};
const server = http.createServer(async (req, res) => {
  try {
    const pathname = decodeURIComponent(
      new URL(req.url, "http://localhost").pathname,
    );
    const file = path.resolve(
      root,
      "." + (pathname === "/" ? "/index.html" : pathname),
    );
    if (!file.startsWith(root + path.sep)) throw new Error();
    res.setHeader(
      "Content-Type",
      mime[path.extname(file)] || "application/octet-stream",
    );
    res.end(await fs.readFile(file));
  } catch {
    res.writeHead(404);
    res.end();
  }
});
await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
const base =
  process.env.LIGHTDATA_URL || `http://127.0.0.1:${server.address().port}`;
const executablePath =
  process.env.LIGHTDATA_CHROME ||
  (process.platform === "win32"
    ? "C:/Program Files/Google/Chrome/Application/chrome.exe"
    : undefined);
const browser = await chromium.launch({
  executablePath,
  headless: true,
  args: [
    "--use-fake-device-for-media-stream",
    "--use-fake-ui-for-media-stream",
  ],
});
const context = await browser.newContext({
  viewport: { width: 430, height: 932 },
  permissions: ["camera"],
  deviceScaleFactor: 1,
});
const page = await context.newPage();
const errors = [];
page.on("pageerror", (error) => errors.push(error.message));
const snapshots =
  process.env.LIGHTDATA_SHOTS || path.join(os.tmpdir(), "LightDataWebQA");
await fs.mkdir(snapshots, { recursive: true });
async function settings() {
  await page.locator("#settings-toggle").click();
  await expect(page.locator("#settings")).toBeVisible();
}
async function close() {
  await page.locator("[data-close=settings]").click();
}
async function notice() {
  if (await page.locator("#notice").isVisible())
    await page.locator("#notice-ok").click();
}
async function valueReading() {
  return page
    .locator("#meter-reading")
    .textContent()
    .then((v) => Number.parseFloat(v));
}
try {
  await page.addInitScript(() => {
    const original = MediaStreamTrack.prototype.getSettings;
    MediaStreamTrack.prototype.getSettings = function () {
      return { ...original.call(this), ...globalThis.testCameraSettings };
    };
  });
  await page.goto(base);
  await expect(page.locator("#film-select option")).toHaveCount(167);
  await expect(page.locator("#format option")).toHaveCount(17);
  await expect(page.locator(".toolbar a[href*=releases]")).toHaveAttribute(
    "href",
    /0\.1\.2-beta/,
  );
  await page.locator("#start-camera").click();
  await expect(page.locator("#live-state")).toContainText("LIVE");
  await expect(page.locator("#meter-reading")).toContainText("相对亮度");
  await settings();
  await page.locator("#capture-aperture").fill("2");
  await page.locator("#capture-aperture").dispatchEvent("change");
  await page.evaluate(() => {
    globalThis.testCameraSettings = {
      exposureMode: "continuous",
      exposureTime: 100,
      iso: 100,
    };
  });
  await page.waitForTimeout(600);
  await expect(page.locator("#meter-reading")).toContainText("相对亮度");
  await page.locator("#capture-mode").selectOption("reported");
  await close();
  await expect(page.locator("#meter-reading")).toContainText("EV100");
  const first = await valueReading();
  await page.evaluate(
    () => (globalThis.testCameraSettings.exposureTime = 1000),
  );
  await expect.poll(valueReading).toBeLessThan(first - 2.5);
  await page.locator("#single").click();
  const held = await page.locator("#exposure-table").textContent();
  await page.evaluate(() => (globalThis.testCameraSettings.exposureTime = 100));
  await page.waitForTimeout(850);
  expect(await page.locator("#exposure-table").textContent()).toBe(held);
  await page.locator("#continuous").click();
  await expect
    .poll(() => page.locator("#exposure-table").textContent())
    .not.toBe(held);
  await settings();
  await page
    .locator("#film-select")
    .selectOption({ label: "PRO 160S · ISO 160" });
  await expect(page.locator("#custom-iso")).toHaveValue("160");
  await expect(page.locator("#custom-iso")).toBeDisabled();
  await page.locator("#settings-iso-lock").click();
  await page.locator("#custom-iso").fill("3");
  await page.locator("#set-iso").click();
  await expect(page.locator("#pair-iso")).toContainText("ISO 3");
  await page
    .locator("#film-select")
    .selectOption({ label: "HP5 PLUS · ISO 400" });
  await expect(page.locator("#custom-iso")).toHaveValue("400");
  await page.locator("#camera-hfov").fill("70");
  await page.locator("#camera-hfov").dispatchEvent("change");
  await close();
  const apertureBefore = await page.evaluate(
    () => JSON.parse(localStorage.getItem("lightdata.web.v1")).manual.a,
  );
  await page.locator("#exposure-table button").first().click();
  expect(
    await page.evaluate(
      () => JSON.parse(localStorage.getItem("lightdata.web.v1")).manual.a,
    ),
  ).not.toBe(apertureBefore);
  await page.locator("#preview-toggle").click();
  await notice();
  await page.locator("#spots-toggle").click();
  await notice();
  const rect = await page.locator("#view").boundingBox();
  await page
    .locator("#view")
    .click({ position: { x: rect.width * 0.35, y: rect.height * 0.4 } });
  await expect(page.locator("#spots .spot")).toHaveCount(1);
  await page
    .locator("#view")
    .click({ position: { x: rect.width * 0.6, y: rect.height * 0.5 } });
  await expect(page.locator("#spots .spot")).toHaveCount(2);
  const colors = await page
    .locator("#spots .spot")
    .evaluateAll((nodes) => nodes.map((n) => n.style.color));
  expect(colors[0]).not.toBe(colors[1]);
  const axisBefore = await page.locator("#chart").getAttribute("aria-label");
  const chart = await page.locator("#chart").boundingBox();
  const cdp = await context.newCDPSession(page);
  const x = chart.x + chart.width * 0.5,
    y = chart.y + chart.height * 0.5;
  await cdp.send("Input.dispatchTouchEvent", {
    type: "touchStart",
    touchPoints: [{ x, y, id: 1 }],
  });
  await cdp.send("Input.dispatchTouchEvent", {
    type: "touchMove",
    touchPoints: [{ x: x + 30, y, id: 1 }],
  });
  await cdp.send("Input.dispatchTouchEvent", {
    type: "touchEnd",
    touchPoints: [],
  });
  expect(await page.locator("#chart").getAttribute("aria-label")).not.toBe(
    axisBefore,
  );
  const panAxis = await page.locator("#chart").getAttribute("aria-label");
  await cdp.send("Input.dispatchTouchEvent", {
    type: "touchStart",
    touchPoints: [
      { x: x - 20, y: y - 25, id: 1 },
      { x: x + 20, y: y + 25, id: 2 },
    ],
  });
  await cdp.send("Input.dispatchTouchEvent", {
    type: "touchMove",
    touchPoints: [
      { x: x - 35, y: y - 45, id: 1 },
      { x: x + 35, y: y + 45, id: 2 },
    ],
  });
  await cdp.send("Input.dispatchTouchEvent", {
    type: "touchEnd",
    touchPoints: [],
  });
  expect(await page.locator("#chart").getAttribute("aria-label")).not.toBe(
    panAxis,
  );
  await page.locator("#hist-reset").click();
  await page.screenshot({ path: path.join(snapshots, "portrait.png") });
  await page.locator("#spots-toggle").click();
  await page.locator("#focal").fill("8");
  await page.locator("#focal").dispatchEvent("change");
  await expect(page.locator("#scan-start")).toBeVisible();
  await page.locator("#scan-start").click();
  await expect(page.locator("#scan-panel")).toBeVisible();
  await expect(page.locator("#scan-status")).toContainText("覆盖");
  await page.locator("#scan-finish").click();
  await expect(page.locator("#scan-status")).toContainText("二次构图");
  await page.locator("#scan-cancel").click();
  await page.locator("#focal").fill("35");
  await page.locator("#focal").dispatchEvent("change");
  await settings();
  await page.locator("#custom-width").fill("5");
  await page.locator("#custom-height").fill("4");
  await page.locator("#format-unit").selectOption("inch");
  await page.locator("#set-format").click();
  await close();
  await page.locator("#format").selectOption("4");
  await page.locator("#format").selectOption("custom");
  expect(
    await page.evaluate(
      () => JSON.parse(localStorage.getItem("lightdata.web.v1")).format.w,
    ),
  ).toBe(127);
  await page.locator("#format").selectOption("4");
  await settings();
  await page.locator("summary").filter({ hasText: "自定义预设" }).click();
  await page.locator("#preset-name").fill("网页测试片");
  await page.locator("#preset-iso").fill("64");
  await page.locator("#preset-form button[type=submit]").click();
  await expect(page.locator("#custom-iso")).toHaveValue("64");
  const pendingDownload = page.waitForEvent("download");
  await page.locator("#presets-export").click();
  const file = await pendingDownload;
  const exported = JSON.parse(await fs.readFile(await file.path(), "utf8"));
  expect(exported.format).toBe("lightdata.custom-presets");
  expect(exported.presets[0].name).toBe("网页测试片");
  exported.presets[0].iso = 80;
  await page.locator("#presets-file").setInputFiles({
    name: "presets.json",
    mimeType: "application/json",
    buffer: Buffer.from(JSON.stringify(exported)),
  });
  await expect(page.locator("#custom-iso")).toHaveValue("80");
  await expect(page.locator("#format option[value=custom]")).toHaveCount(1);
  const before = await page.evaluate(
    () => JSON.parse(localStorage.getItem("lightdata.web.v1")).presets,
  );
  await page.locator("#presets-file").setInputFiles({
    name: "bad.json",
    mimeType: "application/json",
    buffer: Buffer.from("{invalid"),
  });
  expect(
    await page.evaluate(
      () => JSON.parse(localStorage.getItem("lightdata.web.v1")).presets,
    ),
  ).toBe(before);
  await page.locator("#log-counts").check();
  await close();
  await page.reload();
  await settings();
  await expect(page.locator("#log-counts")).toBeChecked();
  await expect(page.locator("#custom-iso")).toHaveValue("80");
  await close();
  await page.locator("#start-camera").click();
  await expect(page.locator("#live-state")).toContainText("LIVE");
  await expect(page.locator("#image-frame")).toBeVisible();
  await page.setViewportSize({ width: 1200, height: 1380 });
  await expect(page.locator("body")).toHaveClass(/side/);
  await page.screenshot({ path: path.join(snapshots, "fold-portrait.png") });
  await page.setViewportSize({ width: 1380, height: 1200 });
  await expect(page.locator("body")).toHaveClass(/landscape/);
  expect(
    (await page.locator("body").getAttribute("class")).includes("side"),
  ).toBe(false);
  await page.screenshot({ path: path.join(snapshots, "fold-landscape.png") });
  await page.setViewportSize({ width: 932, height: 430 });
  await expect(page.locator("body")).toHaveClass(/side/);
  await page.screenshot({ path: path.join(snapshots, "landscape.png") });
  await settings();
  await page.locator("#resize-layout").check();
  await close();
  const split = await page.locator("#split-main").boundingBox();
  await page.mouse.move(split.x + split.width / 2, split.y + split.height / 2);
  await page.mouse.down();
  await page.mouse.move(
    split.x + split.width / 2 + 30,
    split.y + split.height / 2,
  );
  await page.mouse.up();
  expect(
    await page.evaluate(
      () => JSON.parse(localStorage.getItem("lightdata.web.v1")).layouts.l,
    ),
  ).toBeTruthy();
  await settings();
  await page.evaluate(() => {
    globalThis.testCameraSettings = {
      exposureMode: "manual",
      exposureTime: 1000,
      iso: 100,
    };
  });
  await page.waitForTimeout(600);
  await page.locator("#capture-aperture").fill("2");
  await page.locator("#capture-iso").fill("100");
  await page.locator("#capture-time").fill("0.1");
  await page.locator("#capture-mode").selectOption("reference");
  await page.locator("#capture-confirmed").check();
  await page.locator("#reference-ev").fill("8");
  await page.locator("#set-reference").click();
  await expect.poll(valueReading).toBeGreaterThan(7.5);
  await expect.poll(valueReading).toBeLessThan(8.5);
  await page.evaluate(() => {
    globalThis.testCameraSettings.exposureTime = 2000;
  });
  await expect(page.locator("#meter-reading")).toContainText("相对亮度");
  await page.locator("#capture-mode").selectOption("reported");
  await close();
  await expect(page.locator("#meter-reading")).toContainText("EV100");
  const pausedReading = await valueReading();
  await page.evaluate(() => {
    Object.defineProperty(document, "hidden", {
      configurable: true,
      value: true,
    });
    document.dispatchEvent(new Event("visibilitychange"));
    globalThis.testCameraSettings.exposureTime = 100;
  });
  await page.waitForTimeout(600);
  expect(await valueReading()).toBe(pausedReading);
  await page.evaluate(() => {
    Object.defineProperty(document, "hidden", {
      configurable: true,
      value: false,
    });
    document.dispatchEvent(new Event("visibilitychange"));
  });
  await expect.poll(valueReading).toBeGreaterThan(pausedReading + 3);
  await settings();
  await page.locator("#camera-stop").click();
  await expect(page.locator("#live-state")).toContainText("OFF");
  await close();
  const denied = await browser.newContext({
    permissions: [],
    viewport: { width: 430, height: 932 },
  });
  await denied.addInitScript(() => {
    navigator.mediaDevices.getUserMedia = async () => {
      throw new DOMException("Denied", "NotAllowedError");
    };
  });
  const d = await denied.newPage();
  await d.goto(base);
  await d.locator("#start-camera").click();
  await expect(d.locator("#start-message")).toContainText("权限被拒绝");
  await denied.close();
  expect(errors).toEqual([]);
  console.log(
    "Browser checks passed: camera, changing AE, relative fallback, single reading, film ISO lock, 2 spots, touch pan/pinch, panorama/reframe, custom frame/JSON persistence, four layouts, split drag, reference expiry, background pause/resume, denied permission.",
  );
  console.log("Screenshots:", snapshots);
} catch (error) {
  await page.screenshot({ path: path.join(snapshots, "failure.png") });
  console.log("Page errors:", errors);
  throw error;
} finally {
  await browser.close();
  server.close();
}
