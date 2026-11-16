package studio.lightdata

/** The digitized manufacturer curves are ordinary UTF-8 CSV, available offline. */
internal fun loadDensityCurves(): List<FilmRecord> {
    val stream = checkNotNull(FilmRecord::class.java.getResourceAsStream("density.csv"))
    return stream.bufferedReader(Charsets.UTF_8).use { reader ->
        require(reader.readLine() == "stock,brand,iso,document_id,page,process,channel,low_ev,high_ev,ev,density")
        val rows = reader.lineSequence().filter { it.isNotBlank() }.map(::csvFields)
            .groupBy { it.first() }
        rows.map { (name, samples) ->
            val first = samples.first()
            val source = first[3]
            val info = "厂家图采样 · 第 ${first[4]} 页 · ${first[6]} · ${first[5]}"
            val points = samples.map { it[9].toDouble() to it[10].toDouble() }
            require(points.size >= 2 && points.zipWithNext().all { (a, b) -> a.first < b.first }) { name }
            FilmRecord(source, first[1], first[2].toInt(),
                Film(name, first[7].toDouble(), first[8].toDouble(), points, info, filmSourceUrl(source)))
        }
    }
}

// Fields such as development conditions can contain commas and double quotes.
private fun csvFields(line: String): List<String> {
    val fields = ArrayList<String>(11)
    val value = StringBuilder()
    var quoted = false
    var i = 0
    while (i < line.length) {
        val ch = line[i++]
        when {
            ch == '"' && quoted && i < line.length && line[i] == '"' -> {
                value.append(ch)
                i++
            }
            ch == '"' -> quoted = !quoted
            ch == ',' && !quoted -> {
                fields.add(value.toString())
                value.clear()
            }
            else -> value.append(ch)
        }
    }
    fields.add(value.toString())
    require(!quoted && fields.size == 11) { "Invalid film curve CSV record" }
    return fields
}
