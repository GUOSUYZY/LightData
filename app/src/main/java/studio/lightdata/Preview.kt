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
    val safety: Boolean,
)

class Preview(ctx: Context) : GLSurfaceView(ctx), GLSurfaceView.Renderer {
    @Volatile var look: Look? = null
    private val textures = IntArray(2)
    private var program = 0
    private var last: Frame? = null
    private var bytes = ByteBuffer.allocateDirect(4)
    private val table = ByteBuffer.allocateDirect(256 * 4 * 2)
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
                    uniform sampler2D frame; uniform sampler2D curve; uniform int style; uniform int tone; uniform int colorFilm; uniform int safety; uniform mat3 spectral;
                    varying vec2 uv;
                    vec3 decodeRGB(vec3 c){
                        return mix(c/12.92,pow((c+.055)/1.055,vec3(2.4)),step(vec3(.04045),c));
                    }
                    vec3 encodeRGB(vec3 c){
                        return mix(12.92*c,1.055*pow(c,vec3(1./2.4))-.055,step(vec3(.0031308),c));
                    }
                    void main(){
                        vec4 src=texture2D(frame,uv);
                        if(src.a<0.5){gl_FragColor=vec4(.06,.06,.06,1.);return;}
                        vec3 raw=src.rgb;
                        vec3 rgb=raw;
                        const vec3 weights=vec3(.2126,.7152,.0722);
                        float y=dot(decodeRGB(rgb),weights);
                        float gray=encodeRGB(vec3(y)).r;
                        vec3 map=texture2D(curve,vec2((floor(gray*255.+.5)+.5)/256.,.25)).rgb;
                        if(style==1)rgb=vec3(gray);
                        if(style==2)rgb=vec3(max(rgb.g,rgb.r*.65),rgb.r*.8,rgb.b*.72);
                        if(style==3)rgb=vec3(max(rgb.r,rgb.g*.86),rgb.b*.72,max(rgb.b,rgb.g*.93));
                        if(tone!=0) {
                            vec3 linear=decodeRGB(rgb);
                            float sourceY=dot(linear,weights);
                            float targetY=decodeRGB(map.rrr).r;
                            rgb=encodeRGB(clamp(sourceY>0.000001 ? linear*(targetY/sourceY) : vec3(targetY),0.,1.));
                        }
                        if(colorFilm!=0 && style==0) {
                            vec3 codes=encodeRGB(clamp(spectral*decodeRGB(src.rgb),0.,1.));
                            rgb=vec3(texture2D(curve,vec2(codes.r,.75)).r,texture2D(curve,vec2(codes.g,.75)).g,texture2D(curve,vec2(codes.b,.75)).b);
                        }
                        vec3 atCodeLimit=max(step(vec3(.998),raw),vec3(1.)-step(vec3(.002),raw));
                        float channelLimits=atCodeLimit.r+atCodeLimit.g+atCodeLimit.b;
                        bool cameraCensored=channelLimits>=2. || gray<=.5/255. || gray>=254.5/255.;
                        if(safety!=0 && cameraCensored && mod(abs(gl_FragCoord.x)+abs(gl_FragCoord.y),12.)<3.) {
                            rgb=mix(rgb,vec3(.8,.42,1.),.86);
                        } else {
                            if(map.g>.5 && mod(gl_FragCoord.x+gl_FragCoord.y,12.)<3.)rgb=mix(rgb,vec3(1.,.22,.2),.82);
                            if(map.b>.5 && mod(gl_FragCoord.x-gl_FragCoord.y,12.)<3.) rgb=mix(rgb,safety!=0?vec3(.18,.62,1.):vec3(.12,.86,.52),.82);
                        }
                        gl_FragColor=vec4(rgb,1.);
                    }
                """
                    .trimIndent(),
            ),
        )
        glLinkProgram(program)
        glGenTextures(2, textures, 0)
        last = null
        textures.forEachIndexed { i, t ->
            glActiveTexture(GL_TEXTURE0 + i)
            glBindTexture(GL_TEXTURE_2D, t)
            for (p in listOf(GL_TEXTURE_MIN_FILTER, GL_TEXTURE_MAG_FILTER)) glTexParameteri(
                GL_TEXTURE_2D,
                p,
                if (i == 0) GL_LINEAR else GL_NEAREST,
            )
            for (p in listOf(GL_TEXTURE_WRAP_S, GL_TEXTURE_WRAP_T)) glTexParameteri(
                GL_TEXTURE_2D,
                p,
                GL_CLAMP_TO_EDGE,
            )
        }
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
                bytes = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
            bytes.clear()
            for (c in f.pixels) {
                bytes.put((c shr 16).toByte())
                bytes.put((c shr 8).toByte())
                bytes.put(c.toByte())
                bytes.put((c ushr 24).toByte())
            }
            bytes.flip()
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
        val color = colorCurves[l.film.name]
        for (i in 0..255) {
            val ev = l.lut[i] + l.delta
            for (channel in listOf("red", "green", "blue")) {
                val value = color?.positive(channel, ev) ?: l.film.positive(ev)
                table.put((linearToSrgb(value).coerceIn(0.0, 1.0)*255).roundToInt().toByte())
            }
            table.put((-1).toByte())
        }
        table.flip()
        glActiveTexture(GL_TEXTURE1)
        glBindTexture(GL_TEXTURE_2D, textures[1])
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 256, 2, 0, GL_RGBA, GL_UNSIGNED_BYTE, table)
        glUniform1i(glGetUniformLocation(program, "frame"), 0)
        glUniform1i(glGetUniformLocation(program, "curve"), 1)
        glUniform1i(glGetUniformLocation(program, "style"), l.style)
        glUniform1i(glGetUniformLocation(program, "tone"), if (l.manual || l.preview) 1 else 0)
        glUniform1i(glGetUniformLocation(program, "colorFilm"), if (l.preview && color != null) 1 else 0)
        glUniform1i(glGetUniformLocation(program, "safety"), if (l.safety) 1 else 0)
        glUniformMatrix3fv(glGetUniformLocation(program, "spectral"),1,false,color?.matrix ?: floatArrayOf(1f,0f,0f,0f,1f,0f,0f,0f,1f),0)
        val a = glGetAttribLocation(program, "p")
        glEnableVertexAttribArray(a)
        glVertexAttribPointer(a, 2, GL_FLOAT, false, 0, quad)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
    }
}
