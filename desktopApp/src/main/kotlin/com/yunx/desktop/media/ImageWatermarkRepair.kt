package com.yunx.desktop.media

import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/** A pixel-aligned rectangle. The right and bottom coordinates are exclusive. */
data class RepairRegion(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
) {
    init {
        require(x >= 0) { "x must not be negative" }
        require(y >= 0) { "y must not be negative" }
        require(width > 0) { "width must be greater than zero" }
        require(height > 0) { "height must be greater than zero" }
    }
}

/**
 * Repairs a rectangular watermark region without native libraries.
 *
 * The replacement field is built from robust per-channel medians sampled just
 * outside all available sides of the rectangle. Horizontal and vertical
 * estimates are interpolated independently, then combined. This avoids copying
 * one noisy edge across the whole area and still works when the selection
 * touches one or two image edges.
 */
object ImageWatermarkRepair {
    private const val SAMPLE_DEPTH = 3
    private const val CROSS_RADIUS = 2

    /**
     * Repairs [region] and writes a new PNG or JPEG file to [output].
     * Existing output files are not replaced and [output] can never be [source].
     */
    fun repair(source: Path, output: Path, region: RepairRegion): Path {
        val sourceFile = source.toAbsolutePath().normalize()
        val outputFile = output.toAbsolutePath().normalize()

        require(Files.isRegularFile(sourceFile)) { "Source image does not exist: $sourceFile" }
        require(Files.isReadable(sourceFile)) { "Source image is not readable: $sourceFile" }
        require(!sameFile(sourceFile, outputFile)) { "Output must not overwrite the source image" }
        require(!Files.exists(outputFile)) { "Output file already exists: $outputFile" }

        val sourceFormat = detectFormat(sourceFile)
        require(sourceFormat == ImageFormat.PNG || sourceFormat == ImageFormat.JPEG) {
            "Only PNG and JPEG source images are supported"
        }
        val outputFormat = formatFromExtension(outputFile)
            ?: throw IllegalArgumentException("Output filename must end with .png, .jpg, or .jpeg")

        val image = ImageIO.read(sourceFile.toFile())
            ?: throw IllegalArgumentException("Unable to decode image: $sourceFile")
        validateRegion(region, image.width, image.height)

        val repaired = repairPixels(image, region)
        val writable = if (outputFormat == ImageFormat.JPEG) repaired.toOpaqueRgb() else repaired
        val parent = outputFile.parent
            ?: throw IllegalArgumentException("Output path must have a parent directory")
        Files.createDirectories(parent)

        val suffix = if (outputFormat == ImageFormat.PNG) ".png" else ".jpg"
        val temporary = Files.createTempFile(parent, ".jiexi-watermark-", suffix)
        try {
            val written = ImageIO.write(writable, outputFormat.writerName, temporary.toFile())
            check(written) { "No image writer is available for ${outputFormat.writerName}" }
            try {
                Files.move(temporary, outputFile, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, outputFile)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
        return outputFile
    }

    /** Repairs to a collision-free sibling such as `photo-repaired.png`. */
    fun repair(source: Path, region: RepairRegion): Path = repair(source, suggestedOutput(source), region)

    fun suggestedOutput(source: Path): Path {
        val absolute = source.toAbsolutePath().normalize()
        val parent = absolute.parent
            ?: throw IllegalArgumentException("Source path must have a parent directory")
        val filename = absolute.fileName.toString()
        val dot = filename.lastIndexOf('.')
        val stem = if (dot > 0) filename.substring(0, dot) else filename
        val extension = if (dot > 0) filename.substring(dot).lowercase() else ".png"
        val safeExtension = if (extension in setOf(".png", ".jpg", ".jpeg")) extension else ".png"

        var candidate = parent.resolve("$stem-repaired$safeExtension")
        var number = 2
        while (Files.exists(candidate)) {
            candidate = parent.resolve("$stem-repaired-$number$safeExtension")
            number++
        }
        return candidate
    }

    private fun repairPixels(source: BufferedImage, region: RepairRegion): BufferedImage {
        val result = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
        val resultGraphics = result.createGraphics()
        try {
            resultGraphics.drawImage(source, 0, 0, null)
        } finally {
            resultGraphics.dispose()
        }

        val left = Array<BoundarySample?>(region.height) { row ->
            sampleVerticalSide(source, region.x, region.y + row, leftSide = true)
        }
        val right = Array<BoundarySample?>(region.height) { row ->
            sampleVerticalSide(source, region.x + region.width, region.y + row, leftSide = false)
        }
        val top = Array<BoundarySample?>(region.width) { column ->
            sampleHorizontalSide(source, region.x + column, region.y, topSide = true)
        }
        val bottom = Array<BoundarySample?>(region.width) { column ->
            sampleHorizontalSide(source, region.x + column, region.y + region.height, topSide = false)
        }

        for (row in 0 until region.height) {
            for (column in 0 until region.width) {
                val px = region.x + column
                val py = region.y + row
                val horizontal = interpolate(left[row], right[row], px)
                val vertical = interpolate(top[column], bottom[column], py)
                val repaired = when {
                    horizontal != null && vertical != null -> mix(horizontal, vertical, 0.5)
                    horizontal != null -> horizontal
                    vertical != null -> vertical
                    else -> error("The selected region has no surrounding pixels to sample")
                }
                result.setRGB(px, py, repaired)
            }
        }
        return result
    }

    private fun sampleVerticalSide(
        image: BufferedImage,
        boundaryX: Int,
        centerY: Int,
        leftSide: Boolean
    ): BoundarySample? {
        val xRange = if (leftSide) {
            maxOf(0, boundaryX - SAMPLE_DEPTH) until boundaryX
        } else {
            boundaryX until minOf(image.width, boundaryX + SAMPLE_DEPTH)
        }
        if (xRange.isEmpty()) return null
        val yRange = maxOf(0, centerY - CROSS_RADIUS)..minOf(image.height - 1, centerY + CROSS_RADIUS)
        val pixels = mutableListOf<Int>()
        val coordinates = mutableListOf<Int>()
        for (x in xRange) for (y in yRange) {
            pixels += image.getRGB(x, y)
            coordinates += x
        }
        return BoundarySample(medianColor(pixels), median(coordinates).toDouble())
    }

    private fun sampleHorizontalSide(
        image: BufferedImage,
        centerX: Int,
        boundaryY: Int,
        topSide: Boolean
    ): BoundarySample? {
        val yRange = if (topSide) {
            maxOf(0, boundaryY - SAMPLE_DEPTH) until boundaryY
        } else {
            boundaryY until minOf(image.height, boundaryY + SAMPLE_DEPTH)
        }
        if (yRange.isEmpty()) return null
        val xRange = maxOf(0, centerX - CROSS_RADIUS)..minOf(image.width - 1, centerX + CROSS_RADIUS)
        val pixels = mutableListOf<Int>()
        val coordinates = mutableListOf<Int>()
        for (y in yRange) for (x in xRange) {
            pixels += image.getRGB(x, y)
            coordinates += y
        }
        return BoundarySample(medianColor(pixels), median(coordinates).toDouble())
    }

    private fun interpolate(first: BoundarySample?, second: BoundarySample?, coordinate: Int): Int? = when {
        first != null && second != null -> {
            val span = second.coordinate - first.coordinate
            val amount = if (span == 0.0) 0.5 else ((coordinate - first.coordinate) / span).coerceIn(0.0, 1.0)
            mix(first.color, second.color, amount)
        }
        first != null -> first.color
        second != null -> second.color
        else -> null
    }

    private fun medianColor(colors: List<Int>): Int {
        require(colors.isNotEmpty())
        fun channel(shift: Int): Int = median(colors.map { it ushr shift and 0xff })
        return channel(24) shl 24 or
            (channel(16) shl 16) or
            (channel(8) shl 8) or
            channel(0)
    }

    private fun median(values: List<Int>): Int {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle]
        else (sorted[middle - 1] + sorted[middle]) / 2
    }

    private fun mix(first: Int, second: Int, amount: Double): Int {
        fun channel(shift: Int): Int {
            val a = first ushr shift and 0xff
            val b = second ushr shift and 0xff
            return (a + (b - a) * amount).roundToInt().coerceIn(0, 255)
        }
        return channel(24) shl 24 or
            (channel(16) shl 16) or
            (channel(8) shl 8) or
            channel(0)
    }

    private fun validateRegion(region: RepairRegion, imageWidth: Int, imageHeight: Int) {
        val right = region.x.toLong() + region.width.toLong()
        val bottom = region.y.toLong() + region.height.toLong()
        require(right <= imageWidth.toLong() && bottom <= imageHeight.toLong()) {
            "Repair region must be completely inside the image"
        }
        require(region.width < imageWidth || region.height < imageHeight) {
            "Repair region cannot cover the entire image"
        }
    }

    private fun sameFile(source: Path, output: Path): Boolean {
        if (source == output) return true
        if (Files.exists(output) && Files.isSameFile(source, output)) return true
        val sourceText = source.toString()
        val outputText = output.toString()
        return System.getProperty("os.name").startsWith("Windows", ignoreCase = true) &&
            sourceText.equals(outputText, ignoreCase = true)
    }

    private fun detectFormat(path: Path): ImageFormat? {
        ImageIO.createImageInputStream(path.toFile()).use { input ->
            if (input == null) return null
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) return null
            val reader = readers.next()
            return try {
                when (reader.formatName.lowercase()) {
                    "png" -> ImageFormat.PNG
                    "jpeg", "jpg" -> ImageFormat.JPEG
                    else -> null
                }
            } finally {
                reader.dispose()
            }
        }
    }

    private fun formatFromExtension(path: Path): ImageFormat? = when (
        path.fileName.toString().substringAfterLast('.', missingDelimiterValue = "").lowercase()
    ) {
        "png" -> ImageFormat.PNG
        "jpg", "jpeg" -> ImageFormat.JPEG
        else -> null
    }

    private fun BufferedImage.toOpaqueRgb(): BufferedImage {
        if (type == BufferedImage.TYPE_INT_RGB) return this
        return BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).also { target ->
            val graphics = target.createGraphics()
            try {
                graphics.color = Color.WHITE
                graphics.fillRect(0, 0, width, height)
                graphics.drawImage(this, 0, 0, null)
            } finally {
                graphics.dispose()
            }
        }
    }

    private data class BoundarySample(val color: Int, val coordinate: Double)

    private enum class ImageFormat(val writerName: String) {
        PNG("png"),
        JPEG("jpg")
    }
}
