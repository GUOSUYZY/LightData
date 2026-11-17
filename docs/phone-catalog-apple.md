# Apple 镜头资料片段

核对日期：2026-11-17。数据位于 `tools/phone_catalog/apple.json`，供主镜头库构建程序合并。保留原有 10 个 Apple 光学组的 ID、别名和镜头参数，新增 6 组，共 16 个光学组、23 个实体机型。2022 年以后的机型有 22 款；原有 iPhone 13 Pro 标记 `legacy: true`，保留历史配置兼容。

纳入标准是中国大陆首发时至少一种存储配置的含税零售价高于 RMB 4,000。`msrpCny` 表示满足门槛的最低首发配置价格；`priceVariants` 区分同光学组中的各机型及其入门容量，SE3 同时列出三种容量以解释其入选原因。价格用于说明资料范围，不表示当前售价、补贴后售价或折抵换购价格。

## 机型与首发价格

| 光学组 ID | 年份 | 机型 / 首发配置 / RMB | 光学参数来源 | 首发价格来源 |
| --- | --- | --- | --- | --- |
| `apple-iphone-13-pro` | 2021，历史保留 | 13 Pro 128GB / 7,999 | [技术规格](https://support.apple.com/en-us/111871) | [Apple 发布稿](https://www.apple.com.cn/newsroom/2021/09/apple-unveils-iphone-13-pro-and-iphone-13-pro-max-more-pro-than-ever-before/) |
| `apple-iphone-14` | 2022 | 14 128GB / 5,999；14 Plus 128GB / 6,999 | [14 规格](https://support.apple.com/en-us/111850)、[Plus 规格](https://support.apple.com/en-us/111854) | [Apple 发布稿](https://www.apple.com.cn/newsroom/2022/09/apple-introduces-iphone-14-and-iphone-14-plus/) |
| `apple-iphone-14-pro` | 2022 | 14 Pro 128GB / 7,999；Pro Max 128GB / 8,999 | [Pro 规格](https://support.apple.com/en-us/111849)、[Max 规格](https://support.apple.com/en-us/111846) | [Apple 发布稿](https://www.apple.com.cn/newsroom/2022/09/apple-debuts-iphone-14-pro-and-iphone-14-pro-max/) |
| `apple-iphone-15` | 2023 | 15 128GB / 5,999；15 Plus 128GB / 6,999 | [15 规格](https://support.apple.com/en-us/111831)、[Plus 规格](https://support.apple.com/en-us/111830) | [Apple 发布稿](https://www.apple.com.cn/newsroom/2023/09/apple-debuts-iphone-15-and-iphone-15-plus/) |
| `apple-iphone-15-pro` | 2023 | 15 Pro 128GB / 7,999 | [Pro 规格](https://support.apple.com/en-us/111829) | [Apple 发布稿](https://www.apple.com.cn/newsroom/2023/09/apple-unveils-iphone-15-pro-and-iphone-15-pro-max/) |
| `apple-iphone-15-pro-max` | 2023 | 15 Pro Max 256GB / 9,999 | [Max 规格](https://support.apple.com/en-us/111828) | [Apple 发布稿](https://www.apple.com.cn/newsroom/2023/09/apple-unveils-iphone-15-pro-and-iphone-15-pro-max/) |
| `apple-iphone-16` | 2024 | 16 128GB / 5,999；16 Plus 128GB / 6,999 | [16 规格](https://support.apple.com/en-us/121029) | [Apple 发布稿](https://www.apple.com.cn/newsroom/2024/09/apple-introduces-iphone-16-iphone-16-plus/) |
| `apple-iphone-16-pro` | 2024 | 16 Pro 128GB / 7,999；Pro Max 256GB / 9,999 | [Pro 规格](https://support.apple.com/en-us/121031)、[Max 规格](https://support.apple.com/en-us/121032) | [Apple 发布稿](https://www.apple.com.cn/newsroom/2024/09/apple-debuts-iphone-16-pro-iphone-16-pro-max/) |
| `apple-iphone-17` | 2025 | 17 256GB / 5,999 | [技术规格](https://support.apple.com/en-us/125089) | [Apple 发布稿](https://www.apple.com.cn/newsroom/2025/09/apple-debuts-iphone-17/) |
| `apple-iphone-17-pro` | 2025 | 17 Pro 256GB / 8,999；Pro Max 256GB / 9,999 | [技术规格](https://support.apple.com/en-us/125090) | [Apple 发布稿](https://www.apple.com.cn/newsroom/2025/09/apple-unveils-iphone-17-pro-and-iphone-17-pro-max-cn/) |
| `apple-iphone-se3` | 2022 | SE 第三代 64GB / 3,499；128GB / 3,899；**256GB / 4,699** | [Apple 技术规格](https://support.apple.com/en-us/111866) | [新浪科技发布当日报道](https://finance.sina.cn/tech/2022-03-09/detail-imcwipih7391781.d.html?from=wap&fromtech=1)，二级价格来源，见下文 |
| `apple-iphone-16e` | 2025 | 16e 128GB / 4,499 | [技术规格](https://support.apple.com/en-us/122208) | [Apple 发布稿](https://www.apple.com.cn/newsroom/2025/02/apple-debuts-iphone-16e/) |
| `apple-iphone-air` | 2025 | Air 256GB / 7,999 | [技术规格](https://www.apple.com.cn/iphone-air/specs/) | [中国大陆发售公告](https://www.apple.com.cn/newsroom/2025/10/iphone-air-will-be-available-in-china-beginning-october-22/) |
| `apple-iphone-17e` | 2026 | 17e 256GB / 4,499 | [技术规格](https://www.apple.com.cn/iphone-17e/specs/) | [Apple 发布稿](https://www.apple.com.cn/newsroom/2026/03/apple-introduces-iphone-17e/) |
| `apple-iphone-18-pro` | 2026 | 18 Pro 256GB / 9,999；Pro Max 256GB / 10,999 | [技术规格](https://www.apple.com.cn/iphone-18-pro/specs/) | [Apple 发布稿](https://www.apple.com.cn/newsroom/2026/09/apple-debuts-iphone-18-pro-and-iphone-18-pro-max/) |
| `apple-iphone-duo` | 2026 | Duo 256GB / 15,999 | [技术规格](https://www.apple.com.cn/iphone-duo/specs/) | [Apple 发布稿](https://www.apple.com.cn/newsroom/2026/09/apple-unveils-iphone-duo/) |

SE3 的 [Apple 发布稿](https://www.apple.com.cn/newsroom/2022/03/apple-announces-the-new-iphone-se-a-powerful-smartphone-in-an-iconic-design/)确认三种容量和起售价 RMB 3,499，没有逐容量价格。256GB 的 RMB 4,699 来自新浪科技 2022-03-09 发布会当日报道，已在 JSON 的 `priceNote` / `priceNoteEn` 中明确标为二级价格来源。该例外只用于价格，光学参数仍只采用 Apple 官方资料。

## 光学参数与使用限制

- 现有 14、14 Pro、15、16、16 Pro、17 Pro 的 Plus / Max 别名继续共用原光学组；15 Pro 与 15 Pro Max 长焦不同，继续保留独立 ID。数据片段不改变用户已经保存的镜头 ID。
- 新增 16e、Air、17e 均只有一颗后置实体主摄，使用厂家公布的 26mm 等效焦距与 f/1.6。52mm 的 2× 模式来自主摄裁切，不增建一颗实体长焦镜头。
- 18 Pro / Max 的主摄为 24mm，厂家列出 f/1.48、f/1.8、f/2.8、f/4.0 四档光圈；默认 f/1.48，额外记录 `variableAperture`、`apertureOptions`、`apertureRange` 与中英文说明。超广角为 13mm f/2.2，实体长焦为 100mm f/2.8。200mm 的 8× 模式不当作另一颗实体镜头。
- Duo 的后置镜头为 26mm f/1.6 主摄与 13mm f/2.2 超广角。52mm 的 2× 裁切不单独建档。
- SE3 官方资料确认 f/1.8，但本次找到的 Apple 页面没有给出可核实的等效焦距或水平视角，因此保留 `coverageUnknown: true`，不以第三方推算填充视角；设置中要求手动输入水平视角或等效焦距。
- 历史 13 Pro 的超广角资料只有 120°、没有角度方向，继续保留 `fovAxis: "unspecified"` 与手动设置说明，不将其直接作为水平视角使用。

这里的等效焦距是厂家静态相机规格。浏览器实际开放的镜头、预览裁切、视频防抖、变焦和可变光圈可能改变有效取景范围及测光参数。选取预设后仍需核对实际采集镜头、光圈及水平视角。新增型号只包含后置实体镜头；没有把传感器裁切倍率、软件变焦或前置相机合并为后置光学组件。

## 字段与核验

设备包含 `launchYear`、`msrpCny`、`priceSource`、`priceVariants` 和中英文价格说明；`source` 指向技术参数来源。每个镜头补充 `nameEn`，有使用限制时同时提供 `note` 和 `noteEn`，便于语言切换。首发价格优先采用对应年份的 Newsroom 发布稿，避免将 2026 年商店的调整后价格误写为首发价格。

本次执行了 JSON 解析、ID 唯一性、原有 ID / aliases / modelPatterns / 原镜头字段保留、正值光圈与已知等效焦距、至少一个配置符合价格门槛、价格与技术来源 URL 检查。资料核验属于厂家规格对照，没有连接 iPhone 逐机验证浏览器设备标签与实体镜头的对应关系。
