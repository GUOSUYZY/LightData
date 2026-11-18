# Xiaomi 手机镜头目录核验记录

核验日期：2026-11-17。数据片段位于 `tools/phone_catalog/xiaomi.json`；由 `tools/build_camera_catalog.py` 合并到网页目录。本片段不直接改写 `web/data/cameras.json`。

## 范围与取值规则

本次提供 28 个已发布的 Xiaomi 主品牌机型组，均在 2022 年及以后于中国大陆上市，且有至少一个配置的首发官方零售价严格大于 4000 元。共记录 87 枚后置实体镜头，其中 85 枚可建立有已知光圈的快捷配置，另有 2 枚长焦微距因厂家未标光圈而单独保留。此为有来源的精选目录，不声称覆盖所有限量配色、特别版或区域版本。

- 镜头光圈、原生等效焦距仅来自小米官网规格页。价格是入选范围的证据，不用媒体报道的镜头数据填充光学参数。
- `msrpCny` 是本片段已确认且符合门槛的最低首发配置价。12S、12 Pro 天玑版、13、14 的基础版均为 3999 元，本片段分别以已确认的高配入选，并保留 `baseLaunchMsrpCny`。
- 不用国补、首销优惠或当前涨价后的价格代替首发 MSRP。17T Pro 12+256GB 的 MSRP 为 4299 元，3999 元是发布时的暑促价；17 Max 的 4799 元 MSRP 与 4299 元国补价分别记录。
- 价格优先使用厂家投资者材料。其余价格明确标为发布时的二级媒体证据；17 Max 使用上市次日 PChome 的明确报价（新浪转载）。不将二级价格来源描述为厂家原文。
- 所列毫米数均为 35mm 等效焦距。没有用“2 倍”“5 倍”等倍率反推一个伪精确的厂家焦距，也未把人像模式、传感器裁切、数码变焦或前摄列为额外的后置实体镜头。
- 厂家仅给 FOV 且未给方向时保留 `manufacturerFov` 和 `fovAxis: "unspecified"`。它不会直接作为水平视角使用。
- 新增机型没有已核实的系统硬件型号时，`modelPatterns` 留空，不编造自动识别代码。可手动选择机型与实际相机 track。

## 机型、首发配置与来源

光学参数的写法为“等效 mm / f 值”；“待设”表示光圈已知但原生等效焦距没有足够的厂家证据，需要用户输入。表中价格链接仅支持价格与上市年份，光学列链接支持镜头参数。

| 机型 / 中国首发年 | 符合门槛的首发配置与 MSRP | 后置实体镜头快捷配置 | 参数来源 | 价格来源 |
|---|---|---|---|---|
| Xiaomi 12S / 2022 | 8GB+256GB · ¥4299 | 主摄 24/f1.9；超广角 15/f2.4 | [小米规格](https://www.mi.com/mi12s/specs) | [IT之家](https://www.ithome.com/0/627/847.htm) |
| Xiaomi 12S Pro / 2022 | 8GB+128GB · ¥4699 | 主摄 24/f1.9；超广角 待设/f2.2；长焦 50/f1.9 | [小米规格](https://www.mi.com/mi12s-pro/specs) | [IT之家](https://www.ithome.com/0/627/847.htm) |
| Xiaomi 12S Ultra / 2022 | 8GB+256GB · ¥5999 | 主摄 23/f1.9；超广角 13/f2.2；潜望长焦 120/f4.1 | [小米规格](https://www.mi.com/mi12s-ultra/specs) | [IT之家](https://www.ithome.com/0/627/849.htm) |
| Xiaomi 12 Pro Dimensity / 2022 | 12GB+256GB · ¥4499 | 主摄 24/f1.9；超广角 15/f2.4 | [小米规格](https://www.mi.com/mi12pro-dimensity/specs) | [IT之家](https://www.ithome.com/0/627/852.htm) |
| Xiaomi 13 / 2022 | 8GB+256GB · ¥4299 | 主摄 23/f1.8；超广角 15/f2.2；长焦 75/f2 | [小米规格](https://www.mi.com/xiaomi-13/specs) | [IT之家](https://www.ithome.com/0/660/339.htm) |
| Xiaomi 13 Pro / 2022 | 8GB+128GB · ¥4999 | 主摄 23/f1.9；超广角 14/f2.2；长焦 75/f2 | [小米规格](https://www.mi.com/xiaomi-13-pro/specs) | [IT之家](https://www.ithome.com/0/660/343.htm) |
| Xiaomi 13 Ultra / 2023 | 12GB+256GB · ¥5999 | 主摄 23/f1.9–4；超广角 12/f1.8；长焦 75/f1.8；潜望长焦 120/f3 | [小米规格](https://www.mi.com/xiaomi-13-ultra/specs) | [IT之家](https://www.ithome.com/0/687/322.htm) |
| Xiaomi 14 / 2023 | 12GB+256GB · ¥4299 | 主摄 23/f1.6；超广角 14/f2.2；长焦 75/f2 | [小米规格](https://www.mi.com/xiaomi-14/specs) | [IT之家](https://www.ithome.com/0/727/908.htm) |
| Xiaomi 14 Pro / 2023 | 12GB+256GB · ¥4999 | 主摄 23/f1.42–4；超广角 14/f2.2；长焦 75/f2 | [小米规格](https://www.mi.com/xiaomi-14-pro/specs) | [IT之家](https://www.ithome.com/0/727/914.htm) |
| Xiaomi 14 Ultra / 2024 | 12GB+256GB · ¥6499 | 主摄 23/f1.63–4；超广角 12/f1.8；长焦 75/f1.8；潜望长焦 120/f2.5 | [小米规格](https://www.mi.com/prod/xiaomi-14-ultra/specs) | [IT之家](https://www.ithome.com/0/751/479.htm) |
| Xiaomi 15 / 2024 | 12GB+256GB · ¥4499 | 主摄 23/f1.62；超广角 14/f2.2；长焦 60/f2 | [小米规格](https://www.mi.com/prod/xiaomi-15/specs) | [IT之家](https://www.ithome.com/0/806/219.htm) |
| Xiaomi 15 Pro / 2024 | 12GB+256GB · ¥5299 | 主摄 23/f1.44；超广角 14/f2.2；潜望长焦 120/f2.5 | [小米规格](https://www.mi.com/prod/xiaomi-15-pro/specs) | [IT之家](https://www.ithome.com/0/806/313.htm) |
| Xiaomi 15S Pro / 2025 | 首发起售价 · ¥5499 | 主摄 23/f1.44；超广角 14/f2.2；潜望长焦 120/f2.5 | [小米规格](https://www.mi.com/prod/xiaomi-15s-pro/specs) | [厂家 IR](https://ir.mi.com/system/files-encrypted/nasdaq_kms/assets/2025/05/27/6-36-49/Xiaomi%20Corp_25Q1_ER_ENG%20vF.pdf) |
| Xiaomi 15 Ultra / 2025 | 12GB+256GB · ¥6499 | 主摄 23/f1.63；超广角 14/f2.2；长焦 70/f1.8；潜望长焦 100/f2.6 | [小米规格](https://www.mi.com/global/product/xiaomi-15-ultra/specs/) | [IT之家](https://www.ithome.com/0/834/153.htm) |
| Xiaomi 17 / 2025 | 12GB+256GB · ¥4499 | 主摄 23/f1.67；超广角 17/f2.4；长焦 60/f2 | [小米规格](https://www.mi.com/prod/xiaomi-17/specs) | [IT之家](https://www.ithome.com/0/885/831.htm) |
| Xiaomi 17 Pro / 2025 | 12GB+256GB · ¥4999 | 主摄 23/f1.67；超广角 17/f2.4；潜望长焦 待设/f3 | [小米规格](https://www.mi.com/prod/xiaomi-17-pro/specs) | [IT之家](https://www.ithome.com/0/885/833.htm) |
| Xiaomi 17 Pro Max / 2025 | 12GB+512GB · ¥5999 | 主摄 23/f1.67；超广角 17/f2.4；潜望长焦 待设/f2.6 | [小米规格](https://www.mi.com/prod/xiaomi-17-pro-max/specs) | [IT之家](https://www.ithome.com/0/885/835.htm) |
| Xiaomi 17 Ultra / 2025 | 首发起售价 · ¥6999 | 主摄 23/f1.67；超广角 14/f2.2；长焦 75–100/f2.39–2.96 | [小米规格](https://www.mi.com/prod/xiaomi-17-ultra/specs) | [厂家 IR](https://ir.mi.com/system/files-encrypted/nasdaq_kms/assets/2026/03/24/6-22-11/Xiaomi%20Corp_25Q4_ER_CHN%20vF.pdf) |
| Xiaomi 17 Max / 2026 | 12GB+256GB · ¥4799 | 主摄 23/f1.65；超广角 17/f2.4；潜望长焦 待设/f2.4 | [小米规格](https://www.mi.com/prod/xiaomi-17-max/specs) | [PChome/新浪](https://finance.sina.com.cn/tech/roll/2026-05-22/doc-inhytuss5377003.shtml) |
| Xiaomi 17T Pro / 2026 | 12GB+256GB · ¥4299 | 主摄 23/f1.67；超广角 待设/f2.2；潜望长焦 115/f3 | [小米规格](https://www.mi.com/prod/xiaomi-17t-pro/specs) | [IT之家](https://m.ithome.com/html/961385.htm) |
| Xiaomi 18 Pro / 2026 | 12GB+256GB · ¥5999 | 主摄 23/f1.67；超广角 17/f2.4；潜望长焦 待设/f2.4 | [小米规格](https://www.mi.com/prod/xiaomi-18-pro/specs) | [IT之家](https://www.ithome.com/0/100/6426.htm) |
| Xiaomi 18 Pro Max / 2026 | 12GB+256GB · ¥6999 | 主摄 23/f1.67；超广角 17/f2.4；潜望长焦 待设/f2.4 | [小米规格](https://www.mi.com/prod/xiaomi-18-pro-max/specs) | [IT之家](https://www.ithome.com/1/006/425.htm) |
| Xiaomi 18 Fold / 2026 | 12GB+256GB · ¥10999 | 主摄 23/f1.7；超广角 待设/f2.4；潜望长焦 待设/f2.8 | [小米规格](https://www.mi.com/prod/xiaomi-18-fold/specs) | [IT之家](https://www.ithome.com/0/999/441.htm) |
| Xiaomi MIX Fold 2 / 2022 | 12GB+256GB · ¥8999 | 主摄 待设/f1.8；超广角 15/f2.4；长焦 45/f2.6 | [小米规格](https://www.mi.com/xiaomimixfold2/specs) | [IT之家](https://www.ithome.com/0/634/609.htm) |
| Xiaomi MIX Fold 3 / 2023 | 12GB+256GB · ¥8999 | 主摄 23/f1.77；超广角 15/f2.2；长焦 75/f2；潜望长焦 115/f2.92 | [小米规格](https://www.mi.com/xiaomi-mix-fold-3/specs) | [IT之家](https://www.ithome.com/0/712/328.htm) |
| Xiaomi MIX Fold 4 / 2024 | 12GB+256GB · ¥8999 | 主摄 23/f1.7；超广角 15/f2.2；长焦 47/f2；潜望长焦 115/f2.9 | [小米规格](https://www.mi.com/prod/xiaomi-mix-fold-4/specs) | [IT之家](https://www.ithome.com/0/783/027.htm) |
| Xiaomi MIX Flip / 2024 | 12GB+256GB · ¥5999 | 主摄 23/f1.7；长焦 47/f2 | [小米规格](https://www.mi.com/global/product/xiaomi-mix-flip/specs/) | [IT之家](https://www.ithome.com/0/783/030.htm) |
| Xiaomi MIX Flip 2 / 2025 | 12GB+256GB · ¥5999 | 主摄 23/f1.7；超广角 14/f2.2 | [小米规格](https://www.mi.com/prod/xiaomi-mix-flip2/specs) | [IT之家](https://www.ithome.com/0/863/940.htm) |

## 需要手动设置的内容

- **小米 12S、12 Pro 天玑版长焦微距**：官网有等效 50 mm 及微距距离，没有光圈值。记录在设备的 `unconfiguredLenses`，没有猜测为 f/2.4 或创建不可靠的测光按钮；用户应从实际硬件资料确认光圈后手动配置。
- **12S Pro 超广角**：官网确认 f/2.2、115° FOV，但未注明原生等效焦距与 FOV 方向。
- **17 Pro、17 Pro Max、17 Max、18 Pro、18 Pro Max 的长焦**：官网给光圈与光学倍率，没有本次可直接核验的原生等效毫米数。保留倍率作资料，不用主摄等效毫米数乘倍率替代厂家焦距。
- **17T Pro 超广角、18 Fold 超广角及长焦、MIX Fold 2 主摄**：官网的本次规格正文没有足够的原生等效焦距证据，标为 `coverageUnknown: true`。18 Fold 的“7 倍光学品质”不会另建实体镜头。
- **Xiaomi 17 超广角**：中国规格页的 f/2.4、102° 与全球官网规格的 17 mm 一同记录；`equivalentSource` 直接指向 [小米全球规格](https://www.mi.com/global/product/xiaomi-17/specs/)。
- **Xiaomi 17 Ultra 长焦**：等效 75–100 mm 由[小米全球规格](https://www.mi.com/global/product/xiaomi-17-ultra/specs/)确认；中国规格给 f/2.39–f/2.96。它是同一枚实体光学变焦镜头，只建一个配置，默认 75 mm / f2.39，另存两端范围。用户需要按实际变焦位置调整焦距和光圈，不能当成两枚固定镜头。
- **可变光圈**：13 Ultra 的 f/1.9、f/4.0 为两档物理光圈；14 Pro 为 f/1.42–f/4.0，14 Ultra 为 f/1.63–f/4.0。默认存最大光圈，保留可变范围与中英提示，实际测光须输入当前档位。
- **MIX Flip 与 MIX Flip 2**：前者是 23 mm 主摄加 47 mm 长焦，没有超广角；后者是 23 mm 主摄加 14 mm 超广角，没有独立长焦。未混用两代相机配置。

## 范围排除与兼容性

- 12 / 12 Pro 原版在 2021 年发布，不因当前仍可购买而计入 2022 年后的新机范围。2022 年发布的 12 Pro 天玑版单独记录。
- 17T 标准版最高已确认首发配置为 3799 元，未满足严格大于 4000 元门槛。17T Pro 满足门槛，单独入选。
- 本次检查到的 18 标准版地址是没有有效规格正文的页面，未找到截至核验日的正式上市证据，未据占位页面编入。18 Pro、18 Pro Max、18 Fold 已有正式规格与发布价格来源。
- REDMI、POCO 及境外独占的 T 系列不在此主品牌中国大陆目录范围中；不以改名或近似硬件套用光学值。
- 原有 `xiaomi-14`、`xiaomi-14-ultra`、`xiaomi-15`、`xiaomi-15-ultra` 的 ID、全部旧字段值、硬件型号正则、别名、源链接和镜头参数原样保留，只补充有来源的元数据与英文提示，确保本地已保存的配置仍能找到。

## 验证与限制

已执行结构验证：28 个唯一机型 ID；85 个唯一设备内镜头 ID；全部可配置镜头光圈为正数；全部已知焦距为正数，未知焦距明确标记；28 组均有中国首发年份、严格大于 4000 的 MSRP 和可定位的价格链接；4 组原有配置逐字段兼容检查通过。

没有在这 28 台实体手机上验证浏览器暴露了哪些 camera track，也没有据此承诺网页可直接控制光圈。网站给出的等效焦距不包含当前视频裁切、电子防抖或浏览器输出裁切，因此实际取景覆盖仍需通过网页的镜头配置校正。全部资料是可追踪的厂家规格加有标记的价格证据，不代表完成每台设备的实机光学标定。
