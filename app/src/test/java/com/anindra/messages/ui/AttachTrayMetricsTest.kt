package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The attachment tray's geometry: a tall camera tile beside a grid of actions,
 * scaled from the approved design so the tray fits any screen width.
 */
class AttachTrayMetricsTest {

    private val epsilon = 0.01f

    @Test
    fun theTrayIsCompactOnAPhoneWidth() {
        // 412 dp screen minus 8 dp of page padding on each side.
        val sizes = AttachTrayMetrics.sizes(396f)

        assertEquals(8f, sizes.gap.value, epsilon)
        assertEquals(8f, sizes.pagePad.value, epsilon)
        assertEquals(8f, sizes.verticalPad.value, epsilon)
        // The design's 132 : 112 camera-to-tile ratio, scaled to fill the width.
        assertEquals(132f / 112f, sizes.cameraWidth.value / sizes.tile.value, epsilon)
        assertEquals(119.6f, sizes.tile.value, 0.1f)
        // A tile is wider than it is tall, which is what keeps the tray short.
        assertEquals(
            AttachTrayMetrics.TILE_ASPECT,
            sizes.tileHeight.value / sizes.tile.value,
            epsilon
        )
        assertEquals(223.2f, sizes.cameraHeight.value, 0.2f)
        // About a keyboard, so swapping the two does not move the composer.
        assertEquals(239.2f, sizes.height.value, 0.2f)
    }

    @Test
    fun columnsAndGapsFillTheContentWidthExactly() {
        for (content in listOf(396f, 344f, 280f, 460f)) {
            val sizes = AttachTrayMetrics.sizes(content)
            val used = sizes.cameraWidth + sizes.tile * AttachTrayMetrics.COLUMNS +
                sizes.gap * AttachTrayMetrics.COLUMNS
            assertEquals("width $content", content, used.value, epsilon)
        }
    }

    @Test
    fun theCameraColumnKeepsItsRatioToATile() {
        val ratio = AttachTrayMetrics.REFERENCE_CAMERA_DP / AttachTrayMetrics.REFERENCE_TILE_DP
        for (content in listOf(396f, 344f, 260f)) {
            val sizes = AttachTrayMetrics.sizes(content)
            assertEquals(
                "width $content",
                ratio,
                sizes.cameraWidth.value / sizes.tile.value,
                epsilon
            )
        }
    }

    @Test
    fun theTrayTakesTheKeyboardsSlot() {
        // 261 dp is what this AVD's keyboard measures after the navigation bar
        // is taken out: the tray must fill exactly that so nothing moves.
        val sizes = AttachTrayMetrics.sizes(396f, targetHeightDp = 261f)

        assertEquals(261f, sizes.height.value, 0.3f)
        // Two rows, two gaps' worth of padding and one gap between them.
        assertEquals(261f, sizes.cameraHeight.value + sizes.verticalPad.value * 2, 0.3f)
    }

    @Test
    fun aTallerKeyboardGrowsTheTiles() {
        val taller = AttachTrayMetrics.sizes(396f, targetHeightDp = 300f)

        assertTrue("tiles grow with the slot", taller.tileHeight > taller.tile * 0.9f)
        assertTrue("and the tray fills it", taller.height.value > 290f)
    }

    @Test
    fun aTallScreenKeyboardDoesNotMakeTheTilesAbsurd() {
        // A tablet keyboard is far taller than a phone's; the tiles stop growing
        // rather than turning into columns.
        val sizes = AttachTrayMetrics.sizes(396f, targetHeightDp = 520f)

        assertEquals(
            AttachTrayMetrics.MAX_TILE_ASPECT,
            sizes.tileHeight.value / sizes.tile.value,
            epsilon
        )
    }

    @Test
    fun aShorterSlotLeavesTheTilesAlone() {
        val natural = AttachTrayMetrics.sizes(396f)
        val short = AttachTrayMetrics.sizes(396f, targetHeightDp = 180f)

        assertEquals(natural.tileHeight.value, short.tileHeight.value, epsilon)
        assertEquals(natural.height.value, short.height.value, epsilon)
    }

    @Test
    fun aNarrowScreenStillGetsUsableTiles() {
        // 360 dp phone: the grid shrinks rather than pushing a column off screen.
        val sizes = AttachTrayMetrics.sizes(344f)

        assertTrue("tiles stay large enough to tap", sizes.tile.value > 80f)
        assertTrue("camera stays taller than wide", sizes.cameraHeight > sizes.cameraWidth)
    }

    @Test
    fun aDegenerateWidthDoesNotProduceNegativeSizes() {
        val sizes = AttachTrayMetrics.sizes(0f)

        assertTrue(sizes.tile.value > 0f)
        assertTrue(sizes.cameraWidth.value > 0f)
        assertTrue(sizes.cameraHeight.value > 0f)
    }

    @Test
    fun theTrayIsTallerThanASingleTileRow() {
        val sizes = AttachTrayMetrics.sizes(396f)

        // The camera tile sets the grid's height, so the tray can never be
        // shorter than it and clip the tile.
        assertTrue(sizes.height > sizes.cameraHeight)
        assertEquals(sizes.verticalPad * 2, sizes.height - sizes.cameraHeight)
    }
}
