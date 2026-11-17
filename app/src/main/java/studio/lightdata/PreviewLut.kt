package studio.lightdata

import android.content.res.AssetManager
import android.util.LruCache
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.*

data class LutEntry(
    val film: String, val file: String, val kind: String, val description: String,
    val preferred: Boolean = false, val iso: Int = 0, val approximation: String = "",
    val source: String = "", val digest: String = "",
) {
    val label get() = (if (kind == "upstream") "上游配置烘焙" else "LightData 派生配置") + " · " + description
}

class LutAtlas(val entry: LutEntry, val pixels: ByteBuffer)

internal class LutAssets(private val assets: AssetManager) {
    private val cache = LruCache<String, LutAtlas>(3)
    fun entries(): List<LutEntry> {
        val manifest = assets.open("index.json").bufferedReader().use { JSONObject(it.readText()) }
        val rows = manifest.getJSONArray("entries")
        return (0 until rows.length()).map {
            val row = rows.getJSONObject(it)
            val file = row.getString("file")
            require(!file.startsWith('/') && file.split('/').none { s -> s.isEmpty() || s == ".." })
            LutEntry(row.getString("film"), file, row.getString("kind"), row.getString("description"),
                row.optBoolean("default"), row.optInt("iso"), row.optString("approximation"), row.optString("source"),
                row.optJSONObject("validation")?.optString("sha256").orEmpty())
        }
    }
    fun load(entry: LutEntry): LutAtlas {
        cache.get(entry.file)?.let { return it }
        val cube = assets.open(entry.file).use { it.readBytes() }
        if (entry.digest.isNotEmpty()) {
            val hash = MessageDigest.getInstance("SHA-256").digest(cube).joinToString("") { "%02x".format(it) }
            require(hash.equals(entry.digest, ignoreCase = true)) { "LUT 文件校验失败" }
        }
        return LutAtlas(entry, packCube(cube)).also { cache.put(entry.file, it) }
    }
}

internal const val CUBE_SIZE = 33
internal fun packCube(input: ByteArray): ByteBuffer {
    val n = CUBE_SIZE
    require(input.size == n*n*n*3) { "LUT 数据长度不符" }
    val atlas = ByteBuffer.allocateDirect(input.size)
    for (g in 0 until n) for (b in 0 until n) atlas.put(input, (b*n + g)*n*3, n*3)
    atlas.flip()
    return atlas
}

internal class StillDetector {
    private var previous = IntArray(0)
    private var basis: FloatArray? = null
    private var since = 0L
    private var lastTime = 0L
    private var shape = Triple("", 0, 0)
    fun observe(frame: Frame, axes: FloatArray?, now: Long): Boolean {
        val signature = IntArray(96) { i ->
            luma(frame.sample((i % 12 + .5)/12, (i / 12 + .5)/8))
        }
        val geometry = Triple(frame.id, frame.w, frame.h)
        val prior = basis
        // Compare to the start of the still interval, so a slow pan also resets it.
        val turned = axes != null && prior != null &&
            (axes.indices.sumOf { (axes[it] * prior[it]).toDouble() } - 1) / 2 < cos(Math.toRadians(.6))
        val changed = previous.size != signature.size ||
            signature.indices.sumOf { abs(signature[it] - previous[it]) } > signature.size * 3
        if (geometry != shape || now !in lastTime..lastTime + 1500 || changed || turned) {
            basis = axes?.copyOf()
            previous = signature
            shape = geometry
            since = now
        }
        lastTime = now
        return now - since >= 650
    }
}
