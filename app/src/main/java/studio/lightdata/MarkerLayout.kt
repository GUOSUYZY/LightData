package studio.lightdata

import androidx.compose.ui.geometry.Offset

/** The ring remains at the actual sample; only labels can move inside a frame's edge. */
internal fun markerAnchor(point: ExtremalSpot, width: Float, height: Float): Offset =
    Offset((point.x * width).toFloat(), (point.y * height).toFloat())

internal fun markerLabelCenter(point: ExtremalSpot, width: Float, height: Float, inset: Float): Offset {
    fun bounded(value: Double, extent: Float) = if (extent <= 2 * inset) extent / 2
        else (value * extent).toFloat().coerceIn(inset, extent - inset)
    return Offset(bounded(point.x, width), bounded(point.y, height))
}
