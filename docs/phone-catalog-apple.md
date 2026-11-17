# Apple iPhone X 及以后全部型号目录

核验日期：2026-11-17。数据位于 `tools/phone_catalog/apple.json`，由镜头库构建程序合并。本轮逐项对照并通过浏览器读取 [Apple 官方型号列表](https://support.apple.com/en-us/108044)，从 iPhone X（含）起共有 **38 个已正式发布的独立型号、86 枚原生后置实体镜头**。其中 83 枚有来源可追溯的等效焦距，3 枚明确要求手动设置。

## 范围、型号与兼容性

- 所有设备统一标记 `catalogScope: "iphone-x-onward"`。Apple 此范围没有年份或首发价格门槛；iPhone X 的年份是 2017。未找到已核验首发价的新增老机没有填入猜测价格。
- SE 第二代、第三代、12/13 mini、14/15/16 Plus，以及每代 Pro Max 各有独立设备项。另包括 16e、17e、Air、18 Pro、18 Pro Max 和 Duo。2026-11-17 的官方型号表没有已经发布的普通 iPhone 18 或 Air 第二代，不预建未发布型号。
- 没有纳入 iPhone 8 / 8 Plus：范围按用户指定的产品系列自 X 起，而非把 2017 年所有同期机型都纳入。SE 第一代早于 X，不在范围。
- 保留原有 16 个设备 ID 和各设备已有的镜头 ID。此前与常规版共享记录的 Plus / Max 别名已移动至自己的记录，避免精确匹配落到错误型号。
- `opticalGroupId` 及 `sharedOpticsWith` 说明哪些独立型号复用本目录中的后置焦距、光圈参数；`sharedOpticsSources` 分别列出型号对应的官方技术页。这不表示所有传感器、模组、视频裁切或处理流程都相同。
- 官方 A 型号代码来自型号列表，`modelPatterns` 只做完整代码匹配，并保留 `modelSources`。iOS 浏览器通常不会暴露这些型号代码，因此不能承诺只凭普通 User-Agent 自动识别具体 iPhone。

## 全部型号与原生镜头

下表格式为“35mm 等效焦距 / 光圈”；待设表示已知光圈但不使用未经核验的焦距。参数链接是对应型号的 Apple 技术规格；补充焦距链接是该镜头的独立来源。

| 独立设备 ID | 型号 / 年份 | 原生后置镜头（mm / f） | 官方规格 | 补充焦距资料 |
|---|---|---|---|---|
| `apple-iphone-x` | iPhone X / 2017 | 主摄 28 / f1.8；长焦 待设 / f2.4 | [Apple](https://support.apple.com/zh-cn/111864) | [DPReview](https://www.dpreview.com/reviews/4941827141/iphone-x-review/) |
| `apple-iphone-xr` | iPhone XR / 2018 | 主摄 26 / f1.8 | [Apple](https://support.apple.com/zh-cn/111868) | [DXOMARK](https://www.dxomark.com/apple-iphone-xr-camera-review/) |
| `apple-iphone-xs-max` | iPhone XS Max / 2018 | 主摄 26 / f1.8；长焦 52 / f2.4 | [Apple](https://support.apple.com/zh-cn/111880) | [DXOMARK](https://www.dxomark.com/apple-iphone-xs-max-review-flagship-imaging-power/) |
| `apple-iphone-xs` | iPhone XS / 2018 | 主摄 26 / f1.8；长焦 52 / f2.4 | [Apple](https://support.apple.com/zh-cn/111881) | [DXOMARK](https://www.dxomark.com/apple-iphone-xs-max-review-flagship-imaging-power/) |
| `apple-iphone-11` | iPhone 11 / 2019 | 主摄 26 / f1.8；超广角 13 / f2.4 | [Apple](https://support.apple.com/zh-cn/111865) | [DXOMARK](https://www.dxomark.com/apple-iphone-11-camera-review/) |
| `apple-iphone-11-pro-max` | iPhone 11 Pro Max / 2019 | 主摄 26 / f1.8；超广角 13 / f2.4；长焦 52 / f2 | [Apple](https://support.apple.com/zh-cn/111878) | [DXOMARK](https://www.dxomark.com/updated-apple-iphone-11-pro-max-camera-review-still-an-excellent-imaging-option/) |
| `apple-iphone-11-pro` | iPhone 11 Pro / 2019 | 主摄 26 / f1.8；超广角 13 / f2.4；长焦 52 / f2 | [Apple](https://support.apple.com/zh-cn/111879) | [DXOMARK](https://www.dxomark.com/updated-apple-iphone-11-pro-max-camera-review-still-an-excellent-imaging-option/) |
| `apple-iphone-se2` | iPhone SE (2nd generation) / 2020 | 主摄 待设 / f1.8 | [Apple](https://support.apple.com/zh-cn/111882) | 官方规格 / 原有 Apple 发布稿 |
| `apple-iphone-12-mini` | iPhone 12 mini / 2020 | 主摄 26 / f1.6；超广角 13 / f2.4 | [Apple](https://support.apple.com/zh-cn/111877) | [DXOMARK](https://www.dxomark.com/apple-iphone-12-mini-camera-review-performance-in-your-pocket/) |
| `apple-iphone-12` | iPhone 12 / 2020 | 主摄 26 / f1.6；超广角 13 / f2.4 | [Apple](https://support.apple.com/zh-cn/111876) | [DXOMARK](https://www.dxomark.com/apple-iphone-12-camera-review-pro-level-video-amateur-zoom/) |
| `apple-iphone-12-pro` | iPhone 12 Pro / 2020 | 主摄 26 / f1.6；超广角 13 / f2.4；长焦 52 / f2 | [Apple](https://support.apple.com/zh-cn/111875) | [DXOMARK](https://www.dxomark.com/apple-iphone-12-pro-camera-review-great-smartphone-video/) |
| `apple-iphone-12-pro-max` | iPhone 12 Pro Max / 2020 | 主摄 26 / f1.6；超广角 13 / f2.4；长焦 65 / f2.2 | [Apple](https://support.apple.com/zh-cn/111874) | [DXOMARK](https://www.dxomark.com/apple-iphone-12-pro-max-camera-review-big-and-beautiful/) |
| `apple-iphone-13-mini` | iPhone 13 mini / 2021 | 主摄 26 / f1.6；超广角 13 / f2.4 | [Apple](https://support.apple.com/zh-cn/111873) | [DXOMARK](https://www.dxomark.com/apple-iphone-13-mini-camera-review-powerful-mobile-imaging-in-pocket-format/) |
| `apple-iphone-13` | iPhone 13 / 2021 | 主摄 26 / f1.6；超广角 13 / f2.4 | [Apple](https://support.apple.com/zh-cn/111872) | [DXOMARK](https://www.dxomark.com/apple-iphone-13-mini-camera-review-powerful-mobile-imaging-in-pocket-format/) |
| `apple-iphone-13-pro` | iPhone 13 Pro / 2021 | 主摄 26 / f1.5；超广角 13 / f1.8；长焦 77 / f2.8 | [Apple](https://support.apple.com/en-us/111871) | [DXOMARK](https://www.dxomark.com/apple-iphone-13-pro-camera-test-retested/) |
| `apple-iphone-13-pro-max` | iPhone 13 Pro Max / 2021 | 主摄 26 / f1.5；超广角 13 / f1.8；长焦 77 / f2.8 | [Apple](https://support.apple.com/zh-cn/111870) | [DXOMARK](https://www.dxomark.com/apple-iphone-13-pro-max-camera-review-apple-flagship-imaging/) |
| `apple-iphone-se3` | iPhone SE (3rd generation) / 2022 | 主摄 待设 / f1.8 | [Apple](https://support.apple.com/en-us/111866) | 官方规格 / 原有 Apple 发布稿 |
| `apple-iphone-14` | iPhone 14 / 2022 | 主摄 26 / f1.5；超广角 13 / f2.4 | [Apple](https://support.apple.com/en-us/111850) | 官方规格 / 原有 Apple 发布稿 |
| `apple-iphone-14-plus` | iPhone 14 Plus / 2022 | 主摄 26 / f1.5；超广角 13 / f2.4 | [Apple](https://support.apple.com/zh-cn/111854) | 官方规格 / 原有 Apple 发布稿 |
| `apple-iphone-14-pro` | iPhone 14 Pro / 2022 | 主摄 24 / f1.78；超广角 13 / f2.2；长焦 77 / f2.8 | [Apple](https://support.apple.com/en-us/111849) | 官方规格 / 原有 Apple 发布稿 |
| `apple-iphone-14-pro-max` | iPhone 14 Pro Max / 2022 | 主摄 24 / f1.78；超广角 13 / f2.2；长焦 77 / f2.8 | [Apple](https://support.apple.com/zh-cn/111846) | 官方规格 / 原有 Apple 发布稿 |
| `apple-iphone-15` | iPhone 15 / 2023 | 主摄 26 / f1.6；超广角 13 / f2.4 | [Apple](https://support.apple.com/en-us/111831) | 官方规格 / 原有 Apple 发布稿 |
| `apple-iphone-15-plus` | iPhone 15 Plus / 2023 | 主摄 26 / f1.6；超广角 13 / f2.4 | [Apple](https://support.apple.com/zh-cn/111830) | 官方规格 / 原有 Apple 发布稿 |
| `apple-iphone-15-pro` | iPhone 15 Pro / 2023 | 主摄 24 / f1.78；超广角 13 / f2.2；长焦 77 / f2.8 | [Apple](https://support.apple.com/en-us/111829) | 官方规格 / 原有 Apple 发布稿 |
| `apple-iphone-15-pro-max` | iPhone 15 Pro Max / 2023 | 主摄 24 / f1.78；超广角 13 / f2.2；长焦 120 / f2.8 | [Apple](https://support.apple.com/en-us/111828) | 官方规格 / 原有 Apple 发布稿 |
| `apple-iphone-16` | iPhone 16 / 2024 | 主摄 26 / f1.6；超广角 13 / f2.2 | [Apple](https://support.apple.com/en-us/121029) | 官方规格 / 原有 Apple 发布稿 |
| `apple-iphone-16-plus` | iPhone 16 Plus / 2024 | 主摄 26 / f1.6；超广角 13 / f2.2 | [Apple](https://support.apple.com/zh-cn/121030) | 官方规格 / 原有 Apple 发布稿 |
| `apple-iphone-16-pro` | iPhone 16 Pro / 2024 | 主摄 24 / f1.78；超广角 13 / f2.2；长焦 120 / f2.8 | [Apple](https://support.apple.com/en-us/121031) | 官方规格 / 原有 Apple 发布稿 |
| `apple-iphone-16-pro-max` | iPhone 16 Pro Max / 2024 | 主摄 24 / f1.78；超广角 13 / f2.2；长焦 120 / f2.8 | [Apple](https://support.apple.com/zh-cn/121032) | 官方规格 / 原有 Apple 发布稿 |
| `apple-iphone-16e` | iPhone 16e / 2025 | 主摄 26 / f1.6 | [Apple](https://support.apple.com/en-us/122208) | 官方规格 / 原有 Apple 发布稿 |
| `apple-iphone-air` | iPhone Air / 2025 | 主摄 26 / f1.6 | [Apple](https://www.apple.com.cn/iphone-air/specs/) | 官方规格 / 原有 Apple 发布稿 |
| `apple-iphone-17` | iPhone 17 / 2025 | 主摄 26 / f1.6；超广角 13 / f2.2 | [Apple](https://support.apple.com/en-us/125089) | 官方规格 / 原有 Apple 发布稿 |
| `apple-iphone-17-pro` | iPhone 17 Pro / 2025 | 主摄 24 / f1.78；超广角 13 / f2.2；长焦 100 / f2.8 | [Apple](https://support.apple.com/en-us/125090) | 官方规格 / 原有 Apple 发布稿 |
| `apple-iphone-17-pro-max` | iPhone 17 Pro Max / 2025 | 主摄 24 / f1.78；超广角 13 / f2.2；长焦 100 / f2.8 | [Apple](https://support.apple.com/zh-cn/125091) | 官方规格 / 原有 Apple 发布稿 |
| `apple-iphone-17e` | iPhone 17e / 2026 | 主摄 26 / f1.6 | [Apple](https://www.apple.com.cn/iphone-17e/specs/) | 官方规格 / 原有 Apple 发布稿 |
| `apple-iphone-18-pro` | iPhone 18 Pro / 2026 | 主摄 24 / f1.48/1.8/2.8/4；超广角 13 / f2.2；长焦 100 / f2.8 | [Apple](https://www.apple.com.cn/iphone-18-pro/specs/) | 官方规格 / 原有 Apple 发布稿 |
| `apple-iphone-18-pro-max` | iPhone 18 Pro Max / 2026 | 主摄 24 / f1.48/1.8/2.8/4；超广角 13 / f2.2；长焦 100 / f2.8 | [Apple](https://support.apple.com/en-us/148591) | 官方规格 / 原有 Apple 发布稿 |
| `apple-iphone-duo` | iPhone Duo / 2026 | 主摄 26 / f1.6；超广角 13 / f2.2 | [Apple](https://www.apple.com.cn/iphone-duo/specs/) | 官方规格 / 原有 Apple 发布稿 |

## 焦距证据及缺项

**光圈和镜头数量优先使用对应型号的 Apple 技术规格。** 较早规格页只写光圈或 FOV 时，本轮按授权使用原作者实际相机评测中给出的等效焦距，分别写入镜头 `equivalentSource` 和 `equivalentEvidence: "original-review-specification"`。这类数值不能描述为 Apple 技术页直接公布的毫米数。

- **iPhone 13 Pro**：Apple 技术规格确认主摄 f/1.5，已补回缺失的 `wide` 镜头。26 mm 主摄与 13 mm 超广角由 [DXOMARK 对该机的原测评](https://www.dxomark.com/apple-iphone-13-pro-camera-test-retested/)补充；77 mm 长焦保留原有 Apple 发布稿依据。Pro Max 独立项还引用[对应型号的原测评](https://www.dxomark.com/apple-iphone-13-pro-max-camera-review-apple-flagship-imaging/)。
- **iPhone 12 系列**：原测评给出的名义值为主摄 26 mm、超广角 13 mm；12 Pro 是 52 mm f/2 长焦，12 Pro Max 是 65 mm f/2.2 长焦，没有套用同一记录。DXOMARK 在 [12](https://www.dxomark.com/apple-iphone-12-camera-review-pro-level-video-amateur-zoom/)、[12 mini](https://www.dxomark.com/apple-iphone-12-mini-camera-review-performance-in-your-pocket/)、[12 Pro](https://www.dxomark.com/apple-iphone-12-pro-camera-review-great-smartphone-video/)及 [12 Pro Max](https://www.dxomark.com/apple-iphone-12-pro-max-camera-review-big-and-beautiful/)原测评中指出超广角实测覆盖约等效 14 mm。目录默认保留名义 13 mm，另存 `reviewMeasuredEquivalentMm: 14` 和双语说明，实际视频覆盖仍需校正。
- **iPhone XS / XS Max**：两者分别引用自己的 Apple 技术页；26 / 52 mm 来自 [DXOMARK 的 XS Max 原测评](https://www.dxomark.com/apple-iphone-xs-max-review-flagship-imaging-power/)。该文明确说明 XS 与 XS Max 的相机硬件相同，同时明确实际测试对象是 Max，目录保留这一共享来源而不声称对 XS 也完成了实测。
- **iPhone XR** 的 26 mm 来自[该型号原测评](https://www.dxomark.com/apple-iphone-xr-camera-review/)。**11** 的 26 / 13 mm 来自[该型号原测评](https://www.dxomark.com/apple-iphone-11-camera-review/)。**11 Pro / Pro Max** 的 26 / 13 / 52 mm 使用 [Pro Max 原测评](https://www.dxomark.com/updated-apple-iphone-11-pro-max-camera-review-still-an-excellent-imaging-option/)作为共享的焦距来源，并分别保留 Apple 规格页。
- **13 / 13 mini** 的 26 / 13 mm 由 [13 mini 原测评](https://www.dxomark.com/apple-iphone-13-mini-camera-review-powerful-mobile-imaging-in-pocket-format/)补充，该文确认其与 13 的相机配置共享；每个型号仍保留自己的官方规格页。

以下三枚镜头明确标记 `coverageUnknown: true`，没有猜测视角：

1. **iPhone X 长焦**：Apple 只确认 f/2.4，没有等效毫米数。[DPReview 原测评](https://www.dpreview.com/reviews/4941827141/iphone-x-review/)列 56 mm，而 [GSMArena 原创实测对比](https://www.gsmarena.com/apple_iphone_x_vs_samsung_galaxy_s9_plus_shootout-review-1739.php)列 52 mm。目录记录两个候选值及对应来源，不自行裁决冲突，不将任一值用于默认取景覆盖。X 主摄使用 DPReview 原测评的 28 mm 名义值，并明确评测来源。
2. **iPhone SE 第二代**：Apple 规格确认一颗 f/1.8 主摄，本轮核验的规格及原测评没有足够的原生等效焦距证据。
3. **iPhone SE 第三代**：同样确认单颗 f/1.8 主摄；保留原来的未知焦距及手动输入要求，不因为外观或代际关系而复制另一款手机的视角。

## 实体镜头、裁切与可变光圈

- 只记录后置的广角主摄、超广角与独立长焦，不把 LiDAR、深度传感器、前摄或软件镜头算进 86 枚。
- 14 Pro 以后的 2× 主摄裁切不另建长焦。16e、17e 和 Air 各只有一颗后置实体主摄，其 2× 功能也不建立另一项。
- 17 Pro / Max 与 18 Pro / Max 的原生长焦是 100 mm；200 mm 的 8× “光学品质”模式不算另一颗实体相机。15 Pro 的 77 mm 与 15 Pro Max 的 120 mm 长焦分别保存。
- 18 Pro / Max 的主摄保留厂家公布的 f/1.48、f/1.8、f/2.8、f/4 四档物理光圈，默认最大 f/1.48，并带 `variableAperture`、`apertureOptions`、`apertureRange` 和双语说明。
- Duo 按官方资料保留 26 mm f/1.6 主摄与 13 mm f/2.2 超广角，52 mm 的 2× 功能来自主摄裁切。

厂家和测评的焦距主要用于静态相机规格。浏览器输出比例、电子防抖、数字变焦、视频裁切及可变光圈都可能改变实际覆盖与测光参数。预设不代表网页可以直接控制光圈；用户仍需核对当前 camera track、实际光圈和取景覆盖。

## 历史价格资料

本轮 Apple 入选不受价格限制。对已有机型保留经过前轮核验的中国大陆首发价格与链接，拆分后的 Plus / Max 只保留自己对应的 `priceVariants`。新增的范围外老机没有重新追查 MSRP，缺值保持为空，不用当前二手价格或促销价替代。

SE3 保留原有三种容量的历史记录：64GB / 3499、128GB / 3899、256GB / 4699 元。Apple [发布稿](https://www.apple.com.cn/newsroom/2022/03/apple-announces-the-new-iphone-se-a-powerful-smartphone-in-an-iconic-design/)确认起售价及容量；分容量报价来自[新浪科技发布当日报道](https://finance.sina.cn/tech/2022-03-09/detail-imcwipih7391781.d.html?from=wap&fromtech=1)，原有 `priceNote` 明确标为二级价格来源。`msrpCny: 4699` 是旧目录使用的 256GB 门槛配置价，不能读作此机的最低首发价；型号是否纳入当前 Apple 范围也不再取决于该字段。

| 已有历史价格资料的独立型号 | 记录的容量 / 首发 RMB | 来源 |
|---|---|---|
| iPhone 13 Pro | 128GB / 7999 | [Apple 发布稿](https://www.apple.com.cn/newsroom/2021/09/apple-unveils-iphone-13-pro-and-iphone-13-pro-max-more-pro-than-ever-before/) |
| iPhone SE (3rd generation) | 64GB / 3499；128GB / 3899；256GB / 4699 | [新浪科技（二级）](https://finance.sina.cn/tech/2022-03-09/detail-imcwipih7391781.d.html?from=wap&fromtech=1) |
| iPhone 14 | 128GB / 5999 | [Apple 发布稿](https://www.apple.com.cn/newsroom/2022/09/apple-introduces-iphone-14-and-iphone-14-plus/) |
| iPhone 14 Plus | 128GB / 6999 | [Apple 发布稿](https://www.apple.com.cn/newsroom/2022/09/apple-introduces-iphone-14-and-iphone-14-plus/) |
| iPhone 14 Pro | 128GB / 7999 | [Apple 发布稿](https://www.apple.com.cn/newsroom/2022/09/apple-debuts-iphone-14-pro-and-iphone-14-pro-max/) |
| iPhone 14 Pro Max | 128GB / 8999 | [Apple 发布稿](https://www.apple.com.cn/newsroom/2022/09/apple-debuts-iphone-14-pro-and-iphone-14-pro-max/) |
| iPhone 15 | 128GB / 5999 | [Apple 发布稿](https://www.apple.com.cn/newsroom/2023/09/apple-debuts-iphone-15-and-iphone-15-plus/) |
| iPhone 15 Plus | 128GB / 6999 | [Apple 发布稿](https://www.apple.com.cn/newsroom/2023/09/apple-debuts-iphone-15-and-iphone-15-plus/) |
| iPhone 15 Pro | 128GB / 7999 | [Apple 发布稿](https://www.apple.com.cn/newsroom/2023/09/apple-unveils-iphone-15-pro-and-iphone-15-pro-max/) |
| iPhone 15 Pro Max | 256GB / 9999 | [Apple 发布稿](https://www.apple.com.cn/newsroom/2023/09/apple-unveils-iphone-15-pro-and-iphone-15-pro-max/) |
| iPhone 16 | 128GB / 5999 | [Apple 发布稿](https://www.apple.com.cn/newsroom/2024/09/apple-introduces-iphone-16-iphone-16-plus/) |
| iPhone 16 Plus | 128GB / 6999 | [Apple 发布稿](https://www.apple.com.cn/newsroom/2024/09/apple-introduces-iphone-16-iphone-16-plus/) |
| iPhone 16 Pro | 128GB / 7999 | [Apple 发布稿](https://www.apple.com.cn/newsroom/2024/09/apple-debuts-iphone-16-pro-iphone-16-pro-max/) |
| iPhone 16 Pro Max | 256GB / 9999 | [Apple 发布稿](https://www.apple.com.cn/newsroom/2024/09/apple-debuts-iphone-16-pro-iphone-16-pro-max/) |
| iPhone 16e | 128GB / 4499 | [Apple 发布稿](https://www.apple.com.cn/newsroom/2025/02/apple-debuts-iphone-16e/) |
| iPhone Air | 256GB / 7999 | [Apple 发布稿](https://www.apple.com.cn/newsroom/2025/10/iphone-air-will-be-available-in-china-beginning-october-22/) |
| iPhone 17 | 256GB / 5999 | [Apple 发布稿](https://www.apple.com.cn/newsroom/2025/09/apple-debuts-iphone-17/) |
| iPhone 17 Pro | 256GB / 8999 | [Apple 发布稿](https://www.apple.com.cn/newsroom/2025/09/apple-unveils-iphone-17-pro-and-iphone-17-pro-max-cn/) |
| iPhone 17 Pro Max | 256GB / 9999 | [Apple 发布稿](https://www.apple.com.cn/newsroom/2025/09/apple-unveils-iphone-17-pro-and-iphone-17-pro-max-cn/) |
| iPhone 17e | 256GB / 4499 | [Apple 发布稿](https://www.apple.com.cn/newsroom/2026/03/apple-introduces-iphone-17e/) |
| iPhone 18 Pro | 256GB / 9999 | [Apple 发布稿](https://www.apple.com.cn/newsroom/2026/09/apple-debuts-iphone-18-pro-and-iphone-18-pro-max/) |
| iPhone 18 Pro Max | 256GB / 10999 | [Apple 发布稿](https://www.apple.com.cn/newsroom/2026/09/apple-debuts-iphone-18-pro-and-iphone-18-pro-max/) |
| iPhone Duo | 256GB / 15999 | [Apple 发布稿](https://www.apple.com.cn/newsroom/2026/09/apple-unveils-iphone-duo/) |

## 验证记录

本次用无头 Chrome 读取 Apple 官方型号目录，并对全部新增/拆分型号及 13 Pro 复核共 23 个官方技术页保存临时正文，逐项核对后摄数量与光圈。其他已存在的新型号官方资料沿用前轮已经核验的记录。补充焦距的 DXOMARK、DPReview 和 GSMArena 页面通过浏览工具核验，不把搜索摘要转写成厂家参数。

已执行片段级结构检查：

- 官方列表 X 至最新项与数据的型号集合完全一致：38 型号、86 后置实体镜头。
- 原有 16 个设备 ID 全部存在，原有镜头 ID 全部保留；补充 13 Pro `wide`。
- 每型号的镜头 ID 唯一；所有光圈为正数；83 个已知焦距为正数，另 3 个带双语手动设置说明。
- Plus / mini / Max 等独立别名归属唯一；SE2 / SE3 具有英文 `iPhone SE (2nd generation)` / `iPhone SE (3rd generation)` 别名。
- 型号年份和 A 代码与 Apple 官方列表对应；所有项目为 `catalogScope: "iphone-x-onward"`。
- 校验 12 Pro / Max 的 52 mm f/2 与 65 mm f/2.2、15 Pro / Max 的 77 / 120 mm 差异，避免跨型号误复用。

未在这 38 台实体手机上逐台验证 Safari 的 camera track 标签、可访问镜头和视频裁切；本轮属于带来源的规格整理，不能替代实机取景标定或绝对测光校准。集成构建、完整回归与发布由主会话执行。
