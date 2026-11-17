import {
  clamp,
  defaultResponse,
  linearToSrgb,
  layerPositive,
  spectralMatrix,
  luma,
  positive,
  renderImage,
  srgbToLinear,
} from "./core.js";

const EDGE = 33,
  BYTES = 3 * EDGE ** 3,
  MAX_CACHED = 16;
const linear = Float64Array.from({ length: 256 }, (_, i) =>
  srgbToLinear(i / 255),
);
const weights = [0.2126, 0.7152, 0.0722];
const luminance = (r, g, b) => weights[0] * r + weights[1] * g + weights[2] * b;

export class FilmLutLibrary {
  constructor(manifest = "./luts/index.json") {
    this.url = new URL(
      manifest,
      globalThis.document?.baseURI || globalThis.location?.href,
    );
    this.files = new Map();
  }
  entries() {
    if (!this.manifest)
      this.manifest = fetch(this.url)
        .then(async (res) => {
          if (!res.ok) throw new Error("LUT index HTTP " + res.status);
          const { entries } = await res.json();
          if (!Array.isArray(entries)) throw new Error("Invalid LUT index");
          return entries;
        })
        .catch((error) => {
          this.manifest = null;
          throw error;
        });
    return this.manifest;
  }
  load(entry) {
    const url = new URL(entry.file, this.url).href;
    const cached = this.files.get(url);
    if (cached) {
      this.files.delete(url);
      this.files.set(url, cached);
      return cached;
    }
    const pending = fetch(url)
      .then(async (res) => {
        if (!res.ok) throw new Error("LUT HTTP " + res.status);
        const lut = new Uint8Array(await res.arrayBuffer());
        checkLut(lut);
        if (entry.validation?.sha256 && globalThis.crypto?.subtle) {
          const bytes = new Uint8Array(await crypto.subtle.digest("SHA-256", lut));
          const digest = Array.from(bytes, n => n.toString(16).padStart(2, "0")).join("");
          if (digest !== entry.validation.sha256) throw new Error("LUT checksum mismatch");
        }
        return lut;
      })
      .catch((error) => {
        if (this.files.get(url) === pending) this.files.delete(url);
        throw error;
      });
    this.files.set(url, pending);
    if (this.files.size > MAX_CACHED)
      this.files.delete(this.files.keys().next().value);
    return pending;
  }
}

function checkLut(lut) {
  if (!(lut instanceof Uint8Array) || lut.length !== BYTES)
    throw new Error("Expected 33³ RGB8 LUT");
}

function toneTable({
  film,
  lut,
  cameraEv = 0,
  targetEv = 0,
  response = defaultResponse,
}) {
  const table = new Float32Array(4 * 256),
    neutral = new Float64Array(3);
  for (let i = 0; i < 256; i++) {
    const ev = response[i] + (cameraEv - targetEv);
    table[4 * i] = 0.18 * 2 ** clamp(ev, -80, 80);
    const exposure = table[4 * i];
    sample(lut, exposure, exposure, exposure, neutral);
    table[4 * i + 3] = luminance(
      srgbToLinear(neutral[0]),
      srgbToLinear(neutral[1]),
      srgbToLinear(neutral[2]),
    );
    table[4 * i + 1] = positive(film, ev);
    table[4 * i + 2] = ev < film.low ? -1 : ev > film.high ? 1 : 0;
  }
  return table;
}

// Storage order is red-fastest. Interpolation takes place in the LUT's encoded output space.
function sample(lut, r, g, b, out) {
  const cr = clamp((Math.log2(1 + r / 0.18) * 32) / 9, 0, 32);
  const cg = clamp((Math.log2(1 + g / 0.18) * 32) / 9, 0, 32);
  const cb = clamp((Math.log2(1 + b / 0.18) * 32) / 9, 0, 32);
  const ir = Math.min(31, cr | 0),
    ig = Math.min(31, cg | 0),
    ib = Math.min(31, cb | 0);
  const fr = cr - ir,
    fg = cg - ig,
    fb = cb - ib;
  const p = 3 * (ir + EDGE * (ig + EDGE * ib)),
    row = 3 * EDGE,
    plane = row * EDGE;
  for (let c = 0; c < 3; c++) {
    const i = p + c;
    const a = lut[i] * (1 - fr) + lut[i + 3] * fr;
    const b = lut[i + row] * (1 - fr) + lut[i + row + 3] * fr;
    const d = lut[i + plane] * (1 - fr) + lut[i + plane + 3] * fr;
    const e = lut[i + plane + row] * (1 - fr) + lut[i + plane + row + 3] * fr;
    out[c] =
      ((a * (1 - fg) + b * fg) * (1 - fb) + (d * (1 - fg) + e * fg) * fb) / 255;
  }
}

function readable(image) {
  if (image.data) return image;
  const ctx = image.getContext("2d");
  if (ctx) return ctx.getImageData(0, 0, image.width, image.height);
  const scratch =
    typeof OffscreenCanvas !== "undefined"
      ? new OffscreenCanvas(image.width, image.height)
      : document.createElement("canvas");
  scratch.width = image.width;
  scratch.height = image.height;
  const copy = scratch.getContext("2d");
  copy.drawImage(image, 0, 0);
  return copy.getImageData(0, 0, image.width, image.height);
}

function nativeCPU(source, options) {
  const image = readable(source);
  const { lut, preview, film, style = "color", stripes = true } = options;
  if (!lut || !preview || !film) return renderImage(image, options);
  checkLut(lut);
  const { data, width, height } = image;
  const out = new Uint8ClampedArray(data.length),
    rgb = new Float64Array(3),
    table = toneTable(options);
  const low = positive(film, film.low),
    high = positive(film, film.high);
  for (let i = 0; i < data.length; i += 4) {
    if (!data[i + 3]) continue;
    const p = 4 * luma(data[i], data[i + 1], data[i + 2]);
    const r = linear[data[i]],
      g = linear[data[i + 1]],
      b = linear[data[i + 2]];
    const y = luminance(r, g, b),
      exposure = table[p];
    let target = table[p + 1];
    const gain = y > 0 ? exposure / y : 0;
    sample(
      lut,
      y ? r * gain : exposure,
      y ? g * gain : exposure,
      y ? b * gain : exposure,
      rgb,
    );
    const red = rgb[0],
      green = rgb[1],
      blue = rgb[2];
    const limit = table[p + 2];
    if (r !== g || g !== b) {
      const colorY = luminance(
        srgbToLinear(red),
        srgbToLinear(green),
        srgbToLinear(blue),
      );
      const neutralY = table[p + 3];
      // A black LUT toe has no meaningful hue ratio. The equal offset keeps it finite.
      target = clamp(
        (target * (colorY + 0.0001)) / (neutralY + 0.0001),
        limit < 0 ? 0 : limit > 0 ? high : low,
        limit > 0 ? 1 : limit < 0 ? low : high,
      );
    }
    if (style === "aero")
      rgb.set([Math.max(green, red * 0.65), red * 0.8, blue * 0.72]);
    if (style === "purple")
      rgb.set([
        Math.max(red, green * 0.86),
        blue * 0.72,
        Math.max(blue, green * 0.93),
      ]);
    for (let c = 0; c < 3; c++) rgb[c] = srgbToLinear(rgb[c]);
    const mapped = luminance(rgb[0], rgb[1], rgb[2]);
    // Move on the constant-luminance plane towards gray until all channels fit the display.
    let sat = mapped > 0 && style !== "bw" ? target / mapped : 0;
    const min = Math.min(rgb[0], rgb[1], rgb[2]),
      max = Math.max(rgb[0], rgb[1], rgb[2]);
    if (min < mapped) sat = Math.min(sat, target / (mapped - min));
    if (max > mapped) sat = Math.min(sat, (1 - target) / (max - mapped));
    const x = (i / 4) % width,
      yy = Math.floor(i / 4 / width);
    const phase = limit > 0 ? x + yy : x - yy;
    const stripe = stripes && limit && ((phase % 12) + 12) % 12 < 3;
    for (let c = 0; c < 3; c++) {
      let value = linearToSrgb(clamp(target + sat * (rgb[c] - mapped), 0, 1));
      if (stripe)
        value =
          0.18 * value +
          0.82 * (limit > 0 ? [1, 0.22, 0.2] : [0.12, 0.86, 0.52])[c];
      out[i + c] = Math.round(255 * value);
    }
    out[i + 3] = data[i + 3];
  }
  return { data: out, width, height };
}

function dimensions(image, scale = 1) {
  if (!Number.isFinite(scale) || scale <= 0)
    throw new RangeError("Invalid preview scale");
  return {
    width: Math.max(1, Math.round(image.width * scale)),
    height: Math.max(1, Math.round(image.height * scale)),
  };
}

// Bilinear interpolation of premultiplied display values keeps transparent pano edges clean.
export function renderFilmLutCPU(image, options = {}) {
  const src = nativeCPU(image, options);
  const { width, height } = dimensions(src, options.scale);
  if (width === src.width && height === src.height) return src;
  const data = new Uint8ClampedArray(width * height * 4);
  for (let y = 0; y < height; y++)
    for (let x = 0; x < width; x++) {
      const u = clamp(((x + 0.5) * src.width) / width - 0.5, 0, src.width - 1),
        v = clamp(((y + 0.5) * src.height) / height - 0.5, 0, src.height - 1);
      const left = Math.floor(u),
        top = Math.floor(v),
        fx = u - left,
        fy = v - top;
      const out = 4 * (x + y * width);
      let a = 0,
        r = 0,
        g = 0,
        b = 0;
      for (let j = 0; j < 2; j++)
        for (let i = 0; i < 2; i++) {
          const offset =
            4 *
            (Math.min(left + i, src.width - 1) +
              Math.min(top + j, src.height - 1) * src.width);
          const weight =
            (i ? fx : 1 - fx) * (j ? fy : 1 - fy) * src.data[offset + 3];
          a += weight;
          r += weight * src.data[offset];
          g += weight * src.data[offset + 1];
          b += weight * src.data[offset + 2];
        }
      if (a) {
        data[out] = r / a;
        data[out + 1] = g / a;
        data[out + 2] = b / a;
        data[out + 3] = a;
      }
    }
  return { width, height, data };
}
const vertex = `#version 300 es
void main() {
  vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
  gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}`;
const fragment = `#version 300 es
precision highp float;
precision highp int;
precision highp sampler3D;
uniform sampler2D src;
uniform sampler2D tones;
uniform sampler3D cube;
uniform int style;
uniform int mode; // 0 native, 1 tone, 2 RGB layers, 3 LUT
uniform mat3 spectral;
uniform bool stripes;
uniform vec2 zoneBounds;
out vec4 result;
const vec3 W = vec3(.2126,.7152,.0722);
vec3 decode(vec3 c) { return mix(c/12.92,pow((c+.055)/1.055,vec3(2.4)),greaterThan(c,vec3(.04045))); }
vec3 encode(vec3 c) { return mix(c*12.92,1.055*pow(c,vec3(1.0/2.4))-.055,greaterThan(c,vec3(.0031308))); }
void main() {
  ivec2 p=ivec2(gl_FragCoord.xy);
  p.y=textureSize(src,0).y-1-p.y;
  vec4 srcPixel=texelFetch(src,p,0);
  if(srcPixel.a==0.) { result=vec4(0.); return; }
  vec3 inputRGB=decode(srcPixel.rgb);
  float y=dot(inputRGB,W);
  uvec3 codes=uvec3(floor(inputRGB*65535.+.5));
  uint y16=(codes.r*2126u+codes.g*7152u+codes.b*722u+5000u)/10000u;
  int gray=int(floor(encode(vec3(float(y16)/65535.)).r*255.+.5));
  vec4 t=texelFetch(tones,ivec2(gray,0),0);
  vec3 rgb=srcPixel.rgb;
  if(mode!=3) {
    if(style==1) rgb=vec3(float(gray)/255.);
    if(style==2) rgb=vec3(max(rgb.g,rgb.r*.65),rgb.r*.8,rgb.b*.72);
    if(style==3) rgb=vec3(max(rgb.r,rgb.g*.86),rgb.b*.72,max(rgb.b,rgb.g*.93));
    if(mode==2) {
      ivec3 c=ivec3(floor(255.*encode(clamp(spectral*inputRGB,0.,1.))+.5));
      rgb=encode(vec3(texelFetch(tones,ivec2(c.r,1),0).r,texelFetch(tones,ivec2(c.g,1),0).g,texelFetch(tones,ivec2(c.b,1),0).b));
    } else if(mode==1) {
      vec3 lin=decode(rgb);
      float lum=dot(lin,W);
      rgb=encode(clamp(lum>1.e-6 ? lin*t.y/lum : vec3(t.y),0.,1.));
    }
  } else {
  rgb=y>0. ? inputRGB*t.x/y : vec3(t.x);
  vec3 u=clamp(log2(1.+rgb/.18)/9.,0.,1.);
  rgb=texture(cube,(u*32.+.5)/33.).rgb;
  float target=t.y;
  if(srcPixel.r!=srcPixel.g || srcPixel.g!=srcPixel.b) {
    float colorY=dot(decode(rgb),W);
    target=clamp(target*(colorY+.0001)/(t.w+.0001),
                 t.z<0. ? 0. : t.z>0. ? zoneBounds.y : zoneBounds.x,
                 t.z>0. ? 1. : t.z<0. ? zoneBounds.x : zoneBounds.y);
  }
  if(style==2) rgb=vec3(max(rgb.g,rgb.r*.65),rgb.r*.8,rgb.b*.72);
  if(style==3) rgb=vec3(max(rgb.r,rgb.g*.86),rgb.b*.72,max(rgb.b,rgb.g*.93));
  rgb=decode(rgb);
  float mapped=dot(rgb,W);
  float sat=mapped>0. && style!=1 ? target/mapped : 0.;
  float lo=min(rgb.r,min(rgb.g,rgb.b)), hi=max(rgb.r,max(rgb.g,rgb.b));
  if(lo<mapped) sat=min(sat,target/(mapped-lo));
  if(hi>mapped) sat=min(sat,(1.-target)/(hi-mapped));
  rgb=encode(clamp(target+sat*(rgb-mapped),0.,1.));
  }
  if(stripes && t.z!=0. && mod(float(t.z>0. ? p.x+p.y : p.x-p.y),12.)<3.)
    rgb=mix(rgb,t.z>0. ? vec3(1.,.22,.2) : vec3(.12,.86,.52),.82);
  result=vec4(rgb,srcPixel.a);
}`;

function createGPU(canvas) {
  const gl = canvas.getContext("webgl2", {
    alpha: true,
    premultipliedAlpha: false,
    antialias: false,
    depth: false,
    stencil: false,
  });
  if (!gl) return null;
  function compile(type, text) {
    const shader = gl.createShader(type);
    gl.shaderSource(shader, text);
    gl.compileShader(shader);
    if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS))
      throw Error(gl.getShaderInfoLog(shader));
    return shader;
  }
  const program = gl.createProgram();
  for (const shader of [
    compile(gl.VERTEX_SHADER, vertex),
    compile(gl.FRAGMENT_SHADER, fragment),
  ]) {
    gl.attachShader(program, shader);
    gl.deleteShader(shader);
  }
  gl.linkProgram(program);
  if (!gl.getProgramParameter(program, gl.LINK_STATUS))
    throw Error(gl.getProgramInfoLog(program));
  gl.useProgram(program);
  gl.disable(gl.DITHER);
  gl.pixelStorei(gl.UNPACK_ALIGNMENT, 1);
  const textures = [];
  function texture(unit, name, target, filter) {
    const tex = gl.createTexture();
    textures.push(tex);
    gl.activeTexture(gl.TEXTURE0 + unit);
    gl.bindTexture(target, tex);
    gl.texParameteri(target, gl.TEXTURE_MIN_FILTER, filter);
    gl.texParameteri(target, gl.TEXTURE_MAG_FILTER, filter);
    gl.texParameteri(target, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(target, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    if (target === gl.TEXTURE_3D)
      gl.texParameteri(target, gl.TEXTURE_WRAP_R, gl.CLAMP_TO_EDGE);
    gl.uniform1i(gl.getUniformLocation(program, name), unit);
  }
  texture(0, "src", gl.TEXTURE_2D, gl.NEAREST);
  texture(1, "tones", gl.TEXTURE_2D, gl.NEAREST);
  gl.texStorage2D(gl.TEXTURE_2D, 1, gl.RGBA32F, 256, 2);
  texture(2, "cube", gl.TEXTURE_3D, gl.LINEAR);
  gl.texStorage3D(gl.TEXTURE_3D, 1, gl.RGB8, EDGE, EDGE, EDGE);
  const maxEdge = gl.getParameter(gl.MAX_TEXTURE_SIZE);
  const zoneBounds = gl.getUniformLocation(program, "zoneBounds");
  const style = gl.getUniformLocation(program, "style"),
    stripes = gl.getUniformLocation(program, "stripes");
  const modeUniform = gl.getUniformLocation(program, "mode");
  const matrixUniform = gl.getUniformLocation(program, "spectral");
  let previousKey, previousLut,
    w = 0,
    h = 0;
  return {
    draw(image, options) {
      if (gl.isContextLost() || image.width > maxEdge || image.height > maxEdge)
        return false;
      gl.activeTexture(gl.TEXTURE0);
      if (image.width !== w || image.height !== h) {
        w = canvas.width = image.width;
        h = canvas.height = image.height;
        gl.viewport(0, 0, w, h);
        gl.texImage2D(
          gl.TEXTURE_2D,
          0,
          gl.RGBA8,
          w,
          h,
          0,
          gl.RGBA,
          gl.UNSIGNED_BYTE,
          null,
        );
      }
      if (image.data) {
        gl.texSubImage2D(
          gl.TEXTURE_2D,
          0,
          0,
          0,
          w,
          h,
          gl.RGBA,
          gl.UNSIGNED_BYTE,
          image.data,
        );
      } else {
        gl.texSubImage2D(
          gl.TEXTURE_2D,
          0,
          0,
          0,
          gl.RGBA,
          gl.UNSIGNED_BYTE,
          image,
        );
      }
      const {film, response = defaultResponse, cameraEv = 0, targetEv = 0} = options;
      const delta = cameraEv - targetEv;
      const hasLayers = options.preview && (options.style || "color") === "color" &&
        ["red", "green", "blue"].every(c => film?.color?.density?.[c]?.length >= 2);
      const mode = options.lut && options.preview && film ? 3 : hasLayers ? 2 :
        options.preview || options.manual ? 1 : 0;
      const key = [film, response, delta, mode, options.preview, options.lut];
      if (!previousKey || key.some((v, i) => v !== previousKey[i])) {
        const table = mode === 3 ? toneTable(options) : new Float32Array(1024);
        if (mode !== 3) {
          for (let i = 0; i < 256; i++) {
            const ev = response[i] + delta;
            table[4*i+1] = options.preview && film ? positive(film, ev) :
              options.manual ? clamp(.18 * 2 ** ev, 0, 1) : linear[i];
            table[4*i+2] = film ? (ev < film.low ? -1 : ev > film.high ? 1 : 0) : 0;
          }
        }
        gl.activeTexture(gl.TEXTURE1);
        gl.texSubImage2D(gl.TEXTURE_2D, 0, 0, 0, 256, 1, gl.RGBA, gl.FLOAT, table);
        if (mode === 2) {
          const layers = new Float32Array(1024);
          const matrix = film.color.matrix || spectralMatrix(film.color.spectral);
          const columnMajor = new Float32Array(9);
          ["red", "green", "blue"].forEach((channel, c) => {
            for (let j = 0; j < 3; j++) columnMajor[3*j+c] = matrix[c][j];
            for (let i = 0; i < 256; i++) layers[4*i+c] = layerPositive(film.color.density[channel], response[i] + delta);
          });
          gl.uniformMatrix3fv(matrixUniform, false, columnMajor);
          gl.texSubImage2D(gl.TEXTURE_2D, 0, 0, 1, 256, 1, gl.RGBA, gl.FLOAT, layers);
        }
        if (mode === 3) gl.uniform2f(zoneBounds, positive(film, film.low), positive(film, film.high));
        gl.uniform1i(modeUniform, mode);
        previousKey = key;
      }
      if (mode === 3 && previousLut !== options.lut) {
        gl.activeTexture(gl.TEXTURE2);
        gl.texSubImage3D(
          gl.TEXTURE_3D,
          0,
          0,
          0,
          0,
          EDGE,
          EDGE,
          EDGE,
          gl.RGB,
          gl.UNSIGNED_BYTE,
          options.lut,
        );
        previousLut = options.lut;
      }
      gl.uniform1i(
        style,
        ["color", "bw", "aero", "purple"].indexOf(options.style),
      );
      gl.uniform1i(stripes, options.stripes !== false);
      gl.drawArrays(gl.TRIANGLES, 0, 3);
      return gl.getError() === gl.NO_ERROR && !gl.isContextLost();
    },
    dispose() {
      textures.forEach((tex) => gl.deleteTexture(tex));
      gl.deleteProgram(program);
      gl.getExtension("WEBGL_lose_context")?.loseContext();
    },
  };
}

export function createFilmLutRenderer({ gpu = true } = {}) {
  const surface = () =>
    typeof document === "undefined"
      ? new OffscreenCanvas(1, 1)
      : document.createElement("canvas");
  let gpuCanvas,
    engine,
    cpuCanvas,
    attempted = false;
  return {
    draw(destination, image, options = {}) {
      const context = destination.canvas
        ? destination
        : destination.getContext("2d");
      const { width, height } = dimensions(image, options.scale);
      const canvas = context.canvas;
      if (canvas.width !== width) canvas.width = width;
      if (canvas.height !== height) canvas.height = height;
      let output,
        backend = "cpu";
      if (!image.data && !options.preview && !options.manual &&
          (options.stripes === false || !options.film) && (options.style || "color") === "color") {
        output = image;
        backend = "canvas2d";
      }
      if (!output && gpu) {
        if (options.lut && options.preview && options.film) checkLut(options.lut);
        if (!attempted) {
          attempted = true;
          gpuCanvas = surface();
          try {
            engine = createGPU(gpuCanvas);
          } catch {
            engine = null;
          }
          gpuCanvas.addEventListener("webglcontextlost", (e) => {
            e.preventDefault();
          });
          gpuCanvas.addEventListener("webglcontextrestored", () => {
            engine = null;
            attempted = false;
          });
        }
        if (engine?.draw(image, options)) {
          output = gpuCanvas;
          backend = "webgl2";
        }
      }
      if (!output) {
        const frame = nativeCPU(image, options);
        const pixels = new ImageData(frame.data, frame.width, frame.height);
        if (width === image.width && height === image.height) {
          context.putImageData(pixels, 0, 0);
          return { width, height, backend };
        }
        cpuCanvas ||= surface();
        if (cpuCanvas.width !== image.width) cpuCanvas.width = image.width;
        if (cpuCanvas.height !== image.height) cpuCanvas.height = image.height;
        cpuCanvas.getContext("2d").putImageData(pixels, 0, 0);
        output = cpuCanvas;
      }
      context.save();
      context.setTransform(1, 0, 0, 1, 0, 0);
      context.globalCompositeOperation = "copy";
      context.globalAlpha = 1;
      context.imageSmoothingEnabled = true;
      context.imageSmoothingQuality = "low";
      context.drawImage(output, 0, 0, width, height);
      context.restore();
      return { width, height, backend };
    },
    dispose() {
      engine?.dispose();
      engine = null;
      cpuCanvas = gpuCanvas = null;
      attempted = false;
    },
  };
}
