"""Bake LightData RGB8 tables using the pinned, unmodified spektrafilm runtime.
Usage: python tools/bake_spektrafilm.py --upstream PATH [--only PROFILE]
Requires the upstream core runtime dependencies. See web/luts/README.md.
"""
import argparse, copy, hashlib, json, subprocess, sys
from pathlib import Path
import numpy as np

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/"web/luts"
UPSTREAM_COMMIT="3bb2c2d2801ff68b92019cf1dbcbb133d60832bc"
URL="https://github.com/andreavolpato/spektrafilm"
MAPPING={
"fujifilm_c200":("FUJICOLOR C200",200),
"fujifilm_pro_400h":("PRO 400H",400),
"fujifilm_xtra_400":("SUPERIA X-TRA 400",400),
"fujifilm_provia_100f":("PROVIA 100F",100),
"fujifilm_velvia_100":("VELVIA 100",100),
"kodak_ektachrome_100":("EKTACHROME E100",100),
"kodak_kodachrome_64":("KODACHROME 64",64),
"kodak_ektar_100":("EKTAR 100",100),
"kodak_gold_200":("GOLD 200",200),
"kodak_portra_160":("PORTRA 160",160),
"kodak_portra_400":("PORTRA 400",400),
"kodak_portra_800":("PORTRA 800",800),
"kodak_portra_800_push1":("PORTRA 800",1600),
"kodak_portra_800_push2":("PORTRA 800",3200),
"kodak_ultramax_400":("ULTRAMAX 400",400),
"kodak_verita_200d":("VERITA 200D",200),
"kodak_vision3_50d":("VISION3 50D",50),
"kodak_vision3_200t":("VISION3 200T",200),
"kodak_vision3_250d":("VISION3 250D",250),
"kodak_vision3_500t":("VISION3 500T",500),
}

def configure(p):
    p.io.input_color_space="sRGB"
    p.io.input_cctf_decoding=False
    p.io.output_color_space="sRGB"
    p.io.output_cctf_encoding=True
    p.io.full_image=True
    p.io.upscale_factor=1.
    p.io.crop=False
    p.camera.auto_exposure=False
    p.camera.exposure_compensation_ev=0.
    p.debug.lut_mode=True
    p.debug.deactivate_spatial_effects=True
    p.debug.deactivate_stochastic_effects=True
    p.settings.use_enlarger_lut=False
    p.settings.use_scanner_lut=False
    p.print_render.glare.active=False
    return p

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--upstream",type=Path,required=True)
    ap.add_argument("--only",default="")
    ap.add_argument("--derived",action="store_true")
    a=ap.parse_args()
    commit=subprocess.check_output(["git","-C",str(a.upstream),"rev-parse","HEAD"],text=True).strip()
    assert commit==UPSTREAM_COMMIT,commit
    sys.path.insert(0,str(a.upstream/"src"))
    from spektrafilm import init_params, simulate
    profiles={p.stem:json.loads(p.read_text()) for p in (a.upstream/"src/spektrafilm/data/profiles").glob("*.json")}
    # meshgrid indexing produces an image with R varying fastest, B slowest.
    b,g,r=np.meshgrid(np.linspace(0,1,33),np.linspace(0,1,33),np.linspace(0,1,33),indexing="ij")
    cube=.18*(2**(9*np.stack([r,g,b],axis=-1))-1)
    image=cube.reshape(33*33,33,3)
    OUT.mkdir(parents=True,exist_ok=True)
    manifest_path=OUT/"index.json"
    manifest=json.loads(manifest_path.read_text()) if manifest_path.exists() else {
        "schemaVersion":1,"size":33,"format":"RGB8","order":"R-fastest,G-next,B-slowest",
        "input":"linear sRGB; u=log2(1+max(0,x)/0.18)/9; clamp u to [0,1]",
        "output":"sRGB display encoded","upstream":URL,"commit":commit,
        "license":"CC-BY-SA-4.0","entries":[]}
    audits=[]
    tasks=[]
    for stock,raw in profiles.items():
        info=raw["info"]
        if info["stage"]!="filming":
            audits.append({"profile":stock,"status":"print-only","use":info,"reason":"作为输出介质组合；不作为拍摄胶片"})
            continue
        name,ei=MAPPING[stock]
        if info["type"]=="positive":
            outputs=[None]
        elif info["use"]=="cine":
            outputs=[s for s,v in profiles.items() if v["info"]["stage"]=="printing" and v["info"]["use"]=="cine"]
        else:
            outputs=[s for s,v in profiles.items() if v["info"]["stage"]=="printing" and v["info"]["support"]=="paper"]
        for paper in outputs:
            tasks.append((stock,name,ei,paper,None))
        audits.append({"profile":stock,"film":name,"iso":ei,"status":"upstream","outputs":outputs,"metadata":raw["metadata"]})
    local=json.loads((ROOT/"web/data/films.json").read_text(encoding="utf8"))
    official={v[0] for v in MAPPING.values()}
    materials = json.loads((ROOT/"tools/spektrafilm_sources/derived_materials.json").read_text(encoding="utf8"))
    color_sources = {}
    for path in sorted((ROOT/"tools/spektrafilm_sources").glob("*.json")):
        source = json.loads(path.read_text(encoding="utf8"))
        if "spectral_sensitivity" in source and "density_curves" in source:
            assert source["stock"] not in color_sources
            color_sources[source["stock"]] = source
    for f in local:
        if f["name"] in official:
            continue
        material = materials[f["name"]]
        decision = {"film":f["name"], "source":f["url"], "document":f["document"], "densitySource":f["info"]}
        if material["measuredDensity"]:
            donor = material["donor"]
            special = color_sources.get(f["name"])
            paper = None if material["bw"] or material["polarity"] == "positive" else profiles[donor]["info"]["target_print"]
            note = derive_description(material, donor, special)
            decision.update(status="derived", donor=donor, reason=note, bw=material["bw"])
            if a.derived:
                tasks.append((donor, f["name"], f["iso"], paper, (f, material, special)))
        else:
            decision.update(status="insufficient",reason="仅有类别曲线或型号参数，缺少可追溯的本型号曝光密度曲线")
        audits.append(decision)

    wanted = set()
    updated = {e["file"]:e for e in manifest["entries"]}
    for stock,name,ei,paper,derived in tasks:
        output_id = paper or ("silver_positive" if derived and derived[1]["bw"] else "D50")
        slug = ("derived_"+hashlib.sha256(name.encode()).hexdigest()[:12] if derived else stock)+"__"+output_id
        filename = slug+".rgb"
        wanted.add(filename)
        if a.only and a.only not in (stock,name,slug):
            continue
        spec = json.dumps([stock,paper,derived],ensure_ascii=False,sort_keys=True).encode()
        key = hashlib.sha256(Path(__file__).read_bytes()+spec).hexdigest()
        old = updated.get(filename,{})
        if old.get("bakeKey")==key and (OUT/filename).is_file() and hashlib.sha256((OUT/filename).read_bytes()).hexdigest()==old["validation"]["sha256"]:
            continue
        print("BAKE",slug,flush=True)
        p = configure(init_params(stock,paper or "kodak_portra_endura"))
        p.io.scan_film = paper is None
        p.film.info.viewing_illuminant = "D50"
        if paper:
            p.print.info.viewing_illuminant = "K75P" if profiles[paper]["info"]["use"]=="cine" else "D50"
        if derived:
            f, material, special = derived
            derive(p, f, material, special)
        # The last row is a fixed set of independent off-lattice reference colours.
        # LUT mode disables neighbour-dependent operations, so one simulation can
        # produce the lattice plus these independent comparison samples.
        probes = reference_samples()
        inputs = np.concatenate([image,probes[None,:,:]])
        if derived and material["bw"]:
            result = silver_positive(inputs, p, material["polarity"])
        else:
            result = simulate(inputs,p)
        assert result.shape==inputs.shape and np.isfinite(result).all(), slug
        output = result[:-1]
        encoded=np.round(np.clip(output,0,1)*255).astype(np.uint8)
        data=encoded.tobytes()
        assert len(data)==33**3*3
        diagonal=encoded.reshape(33,33,33,3)[np.arange(33),np.arange(33),np.arange(33)]
        assert diagonal[-1].mean()-diagonal[0].mean()>12, (slug,diagonal)
        stats={"grayAxisRGB8":diagonal.tolist(),"grayLumaReversals":int(np.sum(np.diff(diagonal.mean(axis=1))<-.5)),
               "minRGB8":encoded.reshape(-1,3).min(axis=0).tolist(),"maxRGB8":encoded.reshape(-1,3).max(axis=0).tolist(),
               "sha256":hashlib.sha256(data).hexdigest()}
        from scipy.interpolate import RegularGridInterpolator
        encoded_grid = encoded.reshape(33,33,33,3)
        interpolator = RegularGridInterpolator([np.arange(33)]*3,encoded_grid)
        positions = np.clip(np.log2(1+probes/.18)*(32/9),0,32)
        errors = np.abs(interpolator(positions[:,::-1]) - np.clip(result[-1],0,1)*255)
        stats["referenceRGB8"] = {
            "samples": len(probes), "meanAbsError":round(float(errors.mean()),3),
            "maxAbsError":round(float(errors.max()),3),
        }
        medium = profiles[paper]["info"]["name"] if paper else ("中性银影正像" if derived and material["bw"] else "D50 直接透射")
        entry={"film":name,"file":filename,"kind":"derived" if derived else "upstream","bakeKey":key,
               "profile":stock,"printProfile":paper,"iso":ei,"size":33,"bytes":len(data),
               "default":bool(derived) or paper==profiles[stock]["info"].get("target_print") or paper is None,
               "description":("派生近似 · "+name if derived else "Spektrafilm · "+profiles[stock]["info"]["name"])+" → "+medium+
                   (" · K75P" if paper and profiles[paper]["info"]["use"]=="cine" else " · D50")+
                   (" · Push 1" if stock.endswith("push1") else " · Push 2" if stock.endswith("push2") else ""),
               "source":URL+"/blob/"+commit+"/src/spektrafilm/data/profiles/"+stock+".json",
               "approximation":derive_description(material,stock,special) if derived else "RGB输入光谱重建；固定冲印，关闭空间与随机效果",
               "validation":stats}
        if derived:
            entry["manual"]=f["url"]
            entry["mono"]=material["bw"]
        (OUT/filename).write_bytes(data)
        updated[filename]=entry
        manifest["entries"]=list(updated.values())
        manifest_path.write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding="utf8")
    if not a.only and a.derived:
        manifest["entries"]=[updated[k] for k in sorted(wanted)]
        manifest_path.write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding="utf8")
        for k in updated.keys()-wanted:
            if Path(k).name==k and k.endswith(".rgb"):
                (OUT/k).unlink(missing_ok=True)
    (OUT/"coverage.json").write_text(json.dumps(audits,ensure_ascii=False,indent=2),encoding="utf8")


def reference_samples():
    # Six off-grid intensities, then reproducible chromatic samples typical of a
    # linear camera input. Compare all three channels before the UI's EV mapping.
    neutral = np.repeat(np.array([.009,.045,.18,.36,.72,4.0])[:,None],3,axis=1)
    colors = np.random.default_rng(160).uniform(.025,1.8,(27,3))
    return np.vstack([neutral,colors])


def derive_description(material, donor, special):
    if special:
        return (special["stock"]+" 原厂 RGB 感光谱与分层密度，印刷图采样的中性染料总谱与 D-min；"
                "各染料分谱借用 "+donor+" 并按中性谱约束；三通道模型未独立模拟青敏层；"
                "中灰近似，未测谱尾留空或沿用底材")
    if material["bw"]:
        spectrum = ("本型号手册单色感光响应（"+material["monoSensitivity"]["scale"]+"）") if material.get("monoSensitivity") else "CIE 明视觉亮度作为感光响应近似"
        return "手册密度曲线；"+spectrum+"；中性银影正像与中灰近似"
    return "手册单通道或平均密度；感光谱与染料分谱借用 "+donor+"；中灰及层间响应近似"


def interpolate(points, grid, **options):
    axis, values = np.asarray(points,dtype=float).T
    return np.interp(grid,axis,values,**options)


def derive(p, f, material, special):
    from spektrafilm import simulate
    p.film = copy.deepcopy(p.film)
    data = p.film.data
    dots = np.asarray(material.get("exposureDensity",{}).get("points",f["dots"]))
    x = dots[:,0]*np.log10(2)
    data.log_exposure = x
    data.density_curves = np.repeat(dots[:,1,None],3,axis=1)
    data.hanatos2025_adaptation_window_params = np.array([])
    data.hanatos2025_adaptation_surface_params = np.array([])
    p.settings.apply_hanatos2025_adaptation_window = False
    p.settings.apply_hanatos2025_adaptation_surface = False
    p.settings.neutral_print_filters_from_database = False
    p.film_render.dir_couplers.active = False
    p.film.info.stock = "lightdata_"+hashlib.sha256(f["name"].encode()).hexdigest()[:12]
    p.film.info.name = f["name"]+" (LightData derived)"
    wavelengths = data.wavelengths
    if special:
        channels = ("red","green","blue")
        curves = special["density_curves"]
        lo = max(curves[c][0][0] for c in channels)
        hi = min(curves[c][-1][0] for c in channels)
        axis = np.linspace(lo, hi, 181)
        data.log_exposure = axis - special["gray_log10_H"]
        data.density_curves = np.stack([interpolate(curves[c],axis) for c in channels],axis=-1)
        data.log_sensitivity = np.stack([interpolate(special["spectral_sensitivity"][c], wavelengths,
                                                     left=-12,right=-12) for c in channels],axis=-1)
        dye = special["spectral_dye_density"]
        valid = (wavelengths>=max(dye[k][0][0] for k in dye)) & (wavelengths<=min(dye[k][-1][0] for k in dye))
        base = interpolate(dye["minimum"],wavelengths)
        mid = interpolate(dye["midscale"],wavelengths)
        gray = np.array([interpolate(curves[c],special["gray_log10_H"]) for c in channels])
        net = gray-np.min(data.density_curves,axis=0)
        # The neutral spectrum constrains the sum of three unknown dye spectra.
        # Preserve donor dye proportions, solve one common multiplier per wavelength.
        weights = data.channel_density @ net
        data.channel_density[valid] *= ((mid[valid]-base[valid])/np.maximum(weights[valid],1e-6))[:,None]
        data.base_density[valid] = base[valid]
        data.midscale_neutral_density[valid] = mid[valid]
    elif material["bw"]:
        spectrum = material.get("monoSensitivity")
        if spectrum:
            response = interpolate(spectrum["points"],wavelengths,left=-12,right=-12)
            response -= response.max()
        else:
            from colour import MSDS_CMFS, SpectralShape
            v = MSDS_CMFS["CIE 1931 2 Degree Standard Observer"].copy().align(SpectralShape(380,780,5)).values[:,1]
            response = np.log10(np.maximum(np.interp(wavelengths,np.arange(380,781,5),v,left=0,right=0),1e-12))
        data.log_sensitivity = np.repeat(response[:,None],3,axis=1)
        data.channel_density = np.ones((len(wavelengths),3))/3
        data.base_density = np.full(len(wavelengths),dots[:,1].min())
        data.midscale_neutral_density = np.full(len(wavelengths),np.interp(0,dots[:,0],dots[:,1]))
        p.film.info.channel_model = "bw"
    data.density_curves -= np.min(data.density_curves,axis=0)
    data.density_curves_layers = data.density_curves[:,None,:]
    # Profile sensitivities in spektrafilm normally arrive pre-balanced. Calibrate
    # the actual RGB -> film exposure path so 18% input maps to log H relative 0.
    p.taps.collect = "log_e_film"
    gray = simulate(np.full((1,1,3),.18),p)[0,0]
    data.log_sensitivity -= gray
    p.taps.collect = None


def silver_positive(image, p, polarity):
    from spektrafilm import simulate
    p.taps.collect="cmy_film"
    density=simulate(image,p).mean(axis=-1)
    gray=simulate(np.full((1,1,3),.18),p).mean()
    # Display convention: reflectance scales with positive net density for a
    # negative and inverse density for reversal stock; anchor target gray to 18%.
    positive=.18 * 10**((density-gray)*(1 if polarity=="negative" else -1))
    srgb=np.where(positive<=.0031308,12.92*positive,1.055*positive**(1/2.4)-.055)
    return np.repeat(srgb[...,None],3,axis=2)


if __name__=="__main__": main()
