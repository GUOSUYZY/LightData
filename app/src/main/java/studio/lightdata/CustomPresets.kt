package studio.lightdata

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.FilterReader
import java.io.IOException
import java.io.Reader
import java.io.StringReader
import java.io.StringWriter
import java.util.UUID

data class CustomPreset(
    val id: String,
    val name: String,
    val digital: Boolean,
    val total: Double,
    val above: Double,
    val iso: Double,
) {
    init {
        require(id.length == 36 && UUID.fromString(id).toString() == id) { "预设 ID 必须是规范的小写 UUID" }
        require(name == name.trim() && name.isNotBlank() && name.length <= 80 && name.none { it.isISOControl() }) {
            "预设名称须为 1–80 个字符，不能含控制字符或首尾空白"
        }
        require(total in 1.0..32.0) { "总动态范围须在 1–32 EV 内" }
        require(above in 0.1..(total - 0.1)) { "中灰以上和以下均须至少保留 0.1 EV" }
        require(iso in 0.001..10000000.0) { "参考 ISO 须在 0.001–10000000 内" }
    }

    fun toFilm(): Film = customFilm(digital, total, above).copy(name = "自定义 · $name")

    companion object {
        fun create(name: String, digital: Boolean, total: Double, above: Double, iso: Double) =
            CustomPreset(UUID.randomUUID().toString(), name.trim(), digital, total, above, iso)
    }
}

object CustomPresets {
    const val MAX_BYTES = 2 * 1024 * 1024
    private const val MAX_PRESETS = 2048
    private const val FORMAT = "lightdata.custom-presets"

    private fun checkList(presets: List<CustomPreset>) {
        require(presets.size <= MAX_PRESETS) { "最多支持 $MAX_PRESETS 个预设" }
        require(presets.distinctBy { it.id }.size == presets.size) { "文件中出现重复预设 ID" }
    }

    fun encode(presets: List<CustomPreset>): String {
        checkList(presets)
        val output = StringWriter()
        JsonWriter(output).use { writer ->
            writer.setIndent("  ")
            writer.beginObject().name("format").value(FORMAT).name("version").value(1L)
            writer.name("presets").beginArray()
            for (p in presets) {
                writer.beginObject()
                writer.name("id").value(p.id)
                writer.name("name").value(p.name)
                writer.name("digital").value(p.digital)
                writer.name("total").value(p.total)
                writer.name("above").value(p.above)
                writer.name("iso").value(p.iso)
                writer.endObject()
            }
            writer.endArray().endObject()
        }
        return output.toString()
    }

    fun decode(json: String): List<CustomPreset> {
        require(json.length <= MAX_BYTES && json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "预设文件超过 2 MiB" }
        return decode(StringReader(json))
    }

    fun decode(input: Reader): List<CustomPreset> {
        // Bound even a single malformed or unterminated JSON token before the parser expands it.
        val bounded = object : FilterReader(input) {
            private var consumed = 0
            override fun read(buffer: CharArray, offset: Int, length: Int): Int {
                val n = super.read(buffer, offset, minOf(length, MAX_BYTES - consumed + 1))
                if (n > 0) consumed += n
                require(consumed <= MAX_BYTES) { "预设文件过大" }
                return n
            }
        }
        try {
            JsonReader(bounded).use { reader ->
                reader.isLenient = false
                val presets = mutableListOf<CustomPreset>()
                val fields = mutableSetOf<String>()
                reader.beginObject()
                while (reader.hasNext()) {
                    when (val key = reader.uniqueField(fields)) {
                        "format" -> require(reader.stringValue() == FORMAT) { "无法识别预设文件格式" }
                        "version" -> {
                            require(reader.peek() == JsonToken.NUMBER && reader.nextString() == "1") { "不支持的预设格式版本" }
                        }
                        "presets" -> {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                require(presets.size < MAX_PRESETS) { "预设数量过多" }
                                presets += readPreset(reader)
                            }
                            reader.endArray()
                        }
                        else -> throw IllegalArgumentException("未知文件字段 $key")
                    }
                }
                reader.endObject()
                require(fields.size == 3 && reader.peek() == JsonToken.END_DOCUMENT) { "预设文件字段不完整或有多余内容" }
                checkList(presets)
                return presets
            }
        } catch (error: IOException) {
            throw IllegalArgumentException("预设文件不是有效 JSON", error)
        } catch (error: IllegalStateException) {
            throw IllegalArgumentException("预设字段类型错误", error)
        }
    }

    private fun JsonReader.uniqueField(seen: MutableSet<String>): String =
        nextName().also { require(seen.add(it)) { "重复字段 $it" } }

    private fun JsonReader.stringValue(): String {
        require(peek() == JsonToken.STRING) { "需要字符串" }
        return nextString()
    }

    private fun JsonReader.numberValue(): Double {
        require(peek() == JsonToken.NUMBER) { "需要数值" }
        return nextDouble()
    }

    private fun readPreset(reader: JsonReader): CustomPreset {
        var id: String? = null
        var name: String? = null
        var digital: Boolean? = null
        var total: Double? = null
        var above: Double? = null
        var iso: Double? = null
        val fields = mutableSetOf<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (val key = reader.uniqueField(fields)) {
                "id" -> id = reader.stringValue()
                "name" -> name = reader.stringValue()
                "digital" -> {
                    require(reader.peek() == JsonToken.BOOLEAN) { "digital 须为布尔值" }
                    digital = reader.nextBoolean()
                }
                "total" -> total = reader.numberValue()
                "above" -> above = reader.numberValue()
                "iso" -> iso = reader.numberValue()
                else -> throw IllegalArgumentException("未知预设字段 $key")
            }
        }
        reader.endObject()
        require(id != null && name != null && digital != null && total != null && above != null && iso != null) {
            "预设字段不完整"
        }
        return CustomPreset(id, name, digital, total, above, iso)
    }

    fun merge(existing: List<CustomPreset>, incoming: List<CustomPreset>): List<CustomPreset> {
        checkList(existing)
        checkList(incoming)
        val byId = existing.associateByTo(linkedMapOf()) { it.id }
        incoming.forEach { byId[it.id] = it }
        return byId.values.toList().also(::checkList)
    }
}
