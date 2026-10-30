package dev.tilevio.launcher

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class TileGeometryTest {
    @Test
    fun tileSharesTheGridWidthBetweenFiveColumnsAfterMarginsAndGutters() {
        assertEquals(160.dp, TileGeometry.width(960.dp))
        assertEquals(90.dp, TileGeometry.height(160.dp))
    }

    @Test
    fun bannerBoundOnAFullHdTvIsTheNativeBannerSize() {
        assertEquals(320, TileGeometry.bannerWidthPx(displayWidthPx = 1920, density = 2f))
        assertEquals(180, TileGeometry.bannerHeightPx(320))
    }

    @Test
    fun bannerBoundIsNeverNarrowerThanAFractionalTile() {
        assertEquals(321, TileGeometry.bannerWidthPx(displayWidthPx = 1921, density = 2f))
    }
}
