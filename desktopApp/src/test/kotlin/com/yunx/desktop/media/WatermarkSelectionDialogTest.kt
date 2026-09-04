package com.yunx.desktop.media

import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WatermarkSelectionDialogTest {
    @Test
    fun `maps scaled preview coordinates to original pixels`() {
        val region = mapPreviewSelection(
            selection = Rectangle(20, 25, 20, 10),
            viewport = ImageViewport(10, 20, 100, 50),
            imageWidth = 1_000,
            imageHeight = 500
        )

        assertEquals(RepairRegion(100, 50, 200, 100), region)
    }

    @Test
    fun `rounds outward so touched pixels are retained`() {
        val region = mapPreviewSelection(
            selection = Rectangle(1, 1, 1, 1),
            viewport = ImageViewport(0, 0, 7, 5),
            imageWidth = 100,
            imageHeight = 50
        )

        assertEquals(RepairRegion(14, 10, 15, 10), region)
    }

    @Test
    fun `clips a drag rectangle to the visible image`() {
        val region = mapPreviewSelection(
            selection = Rectangle(0, 0, 60, 60),
            viewport = ImageViewport(10, 20, 100, 50),
            imageWidth = 1_000,
            imageHeight = 500
        )

        assertEquals(RepairRegion(0, 0, 500, 400), region)
    }

    @Test
    fun `maps the full viewport to the full image`() {
        val region = mapPreviewSelection(
            selection = Rectangle(10, 20, 100, 50),
            viewport = ImageViewport(10, 20, 100, 50),
            imageWidth = 1_001,
            imageHeight = 501
        )

        assertEquals(RepairRegion(0, 0, 1_001, 501), region)
    }

    @Test
    fun `returns null when drag does not overlap the image`() {
        val region = mapPreviewSelection(
            selection = Rectangle(0, 0, 5, 5),
            viewport = ImageViewport(10, 20, 100, 50),
            imageWidth = 1_000,
            imageHeight = 500
        )

        assertNull(region)
    }
}
