# 华为 / 荣耀手机原生后摄资料

更新日期：2026-11-17。数据文件：[huawei-honor.json](../tools/phone_catalog/huawei-honor.json)。

已核验录入 **112 个具体型号 / 325 条原生后摄光学路径**，其中华为 63 型 / 186 条，荣耀 49 型 / 139 条。时间覆盖 2022 年至本次更新日；这是可追溯的较大范围主表，不声称穷尽全部运营商、礼盒、艺术定制与后加配置。

## 口径

- 中国大陆发布，且首发官方配置至少有一档 **严格超过 4000 元**。msrpCny 取最低满足条件的首发配置价；例如荣耀 Magic4 3999 元起，8GB+256GB 首发 4499 元，因此记录 4499，并在 priceNote 保留配置及起售价。
- 价格使用当时厂商新闻、购买指引或可靠媒体对发布会 / 首销定价的报道。每机型保留 priceSource。未把国补、平台补贴、首销折扣、现价当作首发官方定价。华为现有活动页会更新价格，例如 Mate 80 Pro Max 风驰版现网页 9499 元，而 2026-03-27 首销报道明确 16GB+512GB 为 8499 元，因此使用后者。
- 光圈优先中国官网规格。旧中国页面失效时保存官方原页 source 及可访问的 archivedSource；部分已退市产品使用华为海外官网的同名光学规格。海外来源不能证明大陆售价，故价源另列。
- 只列可成像的原生主摄、超广角、长焦光学通路。不另造 2× / 5× 等传感器裁切“镜头”，也不把用于景深、色彩、红枫、超光谱测量的辅助传感器当作通用取景镜头。超广角的微距能力不另增一条镜头。Pura 80 Ultra 一镜双目的两条可切换原生光学路径分别列出并注明共用传感器。
- 可变光圈 aperture 填最大光圈，note/noteEn 记录范围；实际浏览器拍摄模式可能无法控制机械光圈。
- 具有方向证据的 FOV 才可填 horizontalFov / diagonalFov；本表没有将未注明方向的厂家角度写入这两项。厂商给出的 112° / 122° 仅记录 manufacturerFov、fovAxis: unspecified。
- 无等效焦距或方向明确的 FOV 时保留光圈，设置 coverageUnknown: true，给出中英文手动校准说明。本表 50 型的 136 条镜头需要手动校准，不能自动把未知视角转换成覆盖范围。
- 只有有厂商对应证据的硬件代码才进入 modelCodes / modelSources；modelPatterns 完整锚定。未覆盖的国际版、代码后缀及运营商版本不猜测、不泛配。

## 年份覆盖

| 发布年 | HUAWEI 型号 | HONOR 型号 |
| --- | ---: | ---: |
| 2022 | 8 | 9 |
| 2023 | 12 | 7 |
| 2024 | 13 | 13 |
| 2025 | 16 | 8 |
| 2026 | 14 | 12 |

华为主干包括 P50E / Mate 40E Pro、Mate 50 / 60 / 70 / 80 / 90 各主要光学版本、P60、Pura 70 / 80 / 90、Mate Xs 2 / X3 / X5 / X6 / X7、XT / XTs / XT 2、Pocket S / Pocket 2 / 优享版、Pura X / X Max / X View，以及满足价格门槛的 nova Ultra / Pro / Flip。
荣耀主干包括 Magic4–Magic9 的普通 / Pro / 至臻 / RSR 可核实版本、Magic V / Vs / V2 / Vs2 / V3 / Vs3 / V5 / V6、V Purse、V Flip / V Flip2，以及符合门槛的数字系列 70–600 Pro、GT Pro、WIN / WIN2 / WIN RT2 / WIN Turbo2、Robot Phone。

## 一手来源与补充来源

华为相机证据为各条目的 consumer.huawei.com 规格页面（失效页同时保留 Internet Archive 中的官方页面快照）。22 条记录附 archivedSource。网页存档仍是当时厂商页面的保存，不是媒体参数转述。部分存档响应实际为 gzip 二进制但没有声明正确响应编码，读取时按文件头解压后才提取规格。

[华为官方支持设备 / 型号代码表](https://developer.huawei.com/consumer/cn/doc/harmonyos-releases/support-device/)及 [P50E 产品重要信息](https://consumer.huawei.com/cn/support/content/zh-cn15920305/)提供华为代码证据。
[荣耀官方开发者机型适配表](https://developer.honor.com/cn/docs/game_center/guides/jieruzhinan/jixing)提供荣耀代码证据；其认证代码表列至 Robot Phone 等 2026 年机型，较晚发布的 Magic9、WIN2 等本次没有取得可核验的代码，不据此推断。

等效焦距优先官方规格的原生镜头焦段，未从近似“几倍变焦”推算。荣耀部分官网不给焦距，在四款机器共 12 条镜头上采用 DXOMARK 硬件规格表，逐镜头注明 equivalentSource / equivalentNote，光圈仍取大陆官网：

- [Magic4 Pro](https://www.dxomark.com/honor-magic4-pro-camera-test/)：23 / 13 / 90 mm。
- [Magic6 Pro](https://www.dxomark.com/honor-magic6-pro-camera-test/)：24 / 13 / 68 mm。
- [Magic7 Pro](https://www.dxomark.com/honor-magic7-pro-camera-test/)：24 / 12 / 72 mm。
- [Magic8 Pro](https://www.dxomark.com/honor-magic8-pro-camera-test/)：23 / 12 / 85 mm。

没有将这些 Pro 版焦距继承给相近名称的至臻 / RSR 版，也没有把 DXOMARK 某些报告中的默认输出“实测有效视角”硬当原生镜头焦距。

## 代码歧义与明确缺项

93 型有官方硬件代码，其中 83 型可通过精确代码自动识别。以下共享代码的 10 型保留 modelCodes / modelSources，但 modelPatterns 为空并说明必须手选：

- Mate 80 Pro Max 与风驰版同为 SGT-AL10，且风驰版没有普通 Pro Max 的第二条 140 mm 超长焦通路；绝不能根据代码任选一方。
- Mate 60 Pro+ 与 Mate 60 RS 共用 ALN-AL10。
- Magic V2 与 V2 RSR 共用 VER-AN10。
- Magic6 至臻与 Magic6 RSR 共用 BVL-AN20。
- 荣耀 500 Pro 与 MOLLY 限定版共用 MEP-AN00。

暂未收集到官方代码映射的 19 型，保留营销名称手选：HONOR Magic9；HONOR Magic9 Pro Max；HONOR Magic9 超能版；HONOR WIN RT2；HONOR WIN Turbo2；HONOR WIN2；HUAWEI Mate 40E Pro；HUAWEI Mate 50；HUAWEI Mate 50 Pro；HUAWEI Mate 50 RS 保时捷设计；HUAWEI Mate 50E；HUAWEI Mate Xs 2；HUAWEI Pocket S；HUAWEI Mate X3；HUAWEI nova 11 Ultra；HUAWEI P60；HUAWEI P60 Art；HUAWEI P60 Pro；HUAWEI Pura 90 Plus。P50E 官方还列有 ABR-AL90；本次仅将已对应首发规格的 ABR-AL60 写入自动模式，后续芯片 / 配置版不混用。

已知有待进一步核验、因此未悄悄继承参数的版本：Pura 70 北斗卫星消息版、nova 12 Ultra 星耀版、nova Flip S 的满足门槛首发配置、部分 Mate X 典藏 / 艺术定制 / 乐臻版及 Magic V2 至臻版的独立命名与价格记录。同名版本若相机配置不同必须分开。颜色 / 内存配置不自动增加镜头档案。

明确不以低价或优惠补入：2021 年首次发布的 P50 / P50 Pro / P50 Pocket、荣耀 Magic3 / 60 Pro 不在年份范围；nova 11 Pro 最高首发 3999 元（[发布价表](https://www.ithome.com/0/686/959.htm)）、nova 14 Pro 最高 3999 元（[发布价表](https://www.ithome.com/0/854/146.htm)）、nova 15 Pro 最高 3899 元（[发布价表](https://www.ithome.com/0/906/990.htm)）、nova 16 最高 3499 元（[发布价表](https://www.ithome.com/0/960/234.htm)），均没有以这些配置纳入。其余资料不充分的边缘机型保留缺项，不宣称已排除。

## 逐机核验索引

“缺范围”是未能确认等效焦距 / 明确方向 FOV 的镜头数，不表示没有光圈。硬件代码有证据但共享时标“手选”。

| 品牌 / 型号 | 年 | 最低满足条件的首发价（元） | 镜头数 / 缺范围 | 硬件代码 | 规格 / 价格证据 |
| --- | ---: | ---: | --- | --- | --- |
| HONOR 70 Pro | 2022 | 4399 | 3 / 3 | SDY-AN00 | [规格](https://www.honor.com/cn/phones/honor-70-pro/spec/) / [定价](https://m.cnmo.com/news/732955.html) |
| HONOR 70 Pro+ | 2022 | 4299 | 3 / 3 | HPB-AN00 | [规格](https://www.honor.com/cn/phones/honor-70-pro-plus/spec/) / [定价](https://www.honor.com/cn/news/honor-70-series-launch/) |
| HONOR 80 Pro | 2022 | 4099 | 2 / 2 | ANP-AN00 | [规格](https://www.honor.com/cn/phones/honor-80-pro/spec/) / [定价](https://mobile.zol.com.cn/801/8012743.html) |
| HONOR Magic V | 2022 | 9999 | 2 / 2 | MGI-AN00 | [规格](https://www.honor.com/cn/phones/honor-magic-v/spec/) / [定价](https://www.honor.com/cn/news/honor-magic-v-first-foldable-flagship/) |
| HONOR Magic Vs | 2022 | 7499 | 3 / 3 | FRI-AN00 | [规格](https://www.honor.com/cn/phones/honor-magic-vs/spec/) / [定价](https://www.honor.com/cn/news/magic-vs-os/) |
| HONOR Magic Vs 至臻版 | 2022 | 10888 | 3 / 3 | FRI-AN10 | [规格](https://www.honor.com/cn/phones/honor-magic-vs-ultimate/spec/) / [定价](https://www.honor.com/cn/news/magic-vs-os/) |
| HONOR Magic4 | 2022 | 4499 | 3 / 3 | LGE-AN00 | [规格](https://www.honor.com/cn/phones/honor-magic4/spec/) / [定价](https://www.ithome.com/0/609/595.htm) |
| HONOR Magic4 Pro | 2022 | 5499 | 3 / 0 | LGE-AN10 | [规格](https://www.honor.com/cn/phones/honor-magic4-pro/spec/) / [定价](https://www.honor.com/cn/news/honor-announces-magic4-series/) |
| HONOR Magic4 至臻版 | 2022 | 7999 | 3 / 3 | LGE-AN20 | [规格](https://www.honor.com/cn/phones/honor-magic4-ultimate/spec/) / [定价](https://www.honor.com/cn/news/honor-announces-magic4-series/) |
| HONOR 100 Pro | 2023 | 4399 | 3 / 3 | MAA-AN10 | [规格](https://www.honor.com/cn/phones/honor-100-pro/spec/) / [定价](https://www.honor.com/cn/news/honor-100-launch/) |
| HONOR Magic V2 | 2023 | 8999 | 3 / 3 | VER-AN10（手选） | [规格](https://www.honor.com/cn/phones/honor-magic-v2/spec/) / [定价](https://www.honor.com/cn/news/honor-magicv2-china-launch/) |
| HONOR Magic Vs2 | 2023 | 6999 | 3 / 3 | VER-AN00 | [规格](https://www.honor.com/cn/phones/honor-magic-vs-2/spec/) / [定价](https://www.honor.com/cn/news/honor-magicvs2-chinalaunch/) |
| HONOR Magic5 | 2023 | 4499 | 3 / 0 | PGT-AN00 | [规格](https://www.honor.com/cn/phones/honor-magic5/spec/) / [定价](https://club.honor.com/cn/thread-27146025-1-1.html) |
| HONOR Magic5 Pro | 2023 | 5199 | 3 / 0 | PGT-AN10 | [规格](https://www.honor.com/cn/phones/honor-magic5-pro/spec/) / [定价](https://www.honor.com/cn/news/honor-magic-5-launch-china/) |
| HONOR Magic5 至臻版 | 2023 | 6699 | 3 / 0 | PGT-AN20 | [规格](https://www.honor.com/cn/phones/honor-magic5-ultimate/spec/) / [定价](https://www.honor.com/cn/news/honor-magic-5-launch-china/) |
| HONOR V Purse | 2023 | 5999 | 2 / 2 | VCA-AN00 | [规格](https://www.honor.com/cn/phones/honor-v-purse/spec/) / [定价](https://www.honor.com/cn/news/honor-vpurse-chinalaunch/) |
| HONOR 200 Pro | 2024 | 4499 | 3 / 3 | ELP-AN00 | [规格](https://www.honor.com/cn/phones/honor-200-pro/spec/) / [定价](https://www.ithome.com/0/771/066.htm) |
| HONOR 300 Ultra | 2024 | 4199 | 3 / 3 | AMP-AN10 | [规格](https://www.honor.com/cn/phones/honor-300-ultra/spec/) / [定价](https://www.honor.com/cn/news/honor-300-series-launch-china/) |
| HONOR Magic V Flip | 2024 | 4999 | 2 / 2 | LRA-AN00 | [规格](https://www.honor.com/cn/phones/honor-magic-v-flip/spec/) / [定价](https://www.honor.com/cn/news/honor-magic-v-flip-launch/) |
| HONOR Magic V2 RSR 保时捷设计 | 2024 | 15999 | 3 / 3 | VER-AN10（手选） | [规格](https://www.honor.com/cn/phones/honor-magic-v2-rsr-porsche-design/spec/) / [定价](https://www.honor.com/cn/news/honor-magic6-launch/) |
| HONOR Magic V3 | 2024 | 8999 | 3 / 3 | FCP-AN10, FCP-AN20 | [规格](https://www.honor.com/cn/phones/honor-magic-v3/spec/) / [定价](https://www.honor.com/cn/news/honor-magic-v3-series-launch/) |
| HONOR Magic Vs3 | 2024 | 6999 | 3 / 3 | FLC-AN00 | [规格](https://www.honor.com/cn/phones/honor-magic-vs-3/spec/) / [定价](https://www.honor.com/cn/news/honor-magic-v3-series-launch/) |
| HONOR Magic6 | 2024 | 4399 | 3 / 3 | BVL-AN00 | [规格](https://www.honor.com/cn/phones/honor-magic6/spec/) / [定价](https://www.honor.com/cn/news/honor-magic6-launch/) |
| HONOR Magic6 Pro | 2024 | 5699 | 3 / 0 | BVL-AN16 | [规格](https://www.honor.com/cn/phones/honor-magic6-pro/spec/) / [定价](https://www.honor.com/cn/news/honor-magic6-launch/) |
| HONOR Magic6 RSR 保时捷设计 | 2024 | 9999 | 3 / 3 | BVL-AN20（手选） | [规格](https://www.honor.com/cn/phones/honor-magic6-rsr-porsche-design/spec/) / [定价](https://www.honor.com/cn/news/porsche-design-honor-magic6-rsr/) |
| HONOR Magic6 至臻版 | 2024 | 6999 | 3 / 3 | BVL-AN20（手选） | [规格](https://www.honor.com/cn/phones/honor-magic6-ultimate/spec/) / [定价](https://www.honor.com/cn/news/magic-6-all-series/) |
| HONOR Magic7 | 2024 | 4499 | 3 / 3 | PTP-AN00, PTP-AN60 | [规格](https://www.honor.com/cn/phones/honor-magic7/spec/) / [定价](https://www.honor.com/cn/news/honor-magic7-launch/) |
| HONOR Magic7 Pro | 2024 | 5699 | 3 / 0 | PTP-AN10, PTP-AN70 | [规格](https://www.honor.com/cn/phones/honor-magic7-pro/spec/) / [定价](https://www.honor.com/cn/news/honor-magic7-launch/) |
| HONOR Magic7 RSR 保时捷设计 | 2024 | 7999 | 3 / 3 | PTP-AN20 | [规格](https://www.honor.com/cn/phones/honor-magic7-rsr-porsche-design/spec/) / [定价](https://www.honor.com/cn/news/honor-magic7-rsr-porsche-design-launch/) |
| HONOR 400 Pro | 2025 | 4499 | 3 / 3 | DNP-AN00 | [规格](https://www.honor.com/cn/phones/honor-400-pro/spec/) / [定价](https://www.honor.com/cn/news/honor-400-launch/) |
| HONOR 500 Pro | 2025 | 4199 | 3 / 3 | MEP-AN00（手选） | [规格](https://www.honor.com/cn/phones/honor-500-pro/spec/) / [定价](https://www.honor.com/cn/news/honor-500-launch/) |
| HONOR GT Pro | 2025 | 4299 | 3 / 3 | PPG-AN00 | [规格](https://www.honor.com/cn/phones/honor-gt-pro/spec/) / [定价](https://m.ithome.com/html/848956.htm) |
| HONOR Magic V Flip2 | 2025 | 5499 | 2 / 2 | CLE-AN00 | [规格](https://www.honor.com/cn/phones/honor-magic-v-flip2/spec/) / [定价](https://www.honor.com/cn/news/honor-magic-v-flip-launch-china/) |
| HONOR Magic V5 | 2025 | 8999 | 3 / 3 | MBH-AN10 | [规格](https://www.honor.com/cn/phones/honor-magic-v5/spec/) / [定价](https://www.honor.com/cn/news/honor-magic-v5-launch/) |
| HONOR Magic8 | 2025 | 4499 | 3 / 3 | BKQ-AN00 | [规格](https://www.honor.com/cn/phones/honor-magic8/spec/) / [定价](https://www.honor.com/cn/news/honor-magic8-launch/) |
| HONOR Magic8 Pro | 2025 | 5699 | 3 / 0 | BKQ-AN10, BKQ-AN90 | [规格](https://www.honor.com/cn/phones/honor-magic8-pro/spec/) / [定价](https://www.honor.com/cn/news/honor-magic8-launch/) |
| HONOR WIN | 2025 | 4499 | 3 / 3 | AAP-AN00 | [规格](https://www.honor.com/cn/phones/honor-win/spec/) / [定价](https://article.pchome.net/content-2192192.html) |
| HONOR 500 Pro MOLLY 20周年限定版 | 2026 | 4499 | 3 / 3 | MEP-AN00（手选） | [规格](https://www.honor.com/cn/phones/honor-500-pro-molly/spec/) / [定价](https://m.ithome.com/html/914554.htm) |
| HONOR 600 Pro | 2026 | 4299 | 3 / 3 | VKI-AN00 | [规格](https://www.honor.com/cn/phones/honor-600-pro/spec/) / [定价](https://www.ithome.com/0/956/930.htm) |
| HONOR Magic V6 | 2026 | 8999 | 3 / 3 | PNM-AN10, PNM-AN20 | [规格](https://www.honor.com/cn/phones/honor-magic-v6/spec/) / [定价](https://www.honor.com/cn/news/honor-magic-v6-launch/) |
| HONOR Magic8 Pro Air | 2026 | 4999 | 3 / 0 | LDY-AN00 | [规格](https://www.honor.com/cn/phones/honor-magic8-pro-air/spec/) / [定价](https://www.honor.com/cn/news/honor-magic8-pro-air-launch/) |
| HONOR Magic8 RSR 保时捷设计 | 2026 | 7999 | 3 / 3 | BKQ-AN20 | [规格](https://www.honor.com/cn/phones/honor-magic8-rsr-porsche-design/spec/) / [定价](https://www.honor.com/cn/news/honor-magic8-rsr-launch/) |
| HONOR Magic9 | 2026 | 4999 | 3 / 3 | 待核 | [规格](https://www.honor.com/cn/phones/honor-magic9/spec/) / [定价](https://www.honor.com/cn/news/honor-magic9-launch/) |
| HONOR Magic9 Pro Max | 2026 | 6499 | 3 / 3 | 待核 | [规格](https://www.honor.com/cn/phones/honor-magic9-pro-max/spec/) / [定价](https://www.honor.com/cn/news/honor-magic9-launch/) |
| HONOR Magic9 超能版 | 2026 | 4499 | 3 / 3 | 待核 | [规格](https://www.honor.com/cn/phones/honor-magic9-super-power/spec/) / [定价](https://www.honor.com/cn/news/honor-magic9-launch/) |
| HONOR Robot Phone | 2026 | 9999 | 3 / 3 | APH-AN00 | [规格](https://www.honor.com/cn/phones/honor-robot-phone/spec/) / [定价](https://www.honor.com/cn/news/honor-robot-phone-launch/) |
| HONOR WIN RT2 | 2026 | 4199 | 2 / 2 | 待核 | [规格](https://www.honor.com/cn/phones/honor-win-rt2/spec/) / [定价](https://www.ithome.com/1/016/049.htm) |
| HONOR WIN Turbo2 | 2026 | 4199 | 2 / 2 | 待核 | [规格](https://www.honor.com/cn/phones/honor-win-turbo2/spec/) / [定价](https://www.ithome.com/1/015/883.htm) |
| HONOR WIN2 | 2026 | 5499 | 2 / 2 | 待核 | [规格](https://www.honor.com/cn/phones/honor-win2/spec/) / [定价](https://www.ithome.com/1/016/049.htm) |
| HUAWEI Mate 40E Pro | 2022 | 6499 | 3 / 0 | 待核 | [规格](https://consumer.huawei.com/cn/phones/mate40e-pro/specs/) / [官方存档](https://web.archive.org/web/20220228143241id_/https://consumer.huawei.com/cn/phones/mate40e-pro/specs/) / [定价](https://m.ithome.com/html/605104.htm) |
| HUAWEI Mate 50 | 2022 | 4999 | 3 / 3 | 待核 | [规格](https://consumer.huawei.com/kh/phones/mate50/specs/) / [定价](https://www.ithome.com/0/639/288.htm) |
| HUAWEI Mate 50 Pro | 2022 | 6799 | 3 / 3 | 待核 | [规格](https://consumer.huawei.com/kh/phones/mate50-pro/specs/) / [定价](https://www.ithome.com/0/639/288.htm) |
| HUAWEI Mate 50 RS 保时捷设计 | 2022 | 12999 | 3 / 0 | 待核 | [规格](https://consumer.huawei.com/cn/phones/porsche-design-mate50-rs/specs/) / [官方存档](https://web.archive.org/web/20221129144722id_/https://consumer.huawei.com/cn/phones/porsche-design-mate50-rs/specs/) / [定价](https://www.ithome.com/0/639/288.htm) |
| HUAWEI Mate 50E | 2022 | 4499 | 2 / 2 | 待核 | [规格](https://consumer.huawei.com/cn/phones/mate50e/specs/) / [官方存档](https://web.archive.org/web/20221013104026id_/https://consumer.huawei.com/cn/phones/mate50e/specs/) / [定价](https://www.ithome.com/0/639/288.htm) |
| HUAWEI Mate Xs 2 | 2022 | 9999 | 3 / 3 | 待核 | [规格](https://consumer.huawei.com/kh/phones/mate-xs2/specs/) / [定价](https://www.ithome.com/0/615/730.htm) |
| HUAWEI P50E | 2022 | 4088 | 3 / 0 | ABR-AL60 | [规格](https://consumer.huawei.com/cn/phones/p50e/specs/) / [官方存档](https://web.archive.org/web/20220523120958id_/https://consumer.huawei.com/cn/phones/p50e/specs/) / [定价](https://www.ithome.com/0/608/088.htm) |
| HUAWEI Pocket S | 2022 | 5988 | 2 / 2 | 待核 | [规格](https://consumer.huawei.com/cn/phones/pocket-s/specs/) / [官方存档](https://web.archive.org/web/20221103102456id_/https://consumer.huawei.com/cn/phones/pocket-s/specs/) / [定价](https://www.ithome.com/0/652/645.htm) |
| HUAWEI Mate 60 | 2023 | 5499 | 3 / 0 | BRA-AL00 | [规格](https://consumer.huawei.com/cn/phones/mate60/specs/) / [官方存档](https://web.archive.org/web/20230830123312id_/https://consumer.huawei.com/cn/phones/mate60/specs/) / [定价](https://www.ithome.com/0/721/564.htm) |
| HUAWEI Mate 60 Pro | 2023 | 6499 | 3 / 0 | ALN-AL00, ALN-AL80 | [规格](https://consumer.huawei.com/cn/phones/mate60-pro/specs/) / [官方存档](https://web.archive.org/web/20230830145834id_/https://consumer.huawei.com/cn/phones/mate60-pro/specs/) / [定价](https://www.ithome.com/0/721/564.htm) |
| HUAWEI Mate 60 Pro+ | 2023 | 8999 | 3 / 0 | ALN-AL10（手选） | [规格](https://consumer.huawei.com/cn/phones/mate60-pro-plus/specs/) / [官方存档](https://web.archive.org/web/20230909090926id_/https://consumer.huawei.com/cn/phones/mate60-pro-plus/specs/) / [定价](https://www.ithome.com/0/721/564.htm) |
| HUAWEI Mate 60 RS 非凡大师 | 2023 | 11999 | 3 / 0 | ALN-AL10（手选） | [规格](https://consumer.huawei.com/cn/phones/mate60-rs-ultimate-design/specs/) / [官方存档](https://web.archive.org/web/20240625015441id_/https://consumer.huawei.com/cn/phones/mate60-rs-ultimate-design/specs/) / [定价](https://mobile.zol.com.cn/834/8342547.html) |
| HUAWEI Mate X3 | 2023 | 12999 | 3 / 0 | 待核 | [规格](https://consumer.huawei.com/cn/phones/mate-x3/specs/) / [官方存档](https://web.archive.org/web/20230324075032id_/https://consumer.huawei.com/cn/phones/mate-x3/specs/) / [定价](https://m.cnmo.com/news/751102.html) |
| HUAWEI Mate X5 | 2023 | 12999 | 3 / 0 | ALT-AL10 | [规格](https://consumer.huawei.com/cn/phones/mate-x5/specs/) / [官方存档](https://web.archive.org/web/20230911103130id_/https://consumer.huawei.com/cn/phones/mate-x5/specs/) / [定价](https://www.ithome.com/0/721/269.htm) |
| HUAWEI nova 11 Ultra | 2023 | 4499 | 2 / 2 | 待核 | [规格](https://consumer.huawei.com/cn/phones/nova11-ultra/specs/) / [官方存档](https://web.archive.org/web/20230424113223id_/https://consumer.huawei.com/cn/phones/nova11-ultra/specs/) / [定价](https://www.ithome.com/0/686/959.htm) |
| HUAWEI nova 12 Pro | 2023 | 4399 | 2 / 2 | ADA-AL00 | [规格](https://consumer.huawei.com/cn/phones/nova12-pro/specs/) / [官方存档](https://web.archive.org/web/20240408105411id_/https://consumer.huawei.com/cn/phones/nova12-pro/specs/) / [定价](https://m.ithome.com/html/741637.htm) |
| HUAWEI nova 12 Ultra | 2023 | 4699 | 2 / 2 | ADA-AL00U | [规格](https://consumer.huawei.com/cn/phones/nova12-ultra/specs/) / [官方存档](https://web.archive.org/web/20240101141432id_/https://consumer.huawei.com/cn/phones/nova12-ultra/specs/) / [定价](https://m.ithome.com/html/741637.htm) |
| HUAWEI P60 | 2023 | 4488 | 3 / 0 | 待核 | [规格](https://consumer.huawei.com/cn/phones/p60/specs/) / [官方存档](https://web.archive.org/web/20230323102056id_/https://consumer.huawei.com/cn/phones/p60/specs/) / [定价](https://m.cnmo.com/news/751102.html) |
| HUAWEI P60 Art | 2023 | 8988 | 3 / 0 | 待核 | [规格](https://consumer.huawei.com/cn/phones/p60-art/specs/) / [官方存档](https://web.archive.org/web/20240301003920id_/https://consumer.huawei.com/cn/phones/p60-art/specs/) / [定价](https://m.cnmo.com/news/751102.html) |
| HUAWEI P60 Pro | 2023 | 6988 | 3 / 0 | 待核 | [规格](https://consumer.huawei.com/cn/phones/p60-pro/specs/) / [官方存档](https://web.archive.org/web/20230323102117id_/https://consumer.huawei.com/cn/phones/p60-pro/specs/) / [定价](https://m.cnmo.com/news/751102.html) |
| HUAWEI Mate 70 | 2024 | 5499 | 3 / 0 | CLS-AL00, CLS-AL30 | [规格](https://consumer.huawei.com/cn/phones/mate70/specs/) / [定价](https://www.ithome.com/0/813/323.htm) |
| HUAWEI Mate 70 Pro | 2024 | 6499 | 3 / 0 | PLR-AL00, PLR-AL30 | [规格](https://consumer.huawei.com/cn/phones/mate70-pro/specs/) / [定价](https://www.ithome.com/0/813/323.htm) |
| HUAWEI Mate 70 Pro+ | 2024 | 8499 | 3 / 0 | PLA-AL10 | [规格](https://consumer.huawei.com/cn/phones/mate70-pro-plus/specs/) / [定价](https://www.ithome.com/0/813/323.htm) |
| HUAWEI Mate 70 RS 非凡大师 | 2024 | 11999 | 3 / 0 | PLU-AL10 | [规格](https://consumer.huawei.com/cn/phones/mate70-rs-ultimate-design/specs/) / [定价](https://www.ithome.com/0/813/323.htm) |
| HUAWEI Mate X6 | 2024 | 12999 | 3 / 0 | ICL-AL10 | [规格](https://consumer.huawei.com/cn/phones/mate-x6/specs/) / [定价](https://finance.sina.com.cn/roll/2024-11-26/doc-incxktkk7473577.shtml) |
| HUAWEI Mate XT 非凡大师 | 2024 | 19999 | 3 / 0 | GRL-AL10 | [规格](https://consumer.huawei.com/cn/phones/mate-xt-ultimate-design/specs/) / [定价](https://www.ithome.com/0/794/863.htm) |
| HUAWEI nova 13 Pro | 2024 | 4499 | 3 / 0 | MIS-AL00 | [规格](https://consumer.huawei.com/cn/phones/nova13-pro/specs/) / [官方存档](https://web.archive.org/web/20241123050359id_/https://consumer.huawei.com/cn/phones/nova13-pro/specs/) / [定价](https://www.ithome.com/0/804/348.htm) |
| HUAWEI nova Flip | 2024 | 5288 | 2 / 2 | PSD-AL00 | [规格](https://consumer.huawei.com/cn/phones/nova-flip/specs/) / [官方存档](https://web.archive.org/web/20240807075801id_/https://consumer.huawei.com/cn/phones/nova-flip/specs/) / [定价](https://www.ithome.com/0/786/377.htm) |
| HUAWEI Pocket 2 | 2024 | 7499 | 3 / 0 | LEM-AL00 | [规格](https://consumer.huawei.com/cn/phones/pocket-2/specs/) / [官方存档](https://web.archive.org/web/20240229090059id_/https://consumer.huawei.com/cn/phones/pocket-2/specs/) / [定价](https://www.ithome.com/0/752/959.htm) |
| HUAWEI Pura 70 | 2024 | 5499 | 3 / 0 | ADY-AL00 | [规格](https://consumer.huawei.com/sg/phones/pura70/specs/) / [定价](https://www.ithome.com/0/762/571.htm) |
| HUAWEI Pura 70 Pro | 2024 | 6499 | 3 / 0 | HBN-AL00 | [规格](https://consumer.huawei.com/sg/phones/pura70-pro/specs/) / [定价](https://www.ithome.com/0/762/865.htm) |
| HUAWEI Pura 70 Pro+ | 2024 | 7999 | 3 / 0 | HBN-AL10, HBN-AL80 | [规格](https://consumer.huawei.com/cn/phones/pura70-pro-plus/specs/) / [官方存档](https://web.archive.org/web/20240419125519id_/https://consumer.huawei.com/cn/phones/pura70-pro-plus/specs/) / [定价](https://www.ithome.com/0/762/865.htm) |
| HUAWEI Pura 70 Ultra | 2024 | 9999 | 3 / 0 | HBP-AL00 | [规格](https://consumer.huawei.com/sg/phones/pura70-ultra/specs/) / [定价](https://www.ithome.com/0/762/865.htm) |
| HUAWEI Mate 70 Air | 2025 | 4199 | 3 / 0 | SUP-AL90, SUP-AL90a | [规格](https://consumer.huawei.com/cn/phones/mate70-air/specs/) / [官方存档](https://web.archive.org/web/20251106131116id_/https://consumer.huawei.com/cn/phones/mate70-air/specs/) / [定价](https://www.ithome.com/0/895/233.htm) |
| HUAWEI Mate 70 Pro 优享版 | 2025 | 6199 | 3 / 0 | PLR-AL50 | [规格](https://consumer.huawei.com/cn/phones/mate70-pro-youxiangban/specs/) / [定价](https://www.ithome.com/0/834/276.htm) |
| HUAWEI Mate 80 | 2025 | 4699 | 3 / 0 | VYG-AL00, VYG-AL30 | [规格](https://consumer.huawei.com/cn/phones/mate80/specs/) / [定价](https://www.ithome.com/0/900/075.htm) |
| HUAWEI Mate 80 Pro | 2025 | 5999 | 3 / 0 | SGT-AL00, SGT-AL50 | [规格](https://consumer.huawei.com/cn/phones/mate80-pro/specs/) / [定价](https://www.ithome.com/0/900/075.htm) |
| HUAWEI Mate 80 Pro Max | 2025 | 7999 | 4 / 0 | SGT-AL10（手选） | [规格](https://consumer.huawei.com/cn/phones/mate80-pro-max/specs/) / [定价](https://www.jiemian.com/article/13681221.html) |
| HUAWEI Mate 80 RS 非凡大师 | 2025 | 11999 | 4 / 0 | SGU-AL10 | [规格](https://consumer.huawei.com/cn/phones/mate80-rs-ultimate-design/specs/) / [定价](https://www.jiemian.com/article/13681221.html) |
| HUAWEI Mate X7 | 2025 | 12999 | 3 / 0 | DEL-AL10 | [规格](https://consumer.huawei.com/cn/phones/mate-x7/specs/) / [定价](https://news.mydrivers.com/blog/20251125.htm) |
| HUAWEI Mate XTs 非凡大师 | 2025 | 17999 | 3 / 0 | GRL-AL20 | [规格](https://consumer.huawei.com/cn/phones/mate-xts-ultimate-design/specs/) / [定价](https://www.ithome.com/0/880/388.htm) |
| HUAWEI nova 14 Ultra | 2025 | 4199 | 3 / 0 | MRT-AL10 | [规格](https://consumer.huawei.com/cn/phones/nova14-ultra/specs/) / [定价](https://www.ithome.com/0/854/145.htm) |
| HUAWEI nova 15 Ultra | 2025 | 4199 | 3 / 0 | SLY-AL00 | [规格](https://consumer.huawei.com/cn/phones/nova15-ultra/specs/) / [定价](https://www.ithome.com/0/906/990.htm) |
| HUAWEI Pocket 2 优享版 | 2025 | 5499 | 3 / 0 | LEM-AL50 | [规格](https://consumer.huawei.com/cn/phones/pocket-2-youxiangban/specs/) / [定价](https://www.ithome.com/0/863/740.htm) |
| HUAWEI Pura 80 | 2025 | 4699 | 3 / 0 | HED-AL00 | [规格](https://consumer.huawei.com/cn/phones/pura80/specs/) / [定价](https://www.jiemian.com/article/13073690.html) |
| HUAWEI Pura 80 Pro | 2025 | 6499 | 3 / 0 | LMR-AL00 | [规格](https://consumer.huawei.com/cn/phones/pura80-pro/specs/) / [定价](https://www.ithome.com/0/860/029.htm) |
| HUAWEI Pura 80 Pro+ | 2025 | 7999 | 3 / 0 | LMR-AL10 | [规格](https://consumer.huawei.com/cn/phones/pura80-pro-plus/specs/) / [定价](https://www.ithome.com/0/860/029.htm) |
| HUAWEI Pura 80 Ultra | 2025 | 9999 | 4 / 0 | LMU-AL10 | [规格](https://consumer.huawei.com/cn/phones/pura80-ultra/specs/) / [定价](https://www.ithome.com/0/860/029.htm) |
| HUAWEI Pura X | 2025 | 7499 | 3 / 0 | VDE-AL00 | [规格](https://consumer.huawei.com/cn/phones/pura-x/specs/) / [定价](https://www.eepw.com.cn/article/202503/468423.htm) |
| HUAWEI Mate 80 Pro Max 风驰版 | 2026 | 8499 | 3 / 0 | SGT-AL10（手选） | [规格](https://consumer.huawei.com/cn/phones/mate80-pro-max-fengchiban/specs/) / [定价](https://www.ithome.com/0/933/140.htm) |
| HUAWEI Mate 90 | 2026 | 5999 | 3 / 0 | HLS-AL00 | [规格](https://consumer.huawei.com/cn/phones/mate90/specs/) / [定价](https://consumer.huawei.com/cn/support/content/zh-cn16119533/) |
| HUAWEI Mate 90 Pro | 2026 | 6999 | 3 / 0 | CMS-AL20 | [规格](https://consumer.huawei.com/cn/phones/mate90-pro/specs/) / [定价](https://consumer.huawei.com/cn/support/content/zh-cn16119533/) |
| HUAWEI Mate 90 Pro Max | 2026 | 9499 | 3 / 0 | CMM-AL00, CMM-AL10 | [规格](https://consumer.huawei.com/cn/phones/mate90-pro-max/specs/) / [定价](https://consumer.huawei.com/cn/support/content/zh-cn16119533/) |
| HUAWEI Mate 90 RS 非凡大师 | 2026 | 12999 | 3 / 0 | CMU-AL10 | [规格](https://consumer.huawei.com/cn/phones/mate90-rs-ultimate-design/specs/) / [定价](https://consumer.huawei.com/cn/support/content/zh-cn16119533/) |
| HUAWEI Mate XT 2 非凡大师 | 2026 | 19999 | 3 / 0 | LAP-AL10 | [规格](https://consumer.huawei.com/cn/phones/mate-xt-2-ultimate-design/specs/) / [定价](https://www.ithome.com/0/999/576.htm) |
| HUAWEI nova 16 Pro | 2026 | 4399 | 3 / 0 | CRS-AL00 | [规格](https://consumer.huawei.com/cn/phones/nova16-pro/specs/) / [定价](https://www.ithome.com/0/960/234.htm) |
| HUAWEI nova 16 Ultra | 2026 | 4699 | 3 / 0 | HIP-AL00 | [规格](https://consumer.huawei.com/cn/phones/nova16-ultra/specs/) / [定价](https://www.ithome.com/0/960/234.htm) |
| HUAWEI Pura 90 | 2026 | 4699 | 3 / 0 | KEE-AL00 | [规格](https://consumer.huawei.com/cn/phones/pura90/specs/) / [定价](https://static.nfnews.com/content/202604/20/c12355395.html) |
| HUAWEI Pura 90 Plus | 2026 | 5499 | 3 / 0 | 待核 | [规格](https://consumer.huawei.com/cn/phones/pura90-plus/specs/) / [定价](https://m.eccn.com/consultation/news/2026102820233437.html) |
| HUAWEI Pura 90 Pro | 2026 | 5499 | 3 / 0 | MLN-AL00 | [规格](https://consumer.huawei.com/cn/phones/pura90-pro/specs/) / [定价](https://static.nfnews.com/content/202604/20/c12355395.html) |
| HUAWEI Pura 90 Pro Max | 2026 | 6499 | 3 / 0 | SCA-AL00 | [规格](https://consumer.huawei.com/cn/phones/pura90-pro-max/specs/) / [定价](https://static.nfnews.com/content/202604/20/c12355395.html) |
| HUAWEI Pura X Max | 2026 | 10999 | 3 / 0 | HOP-AL00 | [规格](https://consumer.huawei.com/cn/phones/pura-x-max/specs/) / [定价](https://www.xinhuanet.com/tech/20260420/4268f8f6bea14a1cb7a6d0b44ee7804e/c.html) |
| HUAWEI Pura X View | 2026 | 5999 | 3 / 0 | VOL-AL00 | [规格](https://consumer.huawei.com/cn/phones/pura-x-view/specs/) / [定价](https://www.ithome.com/0/999/348.htm) |

## 校验

落盘前检查 JSON 可解析、112 个 device.id 唯一、同机 lens.id 唯一、所有价格严格大于 4000、年份在 2022–2026、每条镜头有正光圈及英文名、所有范围未知镜头明确 coverageUnknown + note + noteEn、代码模式完整锚定且有厂家 modelSources，共享硬件代码没有自动识别模式。没有提交 git commit。
