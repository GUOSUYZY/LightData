"""Digitize verified Kodak vector diagrams. Run with the extracted Silvergrain docs folder."""
import argparse, csv, math
from pathlib import Path
import pymupdf as pdf
import numpy as np

def trace(shape):
    points = []
    for item in shape["items"]:
        pts = [np.array((v.x, v.y)) for v in item[1:] if hasattr(v, "x")]
        if item[0] == "l":
            points.extend(pts)
        elif item[0] == "c":
            for t in np.linspace(0, 1, 24):
                points.append((1-t)**3*pts[0]+3*t*(1-t)**2*pts[1]+3*t*t*(1-t)*pts[2]+t**3*pts[3])
    return np.array(points)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("documents", type=Path)
    args = parser.parse_args()
    target = Path("app/src/main/resources/studio/lightdata")
    # Explicit diagram coordinates verified against the PDF axes, pages are one-based.
    sources = [
        ("EKTAR 100","01d0bf56",5,(74.19,326.06,274.65,477.54),[27,28,29], 3, 0, 25),
        ("PORTRA 800","287b9a0f",4,(350.17,313.67,550.94,465.37),[63,64,65], 4, 0, 200),
        ("ULTRAMAX 400","efa4e393",4,(76.62,371.03,277.57,522.61),[50,51,52], 4, 0, 100),
        ("PORTRA 160","482706f9",4,(73.75,347.98,274.23,499.57),[49,50,51], 3, -1, 50),
    ]
    with (target/"spectral.csv").open("w",encoding="utf-8",newline="") as f:
        w=csv.writer(f,lineterminator="\n")
        w.writerow(["stock","document_id","page","channel","wavelength_nm","log_sensitivity","conditions"])
        for stock,id,page,axis,curves,top,bottom,shutter in sources:
            p=pdf.open(args.documents/(id+".pdf"))[page-1]
            x0,y0,x1,y1=axis
            for channel,index in zip(["blue","green","red"],curves):
                a=trace(p.get_drawings()[index]);a=a[np.argsort(a[:,0])]
                nm=250+(a[:,0]-x0)/(x1-x0)*500
                log=top-(a[:,1]-y0)/(y1-y0)*(top-bottom)
                for wavelength in range(math.ceil(nm[0]/5)*5,math.floor(nm[-1]/5)*5+1,5):
                    w.writerow([stock,id,page,channel,wavelength,round(float(np.interp(wavelength,nm,log)),5),
                                f"1/{shutter} s; Status M; density 0.2 above D-min; log reciprocal exposure erg/cm2"])
    # The three R/G/B density traces share the same chart axes and gray reference.
    densities=[
        ("EKTAR 100","01d0bf56",5,(81.19,77.42,265.70,261.91),[11,12,13],-3,2,-.84),
        ("PORTRA 160","482706f9",4,(81.45,102.12,266.01,286.60),[31,32,33],-4,1,-1.051),
        ("PORTRA 800","287b9a0f",4,(81.64,74.47,266.17,258.89),[10,11,12],-4,1,-1.74),
    ]
    with (target/"layer-density.csv").open("w",encoding="utf8",newline="") as f:
        w=csv.writer(f,lineterminator="\n")
        w.writerow(["stock","document_id","page","channel","ev","density","conditions"])
        for stock,id,page,axis,curves,lo,hi,gray in densities:
            p=pdf.open(args.documents/(id+".pdf"))[page-1];x0,y0,x1,y1=axis
            # Curves sorted by density at their left edge identify blue, green, red.
            arrays=[trace(p.get_drawings()[i]) for i in curves]
            assert all(len(a)>3 for a in arrays),stock
            arrays.sort(key=lambda a:a[0,1])
            for channel,a in zip(["blue","green","red"],arrays):
                a=a[np.argsort(a[:,0])]
                ev=(lo+(a[:,0]-x0)/(x1-x0)*(hi-lo)-gray)/math.log10(2)
                density=(y1-a[:,1])/(y1-y0)*4
                for stop in np.arange(math.ceil(ev[0]*4)/4,math.floor(ev[-1]*4)/4+.01,.25):
                    w.writerow([stock,id,page,channel,round(float(stop),4),round(float(np.interp(stop,ev,density)),5),
                                "daylight; C-41; Status M; EV=(log H-log H reference)/log10(2)"])
if __name__=="__main__":
    main()
