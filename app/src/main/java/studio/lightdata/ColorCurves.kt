package studio.lightdata

import kotlin.math.*

internal data class ColorCurves(val density: Map<String,List<Pair<Double,Double>>>, val spectral: Map<String,List<Pair<Double,Double>>>) {
    val matrix: FloatArray by lazy {
        val centers = listOf(610.0, 545.0, 450.0)
        val widths = listOf(45.0, 35.0, 30.0)
        val rows = listOf("red","green","blue").map { layer ->
            val points = spectral[layer].orEmpty()
            val weights = centers.indices.map { c ->
                points.sumOf { (nm,log) -> 10.0.pow(log) * exp(-.5*((nm-centers[c])/widths[c]).pow(2)) }
            }
            val total = weights.sum()
            if (total > 0) weights.map { it/total } else centers.indices.map { if(it==centers.indices.first { i -> listOf("red","green","blue")[i]==layer }) 1.0 else 0.0 }
        }
        // OpenGL expects column-major order.
        FloatArray(9) { i -> rows[i%3][i/3].toFloat() }
    }
    private val prints by lazy { density.mapValues { NegativePrint(it.value) } }
    fun positive(channel: String, ev: Double): Double = prints.getValue(channel).positive(ev)

}

internal val colorCurves: Map<String,ColorCurves> by lazy {
    fun read(file: String) = checkNotNull(FilmRecord::class.java.getResourceAsStream(file))
        .bufferedReader().use { it.readLines().drop(1).map { row -> row.split(',') } }
    val density = read("layer-density.csv").groupBy { it[0] }
    val spectral = read("spectral.csv").groupBy { it[0] }
    density.mapValues { (name,rows) ->
        ColorCurves(rows.groupBy { it[3] }.mapValues { (_,r)->r.map { it[4].toDouble() to it[5].toDouble() } },
            spectral[name].orEmpty().groupBy { it[3] }.mapValues { (_,r)->r.map { it[4].toDouble() to it[5].toDouble() } })
    }
}
