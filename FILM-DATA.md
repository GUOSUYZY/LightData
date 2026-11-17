# 胶片数据核验

当前可选的 168 个型号中，122 个附有按型号、文献和页码记录的密度样本，46 个使用标明类别的近似响应。122 个采样型号中，85 个设置入口可直接打开手册，37 个定位银盐图书馆的具体来源条目。全部 168 个型号的入口合计 92 个手册、76 个来源条目。这里按型号计数，多款共用一份手册时分别计算。全文在线可访问性会随发布方变化。

本次复核范围为下列原图、曲线重复记录及倒易表。归一化中灰、图内预警阈值、手机 RGB 对光谱的近似和正像变换均含应用侧假设。其余单曲线采样未在本次逐点重审。

## 柯达分层及光谱

| 型号 | 文献与页码 | 光谱图曝光 | 图上 log sensitivity 顶/底刻度 | 蓝/绿/红样本数 | 密度蓝/绿/红样本数 |
|---|---|---:|---:|---:|---:|
| EKTAR 100 | E-4046，第 5 页 | 1/25 s | 3 / 0 | 23 / 39 / 27 | 53 / 53 / 53 |
| PORTRA 160 | E-4051，第 4 页 | 1/50 s | 3 / −1 | 26 / 40 / 37 | 53 / 53 / 53 |
| PORTRA 800 | E-4040，第 4 页，密度图取标准 EI 800 | 1/200 s | 4 / 0 | 31 / 43 / 44 | 53 / 53 / 53 |
| ULTRAMAX 400 | E-7023，第 4 页 | 1/100 s | 4 / 0 | 32 / 43 / 43 | 未收入 |

光谱合计 428 行，图测条件为日光、Status M、密度高于 D-min 0.2，各层在原图支持范围内按 5 nm 插值。分层密度合计 477 行，每层从 −6.5 到 +6.5 EV，每 0.25 EV 一个样本，三张图的 D 轴均为 0–4。EKTAR 100、PORTRA 160、PORTRA 800 的 log H 参照依次为 −0.84、−1.051、−1.74。原始图是连续曲线，CSV 行数是应用的重采样数量。复现命令为 `python tools/digitize_kodak.py <按文献ID命名的PDF目录>`。[EKTAR 100](https://kodakprofessional.com/sites/default/files/wysiwyg/pro/resources/e4046_ektar_100.pdf) · [PORTRA 160](https://www.kodakprofessional.com/sites/default/files/wysiwyg/pro/resources/e4051_Portra_160.pdf) · [PORTRA 800](https://kodakprofessional.com/sites/default/files/wysiwyg/pro/resources/e4040_portra_800.pdf) · [ULTRAMAX 400](https://125px.com/docs/film/kodak/E7023_max_400-2016.pdf)

## 相同及近似的图线

- FOMAPAN 100 Classic、200 Creative、400 Action 的手册首页均给出 5500 K、1/20 s、Microphen 20°C 的三条特性曲线。三份图中的第二条曲线形状极为接近，其显影时间依次为 7、6、9 分钟。现按三份 PDF 各自的轴和矢量轮廓独立读取 56 点。在原图相对曝光 log H = −2 处依次约为 1.39915、1.39935、1.39955 D，图示精度仅支持读到约 1.4 D。三款的 ISO 和倒易数据仍分别取自各自说明。复现命令为 `python tools/digitize_fomapan.py <按文献ID命名的PDF目录>`。[FOMAPAN 100](https://www.foma.cz/en/fomapan-100) · [FOMAPAN 200](https://www.foma.cz/en/fomapan-200) · [FOMAPAN 400](https://www.foma.cz/en/fomapan-400)
- KODACHROME 64 和 64 Professional 的 E-88、E-55 第 4 页在图形平移后使用相同的三条矢量路径。两份手册第 5 页的 200 与 200 Professional 也使用相同形状的图线。不同产品名称和文献 ID 保留，设置中展示图线来源关系。各来源分别读图和取轴时可有小的数字化差异。[E-88](https://125px.com/docs/film/kodak/e88-2009_06.pdf) · [E-55](https://125px.com/docs/film/kodak/e55-2009_06.pdf)

## 长曝光依据

- HARMAN Phoenix II《Technical Information》2025 年 7 月版第 2 页直接写出 `Ta = Tm^1.31`。1/10,000–1 s 内无需补偿；图的测得时间横轴到 50 s。Kentmere PAN 200 的 2025 年 3 月手册第 1 页给出 1.26 指数，横轴也到 50 s。[Phoenix II 原厂手册](https://www.harmanphoto.co.uk/amfile/file/download/file/1968/product/2208/) · [Kentmere PAN 200 原厂手册](https://www.ilfordphoto.com/wp/wp-content/uploads/2025/05/KENTMERE-PAN-200-260325.pdf)
- ILFORD 黑白胶片采用厂家 2023 年 12 月一页指南中的分型号指数。HP5 PLUS 的 10 s 示例为 20.4 s。该指南提醒极长曝光需试验，未定义统一的保证上限。本应用对所有幂律在测得时间超过 50 s 时显示“公式外推，建议试拍”。其中 ILFORD 的 50 s 是显示提示阈值。[ILFORD 长曝光指南](https://www.ilfordphoto.com/wp/wp-content/uploads/2024/05/Reciprocity-Failure-Compensation-v2.pdf)
- PORTRA 400 E-4050 给出的无需补偿范围为 1/10,000–1 s，延长曝光要求按拍摄条件试验。EKTACHROME E100 E-4000 的日光范围为 1/10,000–10 s，120 s 处注明 CC10R 滤镜。应用仅报告已给出的范围或滤镜提示，该滤镜指示不产生一个新快门时间。[PORTRA 400](https://kodakprofessional.com/sites/default/files/wysiwyg/pro/resources/e4050_portra_400.pdf) · [E100](https://kodakprofessional.com/sites/default/files/wysiwyg/pro/resources/e4000_ektachrome_100.pdf)

VERITA 200D 使用柯达 H-1-5206 第 4 页的绿层密度，横轴零点取图中相机曝光档的零点。重建脚本为 `tools/digitize_verita.py`。[柯达 VERITA 200D 手册](https://www.kodak.com/content/pdfs/motion/KODAK-VERITA-200D-5206-7206-technical-information.pdf)

## 通用曲线手册追加审计

原先采用通用曲线的48款逐款记录于 `tools/spektrafilm_sources/manual-audit.json`，含文献SHA-256、检查页码、可用图表和不足原因。PAN F PLUS第4页ILFOTEC HC（1+31）4分钟、20°C视觉密度曲线按栅格提取87点，相对log曝光中灰按趾部高于底密度0.10D后上移3⅓档放置。SUPERIA X-TRA 800第4页CN-16、日光1/125s、Status M绿层提取77点，中灰沿用其他富士曲线H=10/ISO lx·s假设。两款上下限均取已绘曲线首尾内收0.10D；图右端不等于实测饱和或完整动态范围。另保存SUPERIA X-TRA 800三层特性、三色及cyan感光谱、片基与中灰总染料密度，原图无单染料消光谱。

复现 `python tools/audit_manual_sources.py --cache <silvergrain-research目录> --integrate`，再运行 `python tools/export_web_data.py`。HR-50的FX-39红色成品密度曲线LogE轴没有数字刻度，不视为可标定EV。Phoenix II、CHS100II及部分Rollei有感色图但缺D-logH；Lomography示例色卡未经光源和扫描标定，不用于反演。余下46款继续标记通用响应；缓存手册缺图不代表该胶片永无其它资料。

目录旧称 FUJICOLOR NPS 160 NPC 对应手册为 FUJICOLOR 160 NPC Professional。其 LUT 使用 NPC 的密度曲线；选择 NPS 乳剂时应选其单列型号。[富士 160 NPC 手册](https://asset.fujifilm.com/www/jp/files/2024-04/6cc43e223cf542ef0ecc6eed551459ee/datasheet_160npc_01.pdf)
