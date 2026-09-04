package com.yunx.desktop.media

import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageWatermarkRepairTest {
    @Test
    fun `repairs a watermark from robust boundary medians`() = withTemporaryDirectory { directory ->
        val source = directory.resolve("source.png")
        val output = directory.resolve("result.png")
        val image = solidImage(24, 18, Color(235, 220, 190).rgb)
        fill(image, RepairRegion(7, 5, 10, 7), Color.BLACK.rgb)
        image.setRGB(6, 8, Color.RED.rgb) // One contaminated boundary pixel must not dominate.
        ImageIO.write(image, "png", source.toFile())

        val resultPath = ImageWatermarkRepair.repair(source, output, RepairRegion(7, 5, 10, 7))

        assertEquals(output.toAbsolutePath(), resultPath)
        assertTrue(Files.exists(source), "The source must be preserved")
        val repaired = ImageIO.read(output.toFile())
        for (y in 5 until 12) for (x in 7 until 17) {
            assertColorNear(Color(235, 220, 190).rgb, repaired.getRGB(x, y), tolerance = 1)
        }
    }

    @Test
    fun `bidirectional interpolation reconstructs a smooth two-axis gradient`() =
        withTemporaryDirectory { directory ->
            val source = directory.resolve("gradient.png")
            val output = directory.resolve("repaired.png")
            val image = BufferedImage(30, 24, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until image.height) for (x in 0 until image.width) {
                image.setRGB(x, y, Color(x * 6, y * 7, 80, 255).rgb)
            }
            val region = RepairRegion(9, 7, 11, 9)
            fill(image, region, Color.BLACK.rgb)
            ImageIO.write(image, "png", source.toFile())

            ImageWatermarkRepair.repair(source, output, region)

            val repaired = ImageIO.read(output.toFile())
            for (y in region.y until region.y + region.height) {
                for (x in region.x until region.x + region.width) {
                    assertColorNear(Color(x * 6, y * 7, 80, 255).rgb, repaired.getRGB(x, y), tolerance = 4)
                }
            }
        }

    @Test
    fun `regions touching image edges use the remaining boundaries`() = withTemporaryDirectory { directory ->
        val source = directory.resolve("edge.png")
        val output = directory.resolve("edge-repaired.png")
        val image = solidImage(16, 14, Color(210, 225, 240).rgb)
        val region = RepairRegion(0, 0, 6, 5)
        fill(image, region, Color.BLACK.rgb)
        ImageIO.write(image, "png", source.toFile())

        ImageWatermarkRepair.repair(source, output, region)

        val repaired = ImageIO.read(output.toFile())
        assertColorNear(Color(210, 225, 240).rgb, repaired.getRGB(0, 0), tolerance = 1)
        assertColorNear(Color(210, 225, 240).rgb, repaired.getRGB(5, 4), tolerance = 1)
    }

    @Test
    fun `writes jpeg and preserves original file bytes`() = withTemporaryDirectory { directory ->
        val source = directory.resolve("photo.jpg")
        val output = directory.resolve("photo-repaired.jpg")
        val image = solidImage(20, 20, Color(245, 235, 215).rgb, BufferedImage.TYPE_INT_RGB)
        fill(image, RepairRegion(6, 6, 8, 8), Color(20, 20, 20).rgb)
        ImageIO.write(image, "jpg", source.toFile())
        val originalBytes = Files.readAllBytes(source)

        ImageWatermarkRepair.repair(source, output, RepairRegion(6, 6, 8, 8))

        assertTrue(Files.exists(output))
        assertTrue(ImageIO.read(output.toFile()) != null)
        assertTrue(originalBytes.contentEquals(Files.readAllBytes(source)))
    }

    @Test
    fun `strictly rejects invalid coordinates source overwrite and existing output`() =
        withTemporaryDirectory { directory ->
            val source = directory.resolve("source.png")
            val output = directory.resolve("occupied.png")
            ImageIO.write(solidImage(10, 10, Color.WHITE.rgb), "png", source.toFile())
            Files.writeString(output, "occupied")

            assertFailsWith<IllegalArgumentException> {
                ImageWatermarkRepair.repair(source, directory.resolve("outside.png"), RepairRegion(8, 8, 3, 3))
            }
            assertFailsWith<IllegalArgumentException> {
                ImageWatermarkRepair.repair(source, source, RepairRegion(1, 1, 3, 3))
            }
            assertFailsWith<IllegalArgumentException> {
                ImageWatermarkRepair.repair(source, output, RepairRegion(1, 1, 3, 3))
            }
            assertFailsWith<IllegalArgumentException> { RepairRegion(-1, 0, 1, 1) }
            assertFailsWith<IllegalArgumentException> { RepairRegion(0, 0, 0, 1) }
            assertFalse(Files.exists(directory.resolve("outside.png")))
        }

    private fun solidImage(
        width: Int,
        height: Int,
        color: Int,
        type: Int = BufferedImage.TYPE_INT_ARGB
    ): BufferedImage = BufferedImage(width, height, type).also { image ->
        for (y in 0 until height) for (x in 0 until width) image.setRGB(x, y, color)
    }

    private fun fill(image: BufferedImage, region: RepairRegion, color: Int) {
        for (y in region.y until region.y + region.height) {
            for (x in region.x until region.x + region.width) image.setRGB(x, y, color)
        }
    }

    private fun assertColorNear(expected: Int, actual: Int, tolerance: Int) {
        for (shift in listOf(24, 16, 8, 0)) {
            val expectedChannel = expected ushr shift and 0xff
            val actualChannel = actual ushr shift and 0xff
            assertTrue(
                abs(expectedChannel - actualChannel) <= tolerance,
                "Channel at shift $shift differs: expected=$expectedChannel actual=$actualChannel"
            )
        }
    }

    private inline fun withTemporaryDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("watermark-repair-test-")
        try {
            block(directory)
        } finally {
            Files.walk(directory).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
