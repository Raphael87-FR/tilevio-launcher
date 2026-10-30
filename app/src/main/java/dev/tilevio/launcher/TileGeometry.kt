package dev.tilevio.launcher

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Tile grid geometry, shared by the layout and the banner bound so they cannot drift apart. */
internal object TileGeometry {
    const val COLUMN_COUNT = 5
    const val ASPECT_RATIO = 16f / 9f
    val GridMargin = 32.dp
    val GridGutter = 24.dp

    fun width(gridWidth: Dp): Dp = (gridWidth - GridMargin * 2 - GridGutter * (COLUMN_COUNT - 1)) / COLUMN_COUNT

    fun height(tileWidth: Dp): Dp = tileWidth / ASPECT_RATIO

    /** Width of a Tile on a full-width grid, rounded up so its banner is not upscaled. */
    fun bannerWidthPx(displayWidthPx: Int, density: Float): Int = with(Density(density)) {
        ceil(width(displayWidthPx.toDp()).toPx()).toInt()
    }

    fun bannerHeightPx(tileWidthPx: Int): Int = (tileWidthPx / ASPECT_RATIO).roundToInt()
}
