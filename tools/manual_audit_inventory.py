"""Audit the formerly generic 48 catalogue entries against cached source manuals.
Run before or after export_web_data.py. Does not infer absent measurements.
"""

import json, hashlib, os, argparse
from pathlib import Path
import pymupdf as fitz

ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser()
p.add_argument(
    "--cache",
    type=Path,
    default=Path(os.environ.get("TEMP", ".")) / "silvergrain-research",
)
args = p.parse_args()
films = [
    x
    for x in json.loads((ROOT / "web/data/films.json").read_text(encoding="utf-8"))
    if "通用近似" in x.get("info", "")
    or x["name"] in ["PAN F PLUS", "SUPERIA X-TRA 800"]
]
entries = []
for f in films:
    doc = f["document"]
    file = args.cache / "docs" / (doc + ".pdf")
    d = fitz.open(file)
    r = dict(
        film=f["name"],
        id=f["id"],
        brand=f["brand"],
        iso=f["iso"],
        document_id=doc,
        source_url=f["url"],
        pdf_sha256=hashlib.sha256(file.read_bytes()).hexdigest(),
        pdf_pages=len(d),
        text_checked_pages=list(range(1, len(d) + 1)),
        render_checked_pages=list(range(1, len(d) + 1)) if len(d) < 10 else [1, 2, 3],
        status="insufficient",
        density_available=False,
        spectral_available=False,
        action="保留通用近似，不标为厂家曝光密度/光谱重建。",
    )
    if doc == "11731318":
        r.update(
            status="digitized-density",
            density_available=True,
            spectral_available=True,
            chart_pages=[1, 4],
            numeric_source="panf-plus.json",
            reason="第4页相对log曝光-视觉密度曲线有ILFOTEC HC1+31/4min/20°C条件，已提取87点。第1页2850K楔形光谱纵轴仅Sensitivity，不能直接认定log敏感度。",
            action="已接入density.csv；中灰放置与亮端范围含应用假设。",
        )
    elif doc == "0395e7b5":
        r.update(
            status="digitized-density-and-spectral",
            density_available=True,
            spectral_available=True,
            chart_pages=[4],
            numeric_source="superia-xtra800.json",
            reason="第4页三层Status M密度、RGB与cyan感光谱、片基和中灰总染料密度可读取；无单染料消光谱。",
            action="绿层已接入density.csv；三层/光谱/片基/中灰数据供Spektrafilm派生，donor单染料谱须披露。",
        )
    elif doc == "888806de":
        r.update(
            status="partial-uncalibrated-density",
            spectral_available=True,
            chart_pages=[1],
            reason="FX-39红色为Speed Boost成品，蓝色为技术原基材，不可混用。LogD纵轴有刻度，LogE横轴无数值/刻度间隔，无法转为标定EV。光谱图LogD为楔形密度，不等同阈值曝光的log敏感度。",
            action="不替换通用EV曲线，需横轴标定图或实测阶梯曝光。",
        )
    elif doc == "9228a1f0":
        r.update(
            status="spectral-shape-only",
            spectral_available=True,
            chart_pages=[1],
            reason="Spectral Response有波长和单黑白感光形状，无纵轴数值/单位。第2页显影表，乳剂结构图不是曝光密度曲线。",
        )
    elif doc == "42ab5b1d":
        r.update(
            status="spectral-only",
            spectral_available=True,
            chart_pages=[2],
            reason="第2页ADOX Microfilm光谱图有400–650nm和+1/−1刻度，未明确纵轴定义/成品阈值。全文无D-logH；提及characteristics的文字不足以生成曲线。",
        )
    elif doc == "c1be7692":
        r.update(
            status="spectral-only",
            spectral_available=True,
            chart_pages=[2],
            reason="D55楔形RGB Relative Sensitivity纵轴未标log。第4/5页density是扫描软件滑块，不是D-logH；无单染料消光谱。",
        )
    elif f["brand"] == "Lomography":
        r.update(
            reason="英文Film Guide第1–3页为包装/示例照片、ISO、推拉或显影时间表，后续为翻译。全页文本无数值轴特性/光谱；色卡照片未经光源、曝光和扫描标定，无法反演成测量数据。"
        )
    elif "Washi" in f["brand"]:
        r.update(
            reason="全部单/双页Data Sheet给出ISO、用途、显影、装片、扫描和文字感色范围，无定量D-logH。Spectral sensitivity为定性描述/范围。特殊X光、纸基、红外乳剂不可据显影关键字当作普通全色负片。"
        )
    elif f["brand"] == "Rollei":
        if doc in [
            "b0db43fb",
            "e81fad3e",
            "b26905a3",
            "e7c703af",
            "c3600619",
            "d4804249",
        ]:
            r.update(
                status="spectral-only",
                spectral_available=True,
                chart_pages=[3],
                reason="第3页有Spectral Sensitivity形状/波长轴，无D-logH及对应冲洗条件。Reciprocity effect是时间补偿，不是密度图。",
            )
        else:
            r.update(
                reason="六页含乳剂规格、冲洗时间、倒易及使用说明，无D-logH；当前缓存第3页也无定量感光谱，文字感色不能当测量光谱。"
            )
    elif f["brand"] == "Kentmere":
        r.update(
            reason="四页含曝光指数、倒易补偿、显影、温度换算和定影，无D-logH/光谱。首页上升曲线为长曝光时间补偿。"
        )
    elif f["name"] in ["PAN 100", "PAN 400"]:
        r.update(
            reason="共用9页手册，含曝光、长曝光补偿、显影和温度换算；没有各型号D-logH或定量光谱。"
        )
    elif f["brand"] == "Ferrania":
        r.update(
            reason="Best Practices/Processing Chart为拍摄与显影建议，无D-logH或光谱，显影表不能反演成密度曲线。"
        )
    elif doc == "43bc9677":
        r.update(
            reason="六页中文企业标准扫描件为规格、检验、包装规则，无曝光阶梯对应点/D-logH。速度、反差、雾度限值不足以重建趾部和肩部。"
        )
    else:
        raise ValueError((f["brand"], f["name"]))
    entries.append(r)
assert len(entries) == 48
result = dict(
    schemaVersion=1,
    audited_date="2026-11-17",
    scope="Previously generic 48 film entries; 47 unique cached manuals.",
    method="Complete text inspection. All pages visually inspected for technical documents; English pages1–3 visually inspected for multilingual Lomo guides. No claim of visual review of every translated page.",
    source_repository="https://github.com/fuduji000-netizen/silvergrain-library",
    source_commit="5c29192dbe0188995249adda0a70390369bd1198",
    summary=dict(
        entries=48,
        promoted_density_curves=2,
        remaining_generic_density=46,
        numeric_sources=["panf-plus.json", "superia-xtra800.json"],
    ),
    entries=entries,
)
(ROOT / "tools/spektrafilm_sources/manual-audit.json").write_text(
    json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
)
print("Saved audit", len(entries), "entries")
