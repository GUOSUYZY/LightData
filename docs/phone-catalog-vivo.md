# vivo 后置镜头等效焦距补证

更新：2026-11-17。目录：`tools/phone_catalog/vivo.json`。本次仅补光学证据与镜头角色，不改首发价格及机型范围。

现有 32 款机型、99 个后置镜头。新增 53 个有来源的标称 35mm 等效焦距；覆盖从 41 增至 94 个，剩余 5 个明确要求手动输入。

## 证据与实际取景范围

光圈及物理镜头数量继续来自 vivo 中国官网规格。等效焦距优先用厂家说明，其次原作者实机评测和明确标注镜头的原样张。新闻发布规格及相同硬件沿用单独标级，不伪装成独立测量。所有新增值都有 `equivalentSource`、`equivalentSourceType`、`equivalentNote`、中英文 `note`。

此处只收物理镜头的标称原生取景点，不把蔡司人像的 35/50/85/100mm 数码裁切、CIPA 防抖测试中使用的焦段、增距镜外接200/400mm 或最高数码变焦视为额外镜头。23/24、15/16mm 的来源取整与预览裁切差异仍需实机核对，不能把标称值当作当前浏览器流的已校准水平视角。

X90 Pro 的 f/1.6 后摄是50mm人像长焦，X90 Pro+ 的 f/1.6、f/2.2 分别是50mm人像长焦和14mm超广角。原数据把这些项目重复命名为 `wide`，已修正为不同的镜头 ID、名称和角色，避免选择冲突；其官方光圈不变。

X90s 超广角是唯一的有条件跨型号沿用：快科技原测评明确其相机模组与 X90 相同，采用 X90 原测评16mm样张，两个链接均保留。X100s Pro 的23/100mm来自原作者发布报道明确列出的厂家规格，标为 `original-launch-report`。X Fold / Fold+ 使用媒体托管的厂家规格表，标为 `manufacturer-press-kit`，未声称是本项目实测。

## 本次新增证据

| 机型 | 镜头 | 等效 mm | 来源层级 | 直接来源 |
| --- | --- | --- | --- | --- |
| vivo X80 | wide | 23 | original-review | [来源](https://www.kocpc.com.tw/archives/442838) |
| vivo X80 | ultrawide | 16 | original-review | [来源](https://www.kocpc.com.tw/archives/442838) |
| vivo X80 | telephoto | 50 | original-review | [来源](https://www.kocpc.com.tw/archives/442838) |
| vivo X80 Pro | wide | 23 | original-review | [来源](https://www.gsmarena.com/vivo_x80_pro-review-2427p5.php) |
| vivo X80 Pro | ultrawide | 14 | original-review | [来源](https://www.gsmarena.com/vivo_x80_pro-review-2427p5.php) |
| vivo X80 Pro | telephoto-short | 50 | manufacturer | [来源](https://www.vivo.com.cn/vivo/x80pro/) |
| vivo X90 | wide | 23 | original-review | [来源](https://i.ifeng.com/c/8LINp4KbqtV) |
| vivo X90 | ultrawide | 16 | original-review | [来源](https://i.ifeng.com/c/8LINp4KbqtV) |
| vivo X90 Pro | wide | 23 | original-review | [来源](https://petapixel.com/2023/04/07/vivo-x90-pro-review-back-on-track/) |
| vivo X90 Pro | telephoto | 50 | original-review | [来源](https://petapixel.com/2023/04/07/vivo-x90-pro-review-back-on-track/) |
| vivo X90 Pro | ultrawide | 16 | original-review | [来源](https://petapixel.com/2023/04/07/vivo-x90-pro-review-back-on-track/) |
| vivo X90 Pro+ | wide | 23 | original-review | [来源](https://www.notebookcheck.net/Vivo-X90-Pro-Review-Vivo-sets-the-bar-very-high-with-its-flagship-smartphone.693302.0.html) |
| vivo X90 Pro+ | telephoto-short | 50 | original-review | [来源](https://www.dxomark.com/vivo-x90-pro-plus-camera-test-results/) |
| vivo X90 Pro+ | ultrawide | 14 | original-review | [来源](https://www.dxomark.com/vivo-x90-pro-plus-camera-test-results/) |
| vivo X90 Pro+ | telephoto-long | 90 | original-review | [来源](https://www.dxomark.com/vivo-x90-pro-plus-camera-test-results/) |
| vivo X90s | wide | 23 | original-review | [来源](https://news.mydrivers.com/1/918/918809_2.htm) |
| vivo X90s | ultrawide | 16 | same-hardware-review | [来源](https://i.ifeng.com/c/8LINp4KbqtV) |
| vivo X100 | wide | 23 | original-review | [来源](https://www.mobigyaan.com/vivo-x100-review) |
| vivo X100 | telephoto | 70 | original-review | [来源](https://www.mobigyaan.com/vivo-x100-review) |
| vivo X100 Pro | wide | 23 | original-review | [来源](https://amateurphotographer.com/review/vivo-x100-pro-review/) |
| vivo X100s | wide | 23 | original-review | [来源](https://www.antutu.com/doc/131749) |
| vivo X100s | telephoto | 70 | original-review | [来源](https://www.antutu.com/doc/131749) |
| vivo X100s | ultrawide | 15 | original-review | [来源](https://www.antutu.com/doc/131749) |
| vivo X100s Pro | wide | 23 | original-launch-report | [来源](https://smhn.info/202405-vivo-x100s-x100s-pro-spec) |
| vivo X100s Pro | telephoto | 100 | original-launch-report | [来源](https://smhn.info/202405-vivo-x100s-x100s-pro-spec) |
| vivo X200 | wide | 23 | original-review | [来源](https://www.gadgetmatch.com/vivo-x200-camera-review-gadgetsnaps/) |
| vivo X200 | telephoto | 70 | original-review | [来源](https://www.gadgetmatch.com/vivo-x200-camera-review-gadgetsnaps/) |
| vivo X200 Pro mini | wide | 23 | original-review | [来源](https://www.gsmarena.com/vivo_x200_pro_mini_handson_review-news-64924.php) |
| vivo X200 Pro mini | telephoto | 70 | original-review | [来源](https://www.gsmarena.com/vivo_x200_pro_mini_handson_review-news-64924.php) |
| vivo X200 Pro mini | ultrawide | 15 | original-review | [来源](https://www.gsmarena.com/vivo_x200_pro_mini_handson_review-news-64924.php) |
| vivo X200s | wide | 23 | original-review | [来源](https://m.mydrivers.com/newsview/1043182_5.html) |
| vivo X300 | wide | 23 | original-review | [来源](https://www.gsmarena.com/vivo_x300-review-2897.php) |
| vivo X500 Pro | ultrawide | 15 | original-review | [来源](https://www.gsmarena.com/vivo_x500_x500_pro_x500_pro_max_handson-review-3001.php) |
| vivo X500 Pro Max | ultrawide | 15 | original-review | [来源](https://www.androidauthority.com/vivo-x500-pro-max-review-3729439/) |
| vivo X Fold | wide | 23 | manufacturer-press-kit | [来源](https://cache.pressmailing.net/content/0fe475f0-49db-4736-840c-38c77c852448/vivo%20X%20Fold-Specsheet.pdf) |
| vivo X Fold | ultrawide | 14 | manufacturer-press-kit | [来源](https://cache.pressmailing.net/content/0fe475f0-49db-4736-840c-38c77c852448/vivo%20X%20Fold-Specsheet.pdf) |
| vivo X Fold | telephoto-long | 124.15 | manufacturer-press-kit | [来源](https://cache.pressmailing.net/content/0fe475f0-49db-4736-840c-38c77c852448/vivo%20X%20Fold-Specsheet.pdf) |
| vivo X Fold+ | wide | 23 | manufacturer-press-kit | [来源](https://www.igizmo.it/wp-content/uploads/2022/12/vivo-X-Fold-Spec-Sheet.pdf) |
| vivo X Fold+ | ultrawide | 14 | manufacturer-press-kit | [来源](https://www.igizmo.it/wp-content/uploads/2022/12/vivo-X-Fold-Spec-Sheet.pdf) |
| vivo X Fold2 | wide | 23 | original-review | [来源](https://www.gsmarena.com/vivo_x_fold2-review-2579p5.php) |
| vivo X Fold2 | ultrawide | 16 | original-review | [来源](https://www.gsmarena.com/vivo_x_fold2-review-2579p5.php) |
| vivo X Fold2 | telephoto | 47 | original-review | [来源](https://www.gsmarena.com/vivo_x_fold2-review-2579p5.php) |
| vivo X Flip | wide | 23 | original-review | [来源](https://www.gsmarena.com/vivo_x_flip-review-2576p5.php) |
| vivo X Flip | ultrawide | 16 | original-review | [来源](https://www.gsmarena.com/vivo_x_flip-review-2576p5.php) |
| vivo X Fold3 | wide | 23 | original-review | [来源](https://www.donews.com/news/detail/5/4222794.html) |
| vivo X Fold3 | ultrawide | 15 | original-review | [来源](https://m.mydrivers.com/newsview/971811_all.html) |
| vivo X Fold3 Pro | wide | 23 | original-review | [来源](https://www.leikeji.com/article/61946) |
| vivo X Fold3 Pro | ultrawide | 15 | original-review | [来源](https://www.leikeji.com/article/61946) |
| vivo X Fold3 Pro | telephoto | 70 | original-review | [来源](https://www.leikeji.com/article/61946) |
| vivo X Fold5 | wide | 23 | original-review | [来源](https://m.igao7.com/news/202506/PSyVsNKDwtqfvKwp.html) |
| vivo X Fold5 | telephoto | 70 | original-review | [来源](https://m.igao7.com/news/202506/PSyVsNKDwtqfvKwp.html) |
| vivo X Fold6 | wide | 23 | original-review | [来源](https://unwire.hk/2026/11/04/vivo-x-fold6-review/review-2/) |
| vivo S50 Pro mini | telephoto | 73 | original-review | [来源](https://mobile.zol.com.cn/1101/11016042_all.html) |

## 仍未核实

- vivo X80 Pro / telephoto-long。
- vivo X300s / wide。
- vivo X Fold+ / telephoto-long。
- vivo S50 Pro mini / wide。
- vivo S50 Pro mini / ultrawide。

这些镜头仍保留 `coverageUnknown: true`。有光圈可用于测光，取景覆盖范围需要手动输入；没有按相似外观、前代型号或倍率猜测补齐。

## 自检

JSON 可解析；32 个设备 ID 唯一；每个设备内镜头 ID 唯一；99 个后摄有正光圈及官方来源；有焦距的项目有来源链接；未知项目保留手动说明。逐字段比对确保所有设备级首发价、价格来源、年份、型号识别规则均未改动。
