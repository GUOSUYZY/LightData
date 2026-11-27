# Frozen-Frame Extrema Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 固定画面时自动显示当前有效测量帧的最暗／最亮位置，不改变曝光和手动点测光。

**Architecture:** 新帧范围算法对与现有 `spot` 相同的 5×5 有效像素支持计算 EV，并把归一化结果交给 `MainActivity`。Compose 只绘制冻结状态的自动标记；它们不进入 `spots` 或安全主体状态。

**Tech Stack:** Kotlin、Android Compose Canvas、JUnit 4、Android instrumented tests。

**Spec:** `docs/superpowers/specs/2026-11-27-frozen-frame-extrema-design.md`

## Global Constraints

- 不合并连续／单次／冻结；不改变退出 HOLD 才应用建议的语义。
- 不选主体、不修改曝光、不引入百分比评分、分位数替代极值、全屏警告或曲线平移。
- 保留 `studio.lightdata.guosuyzy`、原签名和历经资料；试用版使用递增版本。
- 独立分支 `ui/frozen-extrema`；不直接改动或合并 `main`。

## Review Focus

- 透明／缺失视场可能产生虚假黑点：Task 1 的 `missingPixelsNeverCreateExtrema` 验证排除并共用旧 `spot` 规则。
- 均一灰、窄图及相同亮度可能缺少有效点或出现跳点：Task 1 的 `uniformAndSmallFramesReturnStableCenters` 验证并列和边界。
- 旋转、裁切与 Canvas 坐标不同步：Task 1 的 `liveCropExtremaKeepFrameCoordinates` 及 Task 2 的可访问标记位置验证一致映射。
- 自动点可能绕过用户主体选择：Task 2 的仪器测试验证 `spots` 为空、`safetyPointId` 为空而且曝光值不变。
- 旧标记可能污染新画面：Task 2 的冻结进出、相机失效和镜头失效测试验证清除。

---

### Task 1: 帧内极值计算

**Files:**
- Create: `app/src/main/java/studio/lightdata/FrameExtremes.kt`
- Create: `app/src/test/java/studio/lightdata/FrameExtremesTest.kt`
- Modify: `app/src/test/java/studio/lightdata/LiveCaptureTest.kt`

**Interfaces:**
- Produces: `ExtremalSpot(x: Double, y: Double, ev: Double, cameraLimited: Boolean)`。
- Produces: `FrameExtremes(darkest: ExtremalSpot, brightest: ExtremalSpot)`。
- Produces: `Frame.extremes(response: DoubleArray): FrameExtremes?`；无有效候选时返回 `null`。

- [ ] **Step 1: 写失败的计算测试**
  - 在反差测试帧上验证最暗／最亮的坐标和 EV 与相同位置的 `spot` 一致。
  - 验证完全没有有效像素的帧为 `null`，透明黑白像素不会影响有效区域结果。
  - 验证灰场、1～4 像素尺寸、边界点、相同亮度候选。
  - 验证旋转／裁切后的 `LiveCapture.fullFrame().extremes(response)` 与该帧直接计算一致。

- [ ] **Step 2: 验证测试先失败**

Run: `.\\gradlew.bat :app:testDebugUnitTest --tests studio.lightdata.FrameExtremesTest`
Expected: FAIL，找不到 `extremes`／极值类型。

- [ ] **Step 3: 实现计算**

在 `FrameExtremes.kt` 用积分累加图计算与 `Frame.spot` 相同的局部支持平均值，避免在每个候选上重复 25 次采样。严格小于／大于才替换，保持行优先并列规则。通过 `cameraLimitedAt` 复用触限判断，不改 `spot`、`distribution` 或 `meter`。

- [ ] **Step 4: 验证计算与原有 JVM 回归**

Run: `.\\gradlew.bat :app:testDebugUnitTest`
Expected: 原有 72 项及新增测试全部通过。

- [ ] **Step 5: 提交**

```powershell
git add app/src/main/java/studio/lightdata/FrameExtremes.kt app/src/test/java/studio/lightdata/FrameExtremesTest.kt app/src/test/java/studio/lightdata/LiveCaptureTest.kt
git commit -m "Measure extrema in the frozen frame"
```

### Task 2: 冻结画面标记与生命周期

**Files:**
- Modify: `app/src/main/java/studio/lightdata/MainActivity.kt`
- Modify: `app/src/main/java/studio/lightdata/Interface.kt`
- Modify: `app/src/androidTest/java/studio/lightdata/PhotographerControlsTest.kt`

**Interfaces:**
- Consumes: Task 1 的 `Frame.extremes` 和 `FrameExtremes`。
- Produces: `MainActivity` 内部状态 `extremes: FrameExtremes?`；进入冻结时由当前 `Frame` 创建，退出／画面失效时为空。

- [ ] **Step 1: 写失败的仪器测试**

从真实操作进入冻结，验证自动标记可访问说明、有效极值、无手动点、无安全主体、曝光不变；再添加手动点、退出冻结并验证自动标记清除。保留并运行单次与冻结独立性的现有断言。

- [ ] **Step 2: 验证测试先失败**

Run: `.\\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=studio.lightdata.PhotographerControlsTest`
Expected: FAIL，找不到自动极值。

- [ ] **Step 3: 实现最小界面和状态改动**

只用被冻结的帧设置 `extremes`，在已有 Canvas 中绘制两个“最暗”／“最亮”环标并添加估计性可访问说明。自动标记与手动 `spots` 分开；所有手动点跨画面清除的位置也清除自动极值。不增加触控事件或曝光建议分支。

- [ ] **Step 4: 验证 UI 与 JVM 回归**

Run: `.\\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest`
Expected: 所有测试通过；不适用的既有用例只能按其原条件跳过，不计作通过。

- [ ] **Step 5: 模拟器实际检查**

在 API 35 的 360×800 dp、800×360 dp、360×640 dp 下检查冻结前后和手动点共存；截取最终 APK 的画面。验证退出冻结、旋转和视频帧变化后标记不残留在错误的画面。

- [ ] **Step 6: 提交**

```powershell
git add app/src/main/java/studio/lightdata/MainActivity.kt app/src/main/java/studio/lightdata/Interface.kt app/src/androidTest/java/studio/lightdata/PhotographerControlsTest.kt
git commit -m "Mark frame extrema while frozen"
```

### Task 3: 独立试用版

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `CHANGELOG.md`, `README.md`, `DEVICE-TEST.md`
- Modify: 本次 `docs/superpowers/specs` 与本计划（仅勾选实际完成的步骤）

**Interfaces:**
- Produces: 继承 beta2 的递增 Android 预发布 APK 和可复现的哈希、测试及截图记录。

- [ ] **Step 1: 填写版本与真实验证范围**

设定 `versionCode 11`、`versionName 0.4.0-beta3`，文档只写已获证据与尚存实机缺口，不覆盖 Tests 或历史截图。

- [ ] **Step 2: 全部构建与审查**

Run: `.\\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease :app:compileDebugAndroidTestKotlin`
Run: `git diff --check`
Expected: 全部成功，Lint 无新增错误。

- [ ] **Step 3: 试用分支 CI 与包检查**

推送独立分支并运行 GitHub CI；验证四 ABI、LUT 资产一致、Zip 对齐、APK v2、包名、版本与签名。只有 CI 通过后再创建预发布。

- [ ] **Step 4: 发布试用并指定下一轮验收**

创建 `0.4.0-beta3` 预发布，附 APK、SHA-256 和检查过的截图；远端重下核对哈希。记录实体设备仍待核验的镜头、触控、GPU 和胶片效果；不合并 `main`。
