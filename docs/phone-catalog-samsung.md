# 三星手机相机预设来源与覆盖范围

核验日期：2026-11-17。目录：`tools/phone_catalog/samsung.json`。

本次共收录 **39 款独立机型、111 个后置物理镜头**；96 个镜头有可追溯的标称 35mm 等效焦距，15 个镜头缺省焦距并提示手动输入。颜色、存储容量以及相同光学规格的联名包装不重复计数。

## 纳入与价格口径

仅纳入 2022-01-01 至核验日期已发布、在中国大陆正式首发且至少一个配置官方定价严格大于 ¥4000 的三星手机。`msrpCny` 是最低满足门槛配置的首发官方定价；`priceNote` 标明配置。这里没有用优惠、国补、换购、抢先尝鲜价格或外币换算替代首发价。

S24 采用 2024-01-25 国内发布会的最终价格，未采用提前预售的先行者价格。S26 明确区分建议零售价与首发优惠、升杯活动价格。2026 年新机以当前可访问的中国官网规格及发布/购买资料核验，不从过去产品命名推测。

尚未找到符合此口径的大陆首发官方价格证据的 Galaxy S24 FE / S25 FE / S26 FE 不计入。韩国限定的 Galaxy Z Fold Special Edition 不作为中国发布机型计入；中国 W25 独立录入，不能套用 Z Fold6 摄像头规格。未找到 W26 Flip、W27 Flip 已正式发布的证据，不添加猜测型号。

## 光学参数的含义

镜头 `source` 指向三星中国规格页（较旧机型也使用官方支持页），光圈按官方后置摄像头规格填写。`equivalentSource` 单独指向原作者相机评测或实机 API 数据，`equivalentNote` 说明近似或有条件沿用。`equivalentMm` 是标称 35mm 等效焦距，不是物理焦距。这里没有将三星未注明方向的 85°、120°等 FOV 直接用作水平视角。

原评测可能使用不同拍摄分辨率、比例或取整值，例如 23/24mm、67/69mm；这些差异不能据此判定浏览器当前流的水平视角已校准。浏览器的电子防抖、视频比例和裁切都可能改变实际覆盖范围。预设只作为有来源的起点，仍应与当前输出尺寸和实拍边界核对。

S23+/S24+/S25+/S26+ 的对应镜头沿用同代基础机型评测的标称值，前提是官方列出的后摄配置一致，注释中逐镜头说明。Fold5 的 3×镜头有条件沿用 DXOMARK 明确说明硬件未变的 Fold4 数据。W23/W24 的 Camera FV-5 数据仅补数据库明确给出的两个后摄通道，不把通道 1 的前摄错误当成长焦。W26 主摄约23mm由原评测明确给出的2×46mm折算，其长焦69mm来自原评测样张说明。

## 机型、首发价格与后摄

等效焦距按“主摄 / 超广角 / 3×长焦 / 远摄”顺序，单位 mm；“待核验”必须手动输入，不自动借用外观相似的 Galaxy 型号。

| 机型 | 年 | 首发配置价格 | 等效焦距 | 官方规格 / 型号依据 |
| --- | --- | --- | --- | --- |
| Samsung Galaxy S21 FE | 2022 | [8GB+128GB · ¥4599](https://www.ithome.com/0/597/802.htm) | 26 / 13 / 76 | [官方](https://www.samsung.com.cn/support/model/SM-G9900ZAICHC/) / [型号](https://www.samsung.com.cn/support/newsalert/126815/) |
| Samsung Galaxy S22 | 2022 | [8GB+128GB · ¥4999](https://www.ithome.com/0/604/281.htm) | 24 / 13 / 70 | [官方](https://www.samsung.com.cn/smartphones/galaxy-s22/specs/) / [型号](https://www.samsung.com.cn/support/newsalert/126815/) |
| Samsung Galaxy S22+ | 2022 | [8GB+128GB · ¥6599](https://www.ithome.com/0/604/281.htm) | 24 / 13 / 70 | [官方](https://www.samsung.com.cn/smartphones/galaxy-s22/specs/) / [型号](https://www.samsung.com.cn/support/newsalert/126815/) |
| Samsung Galaxy S22 Ultra | 2022 | [12GB+256GB · ¥9699](https://www.ithome.com/0/604/281.htm) | 24 / 13 / 70 / 230 | [官方](https://www.samsung.com.cn/smartphones/galaxy-s22-ultra/specs/) / [型号](https://www.samsung.com.cn/support/newsalert/126815/) |
| Samsung Galaxy S23 | 2023 | [8GB+128GB · ¥5199](https://www.samsung.com.cn/news/product/galaxy-s23/) | 24 / 13 / 70 | [官方](https://www.samsung.com.cn/smartphones/galaxy-s23/specs/) / [型号](https://www.samsung.com.cn/support/newsalert/126815/) |
| Samsung Galaxy S23+ | 2023 | [8GB+256GB · ¥6999](https://www.samsung.com.cn/news/product/galaxy-s23/) | 24 / 13 / 70 | [官方](https://www.samsung.com.cn/smartphones/galaxy-s23/specs/) / [型号](https://www.samsung.com.cn/support/newsalert/126815/) |
| Samsung Galaxy S23 Ultra | 2023 | [8GB+256GB · ¥8999](https://www.samsung.com.cn/news/product/galaxy-s23/) | 23 / 13 / 70 / 230 | [官方](https://www.samsung.com.cn/smartphones/galaxy-s23-ultra/specs/) / [型号](https://www.samsung.com.cn/support/newsalert/126815/) |
| Samsung Galaxy S23 FE | 2023 | [8GB+128GB · ¥4399](https://phone.cnmo.com/news/762436.html) | 24 / 13 / 75 | [官方](https://www.samsung.com.cn/smartphones/galaxy-s/galaxy-s23-fe-mint-256gb-sm-s7110lggchc/) / [型号](https://www.samsung.com.cn/support/newsalert/126815/) |
| Samsung Galaxy S24 | 2024 | [8GB+256GB · ¥5499](https://www.c114.com.cn/4app/3542/a1253808.html) | 24 / 13 / 67 | [官方](https://www.samsung.com.cn/smartphones/galaxy-s24/specs/) / [型号](https://www.samsung.com.cn/smartphones/galaxy-s24/specs/) |
| Samsung Galaxy S24+ | 2024 | [12GB+256GB · ¥6999](https://www.c114.com.cn/4app/3542/a1253808.html) | 24 / 13 / 67 | [官方](https://www.samsung.com.cn/smartphones/galaxy-s24/specs/) / [型号](https://www.samsung.com.cn/smartphones/galaxy-s24/specs/) |
| Samsung Galaxy S24 Ultra | 2024 | [12GB+256GB · ¥9699](https://www.c114.com.cn/4app/3542/a1253808.html) | 24 / 13 / 67 / 111 | [官方](https://www.samsung.com.cn/smartphones/galaxy-s24-ultra/specs/) / [型号](https://www.samsung.com.cn/smartphones/galaxy-s24-ultra/specs/) |
| Samsung Galaxy S25 | 2025 | [12GB+256GB · ¥5999](https://www.samsung.com.cn/news/product/galaxy-s25-ai/) | 24 / 13 / 67 | [官方](https://www.samsung.com.cn/smartphones/galaxy-s25/specs/) / [型号](https://www.samsung.com.cn/smartphones/galaxy-s25/specs/) |
| Samsung Galaxy S25+ | 2025 | [12GB+256GB · ¥6999](https://www.samsung.com.cn/news/product/galaxy-s25-ai/) | 24 / 13 / 67 | [官方](https://www.samsung.com.cn/smartphones/galaxy-s25/specs/) / [型号](https://www.samsung.com.cn/smartphones/galaxy-s25/specs/) |
| Samsung Galaxy S25 Ultra | 2025 | [12GB+256GB · ¥9699](https://www.samsung.com.cn/news/product/galaxy-s25-ai/) | 24 / 13 / 67 / 111 | [官方](https://www.samsung.com.cn/smartphones/galaxy-s25-ultra/specs/) / [型号](https://www.samsung.com.cn/smartphones/galaxy-s25-ultra/specs/) |
| Samsung Galaxy S25 Edge | 2025 | [12GB+256GB · ¥7999](https://www.samsung.com.cn/news/product/galaxy-s25-edge/) | 24 / 13 | [官方](https://www.samsung.com.cn/smartphones/galaxy-s25-edge/specs/) / [型号](https://www.samsung.com.cn/smartphones/galaxy-s25-edge/specs/) |
| Samsung Galaxy S26 | 2026 | [12GB+256GB · ¥6999](https://www.ithome.com/0/923/676.htm) | 24 / 13 / 67 | [官方](https://www.samsung.com.cn/smartphones/galaxy-s26/specs/) / [型号](https://www.samsung.com.cn/smartphones/galaxy-s26/specs/) |
| Samsung Galaxy S26+ | 2026 | [12GB+256GB · ¥7999](https://www.ithome.com/0/923/676.htm) | 24 / 13 / 67 | [官方](https://www.samsung.com.cn/smartphones/galaxy-s26/specs/) / [型号](https://www.samsung.com.cn/smartphones/galaxy-s26/specs/) |
| Samsung Galaxy S26 Ultra | 2026 | [12GB+256GB · ¥9999](https://www.ithome.com/0/923/675.htm) | 23 / 13 / 69 / 115 | [官方](https://www.samsung.com.cn/smartphones/galaxy-s26-ultra/specs/) / [型号](https://www.samsung.com.cn/smartphones/galaxy-s26-ultra/specs/) |
| Samsung Galaxy Z Fold4 | 2022 | [12GB+256GB · ¥12999](https://www.ithome.com/0/636/501.htm) | 23 / 12 / 66 | [官方](https://www.samsung.com.cn/smartphones/galaxy-z-fold4/specs/) / [型号](https://www.samsung.com.cn/support/newsalert/126815/) |
| Samsung Galaxy Z Fold5 | 2023 | [12GB+512GB · ¥12999](https://www.samsung.com.cn/news/product/in-china-accelerate-smart-lifestyle-upgrading/) | 23 / 12 / 66 | [官方](https://www.samsung.com.cn/smartphones/galaxy-z-fold5/specs/) / [型号](https://www.samsung.com.cn/support/newsalert/126815/) |
| Samsung Galaxy Z Fold6 | 2024 | [12GB+512GB · ¥13999](https://support-cn.samsung.com.cn/campaign/event/psg/2024/promotion/december/index.html) | 23 / 12 / 66 | [官方](https://www.samsung.com.cn/smartphones/galaxy-z-fold6/specs/) / [型号](https://www.samsung.com.cn/smartphones/galaxy-z-fold6/specs/) |
| Samsung Galaxy Z Fold7 | 2025 | [12GB+256GB · ¥13999](https://support-cn.samsung.com.cn/campaign/event/psg/2025/promotion/july/index.html) | 24 / 13 / 67 | [官方](https://www.samsung.com.cn/smartphones/galaxy-z-fold7/specs/) / [型号](https://www.samsung.com.cn/smartphones/galaxy-z-fold7/specs/) |
| Samsung Galaxy Z Fold8 | 2026 | [12GB+256GB · ¥12999](https://www.samsung.com.cn/smartphones/galaxy-z-fold8/buy/) | 23 / 13 | [官方](https://www.samsung.com.cn/smartphones/galaxy-z-fold8/specs/) / [型号](https://www.samsung.com.cn/smartphones/galaxy-z-fold8/specs/) |
| Samsung Galaxy Z Fold8 Ultra | 2026 | [12GB+256GB · ¥14999](https://www.samsung.com.cn/smartphones/galaxy-z-fold8-ultra/buy/) | 23 / 13 / 67 | [官方](https://www.samsung.com.cn/smartphones/galaxy-z-fold8-ultra/specs/) / [型号](https://www.samsung.com.cn/smartphones/galaxy-z-fold8-ultra/specs/) |
| Samsung Galaxy Z Flip4 | 2022 | [8GB+256GB · ¥7499](https://www.ithome.com/0/636/500.htm) | 24 / 13 | [官方](https://www.samsung.com.cn/smartphones/galaxy-z-flip4/specs/) / [型号](https://www.samsung.com.cn/support/newsalert/126815/) |
| Samsung Galaxy Z Flip5 | 2023 | [8GB+256GB · ¥7499](https://www.samsung.com.cn/news/product/in-china-accelerate-smart-lifestyle-upgrading/) | 24 / 13 | [官方](https://www.samsung.com.cn/smartphones/galaxy-z-flip5/specs/) / [型号](https://www.samsung.com.cn/support/newsalert/126815/) |
| Samsung Galaxy Z Flip6 | 2024 | [12GB+256GB · ¥7999](https://support-cn.samsung.com.cn/campaign/event/psg/2024/promotion/december/index.html) | 23 / 13 | [官方](https://www.samsung.com.cn/smartphones/galaxy-z-flip6/specs/) / [型号](https://www.samsung.com.cn/smartphones/galaxy-z-flip6/specs/) |
| Samsung Galaxy Z Flip7 | 2025 | [12GB+256GB · ¥7999](https://support-cn.samsung.com.cn/campaign/event/psg/2025/promotion/july/index.html) | 23 / 13 | [官方](https://www.samsung.com.cn/smartphones/galaxy-z-flip7/specs/) / [型号](https://www.samsung.com.cn/smartphones/galaxy-z-flip7/specs/) |
| Samsung Galaxy Z Flip7 FE | 2025 | [8GB+256GB · ¥6499](https://support-cn.samsung.com.cn/campaign/event/psg/2025/promotion/july/index.html) | 23 / 13 | [官方](https://www.samsung.com.cn/smartphones/galaxy-z-flip7-fe/specs/) / [型号](https://www.samsung.com.cn/smartphones/galaxy-z-flip7-fe/specs/) |
| Samsung Galaxy Z Flip8 | 2026 | [12GB+256GB · ¥8999](https://www.samsung.com.cn/smartphones/galaxy-z-flip8/buy/) | 23 / 13 | [官方](https://www.samsung.com.cn/smartphones/galaxy-z-flip8/specs/) / [型号](https://www.samsung.com.cn/smartphones/galaxy-z-flip8/specs/) |
| Samsung Galaxy Z TriFold | 2025 | [16GB+512GB · ¥19999](https://www.ithome.com/0/906/218.htm) | 24 / 13 / 67 | [官方](https://www.samsung.com.cn/smartphones/galaxy-z/galaxy-z-trifold-crafted-black-512gb-sm-f9680zkdchc/) / [型号](https://www.samsung.com.cn/smartphones/galaxy-z/galaxy-z-trifold-crafted-black-512gb-sm-f9680zkdchc/) |
| Samsung W23 | 2022 | [16GB+512GB · ¥15999](https://www.chinatelecom.com.cn/ct/news/jczt/5g/zxdt/159987.html) | 23.8 / 14 / 待核验 | [官方](https://www.samsung.com.cn/smartphones/samsung-w23/specs/) / [型号](https://www.samsung.com.cn/support/newsalert/126815/) |
| Samsung W23 Flip | 2022 | [12GB+512GB · ¥9999](https://www.chinatelecom.com.cn/ct/news/jczt/5g/zxdt/159987.html) | 待核验 / 待核验 | [官方](https://www.samsung.com.cn/smartphones/samsung-w23-flip/specs/) / [型号](https://www.samsung.com.cn/support/newsalert/126815/) |
| Samsung W24 | 2023 | [16GB+1TB · ¥15999](https://www.chinatelecom.com.cn/ct/news/jczt/5g/zxdt/160025.html) | 23.8 / 14 / 待核验 | [官方](https://www.samsung.com.cn/smartphones/samsung-w24/specs/) / [型号](https://www.samsung.com.cn/support/newsalert/126815/) |
| Samsung W24 Flip | 2023 | [12GB+512GB · ¥9999](https://www.chinatelecom.com.cn/ct/news/jczt/5g/zxdt/160025.html) | 待核验 / 待核验 | [官方](https://www.samsung.com.cn/smartphones/samsung-w24-flip/specs/) / [型号](https://www.samsung.com.cn/support/newsalert/126815/) |
| Samsung W25 | 2024 | [16GB+512GB · ¥15999](https://www.chinatelecom.com.cn/ct/news/jtxw/152936.html) | 待核验 / 待核验 / 待核验 | [官方](https://www.samsung.com.cn/smartphones/samsung-w25/specs/) / [型号](https://www.samsung.com.cn/smartphones/samsung-w25/specs/) |
| Samsung W25 Flip | 2024 | [12GB+512GB · ¥9999](https://www.chinatelecom.com.cn/ct/news/jtxw/152936.html) | 待核验 / 待核验 | [官方](https://www.samsung.com.cn/smartphones/samsung-w25-flip/specs/) / [型号](https://www.samsung.com.cn/smartphones/samsung-w25-flip/specs/) |
| Samsung W26 | 2025 | [16GB+512GB · ¥16999](https://www.chinatelecom.com.cn/ct/news/jtxw/161617.html) | 23 / 待核验 / 69 | [官方](https://www.samsung.com.cn/smartphones/samsung-w26/specs/) / [型号](https://www.samsung.com.cn/smartphones/samsung-w26/specs/) |
| Samsung W27 | 2026 | [16GB+512GB · ¥18999](https://www.samsung.com.cn/smartphones/samsung-w27/buy/) | 待核验 / 待核验 / 待核验 | [官方](https://www.samsung.com.cn/smartphones/samsung-w27/specs/) / [型号](https://www.samsung.com.cn/smartphones/samsung-w27/specs/) |

## 焦距缺口（保留可用光圈）

- Samsung W23：长焦 · 3×。
- Samsung W23 Flip：主摄 · 1×、超广角。
- Samsung W24：长焦 · 3×。
- Samsung W24 Flip：主摄 · 1×、超广角。
- Samsung W25：主摄 · 1×、超广角、长焦 · 3×。
- Samsung W25 Flip：主摄 · 1×、超广角。
- Samsung W26：超广角。
- Samsung W27：主摄 · 1×、超广角、长焦 · 3×。

这些项目在 JSON 中不填 `equivalentMm`。缺失是证据状态，不代表镜头不存在；手动选择相机仍能使用已确认的光圈测光，取景需用户补充焦距或水平视角。

## 主要一手资料与原测评入口

- [三星中国 Android 14 升级计划（含 2022/2023 机型 SM 型号对照）](https://www.samsung.com.cn/support/newsalert/126815/)。2024 年起型号由各中国官网规格页的 `data-model-code` 或官方支持 SKU 交叉核验。
- 三星中国规格页有些内容由接口动态加载。核验接口格式：`https://searchapi.samsung.com.cn/v6/front/b2c/product/spec/detail?siteCode=cn&modelList=<官方SKU>&specAnnotationYN=Y`；读取 `modelList[].spec.specItems[].attrs[]` 的后置摄像头分辨率与光圈。页面源代码中保留了实际 SKU，不把网页渲染缺失视为没有规格。
- [2023 年 S23 中国官方发布（价格表为图片）](https://www.samsung.com.cn/news/product/galaxy-s23/)。
- [2025 年 S25 官方发布](https://www.samsung.com.cn/news/product/galaxy-s25-ai/)、[S25 Edge 官方发布](https://www.samsung.com.cn/news/product/galaxy-s25-edge/)。
- [Z Fold5 / Flip5 中国官方发布](https://www.samsung.com.cn/news/product/in-china-accelerate-smart-lifestyle-upgrading/)、[2024 年官方建议零售价表](https://support-cn.samsung.com.cn/campaign/event/psg/2024/promotion/december/index.html)、[2025 年 7 月官方首发价格与活动价对照](https://support-cn.samsung.com.cn/campaign/event/psg/2025/promotion/july/index.html)。
- [中国电信 W23 / W23 Flip 发布](https://www.chinatelecom.com.cn/ct/news/jczt/5g/zxdt/159987.html)、[W24 / W24 Flip 发布](https://www.chinatelecom.com.cn/ct/news/jczt/5g/zxdt/160025.html)、[W25 / W25 Flip 发布](https://www.chinatelecom.com.cn/ct/news/jtxw/152936.html)、[W26 发布](https://www.chinatelecom.com.cn/ct/news/jtxw/161617.html)。

各镜头的直接等效焦距来源：

- [Samsung Galaxy S21 FE 主摄 · 1×；Samsung Galaxy S21 FE 超广角；Samsung Galaxy S21 FE 长焦 · 3×](https://www.notebookcheck.net/Samsung-Galaxy-S21-FE-5G-review-The-fan-edition-smartphone-goes-into-the-next-round.598090.0.html)。
- [Samsung Galaxy S22 主摄 · 1×；Samsung Galaxy S22 超广角；Samsung Galaxy S22 长焦 · 3×；Samsung Galaxy S22+ 主摄 · 1×；Samsung Galaxy S22+ 超广角；Samsung Galaxy S22+ 长焦 · 3×](https://tweakers.net/reviews/9844/samsung-galaxy-s22-en-s22%2B-kleiner-is-het-nieuwe-groter.html)。
- [Samsung Galaxy S22 Ultra 主摄 · 1×；Samsung Galaxy S22 Ultra 超广角；Samsung Galaxy S22 Ultra 长焦 · 3×；Samsung Galaxy S22 Ultra 潜望长焦 · 10×](https://www.dxomark.com/samsung-galaxy-s22-ultra-snapdragon-camera-test/)。
- [Samsung Galaxy S23 主摄 · 1×；Samsung Galaxy S23 超广角；Samsung Galaxy S23 长焦 · 3×；Samsung Galaxy S23+ 主摄 · 1×；Samsung Galaxy S23+ 超广角；Samsung Galaxy S23+ 长焦 · 3×](https://www.ephotozine.com/article/samsung-galaxy-s23-phone-review-36491)。
- [Samsung Galaxy S23 Ultra 主摄 · 1×；Samsung Galaxy S23 Ultra 超广角；Samsung Galaxy S23 Ultra 长焦 · 3×；Samsung Galaxy S23 Ultra 潜望长焦 · 10×](https://www.dkamera.de/news/samsung-galaxy-s23-ultra-smartphone-und-kameratest-teil-3/)。
- [Samsung Galaxy S23 FE 主摄 · 1×；Samsung Galaxy S23 FE 超广角；Samsung Galaxy S23 FE 长焦 · 3×](https://www.lesnumeriques.com/telephone-portable/samsung-s23-fe-p73578/test.html)。
- [Samsung Galaxy S24 主摄 · 1×；Samsung Galaxy S24 超广角；Samsung Galaxy S24 长焦 · 3×；Samsung Galaxy S24+ 主摄 · 1×；Samsung Galaxy S24+ 超广角；Samsung Galaxy S24+ 长焦 · 3×](https://www.dxomark.com/samsung-galaxy-s24-camera-test/)。
- [Samsung Galaxy S24 Ultra 主摄 · 1×；Samsung Galaxy S24 Ultra 超广角；Samsung Galaxy S24 Ultra 长焦 · 3×；Samsung Galaxy S24 Ultra 长焦 · 5×](https://www.dxomark.com/samsung-galaxy-s24-ultra-camera-test/)。
- [Samsung Galaxy S25 主摄 · 1×；Samsung Galaxy S25 超广角；Samsung Galaxy S25 长焦 · 3×；Samsung Galaxy S25+ 主摄 · 1×；Samsung Galaxy S25+ 超广角；Samsung Galaxy S25+ 长焦 · 3×](https://amateurphotographer.com/review/samsung-galaxy-s25-review/)。
- [Samsung Galaxy S25 Ultra 主摄 · 1×；Samsung Galaxy S25 Ultra 超广角；Samsung Galaxy S25 Ultra 长焦 · 3×；Samsung Galaxy S25 Ultra 长焦 · 5×](https://www.digitalcameraworld.com/tech/phones/samsung-galaxy-s25-ultra-review)。
- [Samsung Galaxy S25 Edge 主摄 · 1×；Samsung Galaxy S25 Edge 超广角](https://www.androidcentral.com/phones/samsung-galaxy/samsung-galaxy-s25-edge-review)。
- [Samsung Galaxy S26 主摄 · 1×；Samsung Galaxy S26 超广角；Samsung Galaxy S26 长焦 · 3×；Samsung Galaxy S26+ 主摄 · 1×；Samsung Galaxy S26+ 超广角；Samsung Galaxy S26+ 长焦 · 3×](https://www.dxomark.com/samsung-galaxy-s26-camera-test/)。
- [Samsung Galaxy S26 Ultra 主摄 · 1×；Samsung Galaxy S26 Ultra 超广角；Samsung Galaxy S26 Ultra 长焦 · 3×；Samsung Galaxy S26 Ultra 长焦 · 5×](https://www.notebookcheck.net/Blocks-prying-eyes-Samsung-Galaxy-S26-Ultra-review.1278349.0.html)。
- [Samsung Galaxy Z Fold4 主摄 · 1×；Samsung Galaxy Z Fold4 超广角；Samsung Galaxy Z Fold4 长焦 · 3×；Samsung Galaxy Z Fold5 长焦 · 3×](https://www.dxomark.com/samsung-galaxy-z-fold4-camera-test/)。
- [Samsung Galaxy Z Fold5 主摄 · 1×；Samsung Galaxy Z Fold5 超广角](https://www.dxomark.com/samsung-galaxy-z-fold5-camera-test/)。
- [Samsung Galaxy Z Fold6 主摄 · 1×；Samsung Galaxy Z Fold6 超广角；Samsung Galaxy Z Fold6 长焦 · 3×](https://www.dxomark.com/samsung-galaxy-z-fold6-camera-test/)。
- [Samsung Galaxy Z Fold7 主摄 · 1×；Samsung Galaxy Z Fold7 超广角；Samsung Galaxy Z Fold7 长焦 · 3×](https://www.notebookcheck.net/The-market-leader-strikes-back-Samsung-Galaxy-Z-Fold7-review.1093384.0.html)。
- [Samsung Galaxy Z Fold8 主摄 · 1×；Samsung Galaxy Z Fold8 超广角](https://www.notebookcheck.net/Shooting-to-the-top-with-a-new-form-factor-Samsung-Galaxy-Z-Fold8-review.1394389.0.html)。
- [Samsung Galaxy Z Fold8 Ultra 主摄 · 1×](https://www.digitalcameraworld.com/tech/android-phones/samsung-galaxy-z-fold-8-ultra-review)。
- [Samsung Galaxy Z Fold8 Ultra 超广角](https://mezha.ua/en/reviews/oglyad-samsung-galaxy-fold8-ultra-314154/)。
- [Samsung Galaxy Z Fold8 Ultra 长焦 · 3×](https://amateurphotographer.com/review/samsung-galaxy-z-fold-8-ultra-review/)。
- [Samsung Galaxy Z Flip4 主摄 · 1×；Samsung Galaxy Z Flip4 超广角](https://www.gsmarena.com/samsung_galaxy_z_flip4-review-2467p5.php)。
- [Samsung Galaxy Z Flip5 主摄 · 1×](https://www.gsmarena.com/samsung_galaxy_z_flip5-review-2597p5.php)。
- [Samsung Galaxy Z Flip5 超广角](https://www.gsmarena.com/samsung_galaxy_z_fold5_z_flip5_watch6_tab_s9_hands-review-2593p4.php)。
- [Samsung Galaxy Z Flip6 主摄 · 1×](https://www.notebookcheck.net/Samsung-Galaxy-Z-Flip6-5G-review-An-almost-unrivalled-flip-phone-continues-to-improve.890376.0.html)。
- [Samsung Galaxy Z Flip6 超广角](https://www.gsmarena.com/samsung_galaxy_z_flip6-review-2727p5.php)。
- [Samsung Galaxy Z Flip7 主摄 · 1×](https://www.notebookcheck.net/Compact-Flip-phone-loses-ground-Samsung-Galaxy-Z-Flip7-review.1102761.0.html)。
- [Samsung Galaxy Z Flip7 超广角](https://www.fortressofsolitude.co.za/samsung-galaxy-z-flip-7-review/)。
- [Samsung Galaxy Z Flip7 FE 主摄 · 1×；Samsung Galaxy Z Flip7 FE 超广角](https://www.gsmarena.com/samsung_galaxy_z_flip7_fe-review-2861.php)。
- [Samsung Galaxy Z Flip8 主摄 · 1×；Samsung Galaxy Z Flip8 超广角](https://www.notebookcheck.net/Ultra-lightweight-smartphone-powerhouse-with-a-6-9-inch-OLED-screen-Samsung-Galaxy-Z-Flip-8-review.1398173.0.html)。
- [Samsung Galaxy Z TriFold 主摄 · 1×；Samsung Galaxy Z TriFold 超广角；Samsung Galaxy Z TriFold 长焦 · 3×](https://s-max.jp/archives/1839981.html)。
- [Samsung W23 主摄 · 1×](https://www.camerafv5.com/devices/manufacturers/samsung/sm-w9023_v4q_0/)。
- [Samsung W23 超广角](https://www.camerafv5.com/devices/manufacturers/samsung/sm-w9023_v4q_2/)。
- [Samsung W24 主摄 · 1×](https://www.camerafv5.com/devices/manufacturers/samsung/sm-w9024_v5q_0/)。
- [Samsung W24 超广角](https://www.camerafv5.com/devices/manufacturers/samsung/sm-w9024_v5q_2/)。
- [Samsung W26 主摄 · 1×；Samsung W26 长焦 · 3×](https://finance.sina.com.cn/tech/roll/2025-10-28/doc-infvmmit1318749.shtml)。

## 数据自检

检查 JSON 可解析；39 个设备 ID 唯一；全部 `launchYear` 在 2022–2026；全部 `msrpCny > 4000`；所有后摄有正光圈及官方来源；有焦距的镜头同时有来源和近似说明；用每个来源型号核对正则只匹配所属机型。既有 `samsung-fold7`、`samsung-fold6`、`samsung-s25-ultra`、`samsung-s24-ultra` 的 ID 与识别正则保持兼容。

这份目录是资料核验结果，不代替在对应实机浏览器上验证可访问镜头、实际裁切及曝光精度。
