package studio.lightdata

import android.content.Context
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

data class Look(
    val frame: Frame,
    val lut: DoubleArray,
    val delta: Double,
    val film: Film,
    val preview: Boolean,
    val manual: Boolean,
    val style: Int,
    val atlas: LutAtlas? = null,
)

class Preview(ctx: Context) : GLSurfaceView(ctx), GLSurfaceView.Renderer {
    var onCubeUnavailable: ((String) -> Unit)? = null
    @Volatile private var look: Look? = null
    private var scale = 1.0
    private var bufferWidth = 0
    private var bufferHeight = 0
    private val textures = IntArray(3)
    private var lastAtlas: LutAtlas? = null
    private var rejectedAtlas: LutAtlas? = null
    private var program = 0
    private var last: Frame? = null
    private var bytes = ByteBuffer.allocateDirect(4).order(ByteOrder.LITTLE_ENDIAN)
    private val table = ByteBuffer.allocateDirect(256 * 4 * 3)
    private val quad =
        ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            position(0)
        }

    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(this)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun show(next: Look, resolution: Double) {
        look = next
        scale = resolution
        resizeBuffer()
        requestRender()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resizeBuffer()
    }

    private fun resizeBuffer() {
        val f = look?.frame ?: return
        if (width == 0 || height == 0) return
        val factor = minOf(1.0, width.toDouble()/f.w, height.toDouble()/f.h) * scale
        val w = max(1, (f.w * factor).roundToInt())
        val h = max(1, (f.h * factor).roundToInt())
        if (w == bufferWidth && h == bufferHeight) return
        bufferWidth = w
        bufferHeight = h
        holder.setFixedSize(w, h)
    }

    override fun onSurfaceCreated(g: GL10?, config: EGLConfig?) {
        fun compile(type: Int, src: String): Int =
            glCreateShader(type).also {
                glShaderSource(it, src)
                glCompileShader(it)
            }
        program = glCreateProgram()
        glAttachShader(
            program,
            compile(
                GL_VERTEX_SHADER,
                "attribute vec2 p; varying vec2 uv; void main(){uv=vec2((p.x+1.)*.5,(1.-p.y)*.5); gl_Position=vec4(p,0.,1.);}",
            ),
        )
        glAttachShader(
            program,
            compile(
                GL_FRAGMENT_SHADER,
                """
                    precision highp float;
                    uniform sampler2D frame; uniform sampler2D curve; uniform sampler2D cube;
                    uniform int style; uniform int tone; uniform int colorFilm; uniform int useCube; uniform mat3 spectral;
                    uniform vec2 zoneBounds;
                    varying vec2 uv;
                    vec3 decodeRGB(vec3 c){
                        return mix(c/12.92,pow((c+.055)/1.055,vec3(2.4)),step(vec3(.04045),c));
                    }
                    vec3 encodeRGB(vec3 c){
                        return mix(12.92*c,1.055*pow(c,vec3(1./2.4))-.055,step(vec3(.0031308),c));
                    }
                    vec3 cubeColor(vec3 linear) {
                        vec3 grid = clamp(log2(1. + max(linear, vec3(0.))/.18)/9.,0.,1.)*32.;
                        float b = floor(grid.b);
                        vec2 a = vec2((33.*b + grid.r + .5)/1089., (grid.g + .5)/33.);
                        vec2 next = vec2((33.*min(32., b+1.) + grid.r + .5)/1089., a.y);
                        return mix(texture2D(cube, a).rgb, texture2D(cube, next).rgb, fract(grid.b));
                    }
                    vec3 applyStyle(vec3 c) {
                        if(style==2) return vec3(max(c.g,c.r*.65),c.r*.8,c.b*.72);
                        if(style==3) return vec3(max(c.r,c.g*.86),c.b*.72,max(c.b,c.g*.93));
                        return c;
                    }
                    vec3 atLuminance(vec3 color, float y) {
                        float original = dot(color,vec3(.2126,.7152,.0722));
                        if(original < .00001) return vec3(y);
                        vec3 chroma = color * (y / original) - y;
                        float high = max(chroma.r,max(chroma.g,chroma.b));
                        float low = -min(chroma.r,min(chroma.g,chroma.b));
                        float strength = min(1.,min((1.-y)/max(.000001,high), y/max(.000001,low)));
                        return clamp(vec3(y) + strength*chroma,0.,1.);
                    }
                    void main(){
                        vec4 src=texture2D(frame,uv).bgra;
                        if(src.a<0.5){gl_FragColor=vec4(.06,.06,.06,1.);return;}
                        vec3 rgb=src.rgb;
                        const vec3 weights=vec3(.2126,.7152,.0722);
                        float y=dot(decodeRGB(rgb),weights);
                        float gray=encodeRGB(vec3(y)).r;
                        float ix=(floor(gray*255.+.5)+.5)/256.;
                        vec3 map=texture2D(curve,vec2(ix,1./6.)).rgb;
                        if(style==1)rgb=vec3(gray);
                        rgb=applyStyle(rgb);
                        if(tone!=0) {
                            vec3 linear=decodeRGB(rgb);
                            float targetY=decodeRGB(map.rrr).r;
                            if(useCube!=0) {
                                vec2 packedEV=texture2D(curve,vec2(ix,5./6.)).rg;
                                float ev=(dot(packedEV,vec2(65280.,255.))/65535.)*160.-80.;
                                float scene=.18*exp2(ev);
                                vec3 inputRGB=y>0.000001 ? decodeRGB(src.rgb)*(scene/y) : vec3(scene);
                                vec3 lutColor=cubeColor(inputRGB);
                                if(max(src.r,max(src.g,src.b)) > min(src.r,min(src.g,src.b))) {
                                    float neutralY=dot(decodeRGB(cubeColor(vec3(scene))),weights);
                                    float colorY=dot(decodeRGB(lutColor),weights);
                                    vec2 bounds=map.g>.5 ? vec2(zoneBounds.y,1.) :
                                        (map.b>.5 ? vec2(0.,zoneBounds.x) : zoneBounds);
                                    targetY=clamp(targetY*(colorY+.0001)/(neutralY+.0001),bounds.x,bounds.y);
                                }
                                linear=decodeRGB(applyStyle(lutColor));
                                if(style==1) linear=vec3(dot(linear,weights));
                                rgb=encodeRGB(atLuminance(linear,targetY));
                            } else if(colorFilm!=0 && style==0) {
                                vec3 codes=encodeRGB(clamp(spectral*linear,0.,1.));
                                rgb=vec3(texture2D(curve,vec2(codes.r,.5)).r,texture2D(curve,vec2(codes.g,.5)).g,texture2D(curve,vec2(codes.b,.5)).b);
                            } else {
                                float sourceY=dot(linear,weights);
                                rgb=encodeRGB(clamp(sourceY>0.000001 ? linear*(targetY/sourceY) : vec3(targetY),0.,1.));
                            }
                        }
                        if(map.g>.5 && mod(gl_FragCoord.x+gl_FragCoord.y,12.)<3.)rgb=mix(rgb,vec3(1.,.22,.2),.82);
                        if(map.b>.5 && mod(gl_FragCoord.x-gl_FragCoord.y,12.)<3.)rgb=mix(rgb,vec3(.12,.86,.52),.82);
                        gl_FragColor=vec4(rgb,1.);
                    }
                """
                    .trimIndent(),
            ),
        )
        glLinkProgram(program)
        glGenTextures(3, textures, 0)
        lastAtlas = null
        rejectedAtlas = null
        last = null
        textures.forEachIndexed { i, t ->
            glActiveTexture(GL_TEXTURE0 + i)
            glBindTexture(GL_TEXTURE_2D, t)
            for (p in listOf(GL_TEXTURE_MIN_FILTER, GL_TEXTURE_MAG_FILTER)) glTexParameteri(
                GL_TEXTURE_2D,
                p,
                if (i == 1) GL_NEAREST else GL_LINEAR,
            )
            for (p in listOf(GL_TEXTURE_WRAP_S, GL_TEXTURE_WRAP_T)) glTexParameteri(
                GL_TEXTURE_2D,
                p,
                GL_CLAMP_TO_EDGE,
            )
        }
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, 1, 1, 0, GL_RGB, GL_UNSIGNED_BYTE, ByteBuffer.allocateDirect(4))
    }

    override fun onSurfaceChanged(g: GL10?, w: Int, h: Int) {
        glViewport(0, 0, w, h)
    }

    override fun onDrawFrame(g: GL10?) {
        val l = look ?: return
        val f = l.frame
        glUseProgram(program)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, textures[0])
        if (last !== f) {
            val size = f.w * f.h * 4
            if (bytes.capacity() < size)
                bytes = ByteBuffer.allocateDirect(size).order(ByteOrder.LITTLE_ENDIAN)
            bytes.clear()
            // Bulk upload ARGB ints. The shader swaps the little-endian B/R bytes.
            bytes.asIntBuffer().put(f.pixels)
            bytes.limit(size)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, f.w, f.h, 0, GL_RGBA, GL_UNSIGNED_BYTE, bytes)
            last = f
        }
        table.clear()
        for (i in 0..255) {
            val ev = l.lut[i] + l.delta
            val y =
                when {
                    l.preview -> linearToSrgb(l.film.positive(ev))
                    l.manual -> linearToSrgb(.18 * 2.0.pow(ev))
                    else -> i / 255.0
                }
            table.put((y.coerceIn(0.0, 1.0) * 255).roundToInt().toByte())
            val limit = l.film.limit(ev)
            table.put(if (limit == FilmLimit.HIGHLIGHT) (-1).toByte() else 0)
            table.put(if (limit == FilmLimit.SHADOW) (-1).toByte() else 0)
            table.put((-1).toByte())
        }
        val color = if (l.preview && l.style == 0 && l.atlas == null) colorCurves[l.film.name] else null
        if (color != null) {
            for (i in 0..255) {
                val ev = l.lut[i] + l.delta
                for (channel in listOf("red", "green", "blue")) {
                    val value = color.positive(channel, ev)
                    table.put((linearToSrgb(value).coerceIn(0.0, 1.0)*255).roundToInt().toByte())
                }
                table.put((-1).toByte())
            }
        } else table.position(256 * 4 * 2)
        // A calibrated relative exposure travels in two bytes, independently of the
        // display curve. LUT input therefore receives the target exposure exactly once.
        for (i in 0..255) {
            val code = (((l.lut[i] + l.delta).coerceIn(-80.0, 80.0) + 80) * 65535 / 160).roundToInt()
            table.put((code ushr 8).toByte()).put(code.toByte()).put(0).put(0)
        }
        table.flip()
        glActiveTexture(GL_TEXTURE1)
        glBindTexture(GL_TEXTURE_2D, textures[1])
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 256, 3, 0, GL_RGBA, GL_UNSIGNED_BYTE, table)
        glActiveTexture(GL_TEXTURE2)
        glBindTexture(GL_TEXTURE_2D, textures[2])
        val atlas = l.atlas
        if (atlas == null) rejectedAtlas = null
        if (atlas != null && atlas !== lastAtlas && atlas !== rejectedAtlas) {
            glGetError()
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, CUBE_SIZE*CUBE_SIZE, CUBE_SIZE, 0,
                GL_RGB, GL_UNSIGNED_BYTE, atlas.pixels.duplicate())
            if (glGetError() == GL_NO_ERROR) lastAtlas = atlas else {
                rejectedAtlas = atlas
                lastAtlas = null
                post { onCubeUnavailable?.invoke(atlas.entry.file) }
            }
        }
        glUniform1i(glGetUniformLocation(program, "cube"), 2)
        glUniform2f(glGetUniformLocation(program, "zoneBounds"),
            l.film.positive(l.film.low).toFloat(), l.film.positive(l.film.high).toFloat())
        glUniform1i(glGetUniformLocation(program, "useCube"), if (atlas != null && atlas === lastAtlas) 1 else 0)
        glUniform1i(glGetUniformLocation(program, "frame"), 0)
        glUniform1i(glGetUniformLocation(program, "curve"), 1)
        glUniform1i(glGetUniformLocation(program, "style"), l.style)
        glUniform1i(glGetUniformLocation(program, "tone"), if (l.manual || l.preview) 1 else 0)
        glUniform1i(glGetUniformLocation(program, "colorFilm"), if (l.preview && color != null) 1 else 0)
        glUniformMatrix3fv(glGetUniformLocation(program, "spectral"),1,false,color?.matrix ?: floatArrayOf(1f,0f,0f,0f,1f,0f,0f,0f,1f),0)
        val a = glGetAttribLocation(program, "p")
        glEnableVertexAttribArray(a)
        glVertexAttribPointer(a, 2, GL_FLOAT, false, 0, quad)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
    }
}
