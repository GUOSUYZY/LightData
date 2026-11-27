package studio.lightdata

import androidx.compose.ui.geometry.Offset

/** Keep an edge estimate's whole annotation visible, without changing its measured coordinates. */
internal fun markerCenter(point: ExtremalSpot, width: Float, height: Float, inset: Float): Offset {
    fun bounded(value: Double, extent: Float) = if (extent <= 2 * inset) extent / 2
        else (value * extent).toFloat().coerceIn(inset, extent - inset)
    return Offset(bounded(point.x, width), bounded(point.y, height))
}
