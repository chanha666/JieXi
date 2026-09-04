package com.yunx.desktop.media

import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dialog
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JScrollPane
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.SwingWorker
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** The letterboxed area occupied by an image inside the preview panel. */
internal data class ImageViewport(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
) {
    val right: Int get() = x + width
    val bottom: Int get() = y + height

    operator fun contains(point: Point): Boolean =
        point.x in x..right && point.y in y..bottom
}

/**
 * Converts a preview-space drag rectangle into an original-image pixel region.
 * Left/top boundaries round down and right/bottom boundaries round up so that
 * every pixel visibly touched by the selection is included.
 */
internal fun mapPreviewSelection(
    selection: Rectangle,
    viewport: ImageViewport,
    imageWidth: Int,
    imageHeight: Int
): RepairRegion? {
    require(viewport.width > 0 && viewport.height > 0)
    require(imageWidth > 0 && imageHeight > 0)

    val left = max(selection.x, viewport.x)
    val top = max(selection.y, viewport.y)
    val right = min(selection.x + selection.width, viewport.right)
    val bottom = min(selection.y + selection.height, viewport.bottom)
    if (right <= left || bottom <= top) return null

    val sourceLeft = floor((left - viewport.x).toDouble() * imageWidth / viewport.width)
        .toInt()
        .coerceIn(0, imageWidth - 1)
    val sourceTop = floor((top - viewport.y).toDouble() * imageHeight / viewport.height)
        .toInt()
        .coerceIn(0, imageHeight - 1)
    val sourceRight = ceil((right - viewport.x).toDouble() * imageWidth / viewport.width)
        .toInt()
        .coerceIn(sourceLeft + 1, imageWidth)
    val sourceBottom = ceil((bottom - viewport.y).toDouble() * imageHeight / viewport.height)
        .toInt()
        .coerceIn(sourceTop + 1, imageHeight)

    return RepairRegion(
        x = sourceLeft,
        y = sourceTop,
        width = sourceRight - sourceLeft,
        height = sourceBottom - sourceTop
    )
}

/**
 * Native Windows/Swing watermark selector used by the Compose desktop app.
 *
 * [show] is safe to call from either the Swing event thread or a background
 * thread. It returns the newly written image, or `null` when the user cancels.
 */
object WatermarkSelectionDialog {
    private val background = Color(0xF7, 0xF4, 0xEE)
    private val panelBackground = Color(0xFF, 0xFF, 0xFF)
    private val primaryText = Color(0x23, 0x23, 0x23)
    private val secondaryText = Color(0x69, 0x66, 0x61)
    private val accent = Color(0xD9, 0x70, 0x36)

    /** Opens an image chooser and then the selection dialog. */
    @JvmStatic
    fun show(parent: Component?): File? = onEventThread {
        val chooser = JFileChooser().apply {
            dialogTitle = "选择需要去水印的图片"
            fileSelectionMode = JFileChooser.FILES_ONLY
            isAcceptAllFileFilterUsed = false
            fileFilter = FileNameExtensionFilter("图片文件（PNG、JPG、JPEG）", "png", "jpg", "jpeg")
            accessibleContext.accessibleDescription = "选择一张 PNG 或 JPEG 图片"
        }
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) null
        else open(parent, chooser.selectedFile)
    }

    /** Opens the selector for [source] and returns the collision-free output. */
    @JvmStatic
    fun show(parent: Component?, source: File): File? = onEventThread { open(parent, source) }

    private fun open(parent: Component?, source: File): File? {
        val image = try {
            validateAndRead(source)
        } catch (error: Throwable) {
            showError(parent, error)
            return null
        }

        val result = AtomicReference<File?>(null)
        val owner: Window? = when (parent) {
            is Window -> parent
            null -> null
            else -> SwingUtilities.getWindowAncestor(parent)
        }
        val dialog = JDialog(owner, "图片去水印", Dialog.ModalityType.APPLICATION_MODAL).apply {
            defaultCloseOperation = JDialog.DO_NOTHING_ON_CLOSE
            layout = BorderLayout()
            contentPane.background = background
        }

        val title = JLabel("框选需要修复的区域").apply {
            font = font.deriveFont(Font.BOLD, 20f)
            foreground = primaryText
        }
        val subtitle = JLabel("按住鼠标拖动框选水印，保存后会生成新文件，不会覆盖原图。").apply {
            font = font.deriveFont(13f)
            foreground = secondaryText
        }
        val sourceLabel = JLabel("原图：${source.name}  ·  ${image.width} × ${image.height} 像素").apply {
            font = font.deriveFont(12f)
            foreground = secondaryText
            toolTipText = source.absolutePath
        }
        val header = JPanel().apply {
            background = background
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(20, 22, 14, 22)
            title.alignmentX = Component.LEFT_ALIGNMENT
            subtitle.alignmentX = Component.LEFT_ALIGNMENT
            sourceLabel.alignmentX = Component.LEFT_ALIGNMENT
            add(title)
            add(javax.swing.Box.createVerticalStrut(5))
            add(subtitle)
            add(javax.swing.Box.createVerticalStrut(8))
            add(sourceLabel)
        }

        val coordinateLabel = JLabel("尚未框选", SwingConstants.LEFT).apply {
            foreground = secondaryText
            font = font.deriveFont(Font.PLAIN, 13f)
            accessibleContext.accessibleName = "当前选区像素坐标"
        }
        val preview = SelectionPanel(image) { region ->
            coordinateLabel.text = if (region == null) {
                "尚未框选"
            } else {
                "像素选区：X ${region.x}  ·  Y ${region.y}  ·  宽 ${region.width}  ·  高 ${region.height}"
            }
        }
        val previewScroll = JScrollPane(preview).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 22, 0, 22),
                BorderFactory.createLineBorder(Color(0xDF, 0xDB, 0xD3), 1, true)
            )
            viewport.background = panelBackground
            horizontalScrollBar.unitIncrement = 18
            verticalScrollBar.unitIncrement = 18
        }

        val resetButton = JButton("重新框选").apply {
            isEnabled = false
            toolTipText = "清除当前选区后重新拖动"
            addActionListener { preview.clearSelection() }
        }
        val cancelButton = JButton("取消").apply {
            addActionListener { dialog.dispose() }
        }
        val saveButton = JButton("保存修复图片").apply {
            isEnabled = false
            foreground = Color.WHITE
            background = accent
            isOpaque = true
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(accent.darker(), 1, true),
                BorderFactory.createEmptyBorder(7, 14, 7, 14)
            )
        }
        val progress = JProgressBar().apply {
            isIndeterminate = true
            isVisible = false
            preferredSize = Dimension(90, 8)
            accessibleContext.accessibleName = "正在修复图片"
        }
        preview.onSelectionStateChanged = { hasSelection ->
            resetButton.isEnabled = hasSelection
            saveButton.isEnabled = hasSelection
        }

        val footer = JPanel(BorderLayout()).apply {
            background = background
            border = BorderFactory.createEmptyBorder(14, 22, 18, 22)
            add(coordinateLabel, BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                background = background
                add(progress)
                add(resetButton)
                add(cancelButton)
                add(saveButton)
            }, BorderLayout.EAST)
        }

        var saving = false
        fun setSaving(value: Boolean) {
            saving = value
            progress.isVisible = value
            preview.isEnabled = !value
            resetButton.isEnabled = !value && preview.selectedRegion != null
            cancelButton.isEnabled = !value
            saveButton.isEnabled = !value && preview.selectedRegion != null
            coordinateLabel.text = if (value) "正在修复并保存，请稍候…" else preview.coordinateText()
        }

        saveButton.addActionListener {
            val region = preview.selectedRegion ?: return@addActionListener
            if (region.width == image.width && region.height == image.height) {
                JOptionPane.showMessageDialog(
                    dialog,
                    "选区不能覆盖整张图片，请只框选水印所在区域。",
                    "无法保存",
                    JOptionPane.WARNING_MESSAGE
                )
                return@addActionListener
            }
            setSaving(true)
            object : SwingWorker<Path, Unit>() {
                override fun doInBackground(): Path = ImageWatermarkRepair.repair(source.toPath(), region)

                override fun done() {
                    try {
                        val output = get().toFile()
                        result.set(output)
                        JOptionPane.showMessageDialog(
                            dialog,
                            "修复完成，已保存为：\n${output.absolutePath}",
                            "保存成功",
                            JOptionPane.INFORMATION_MESSAGE
                        )
                        dialog.dispose()
                    } catch (error: Throwable) {
                        setSaving(false)
                        showError(dialog, error)
                    }
                }
            }.execute()
        }

        dialog.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(event: WindowEvent) {
                if (!saving) dialog.dispose()
            }
        })
        dialog.add(header, BorderLayout.NORTH)
        dialog.add(previewScroll, BorderLayout.CENTER)
        dialog.add(footer, BorderLayout.SOUTH)
        dialog.rootPane.defaultButton = saveButton

        val usable = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        dialog.size = Dimension(
            min(980, max(420, usable.width - 80)).coerceAtMost(usable.width),
            min(760, max(360, usable.height - 80)).coerceAtMost(usable.height)
        )
        dialog.minimumSize = Dimension(min(620, dialog.width), min(500, dialog.height))
        dialog.setLocationRelativeTo(parent)
        dialog.isVisible = true
        return result.get()
    }

    private fun validateAndRead(source: File): BufferedImage {
        require(source.isFile) { "图片文件不存在：${source.absolutePath}" }
        require(source.canRead()) { "图片文件无法读取：${source.absolutePath}" }
        val extension = source.extension.lowercase()
        require(extension in setOf("png", "jpg", "jpeg")) { "仅支持 PNG、JPG 和 JPEG 图片" }
        return ImageIO.read(source) ?: throw IllegalArgumentException("无法识别或图片文件已损坏")
    }

    private fun showError(parent: Component?, error: Throwable) {
        val root = unwrap(error)
        val detail = root.message?.trim().orEmpty()
        val message = when {
            detail.contains("does not exist", ignoreCase = true) -> "图片文件不存在。"
            detail.contains("not readable", ignoreCase = true) -> "图片文件无法读取。"
            detail.contains("entire image", ignoreCase = true) -> "选区不能覆盖整张图片。"
            detail.contains("inside the image", ignoreCase = true) -> "选区超出图片范围，请重新框选。"
            detail.contains("already exists", ignoreCase = true) -> "目标文件已经存在，请重新保存。"
            detail.contains("PNG", ignoreCase = true) || detail.contains("JPEG", ignoreCase = true) -> detail
            detail.isNotEmpty() -> "处理失败：$detail"
            else -> "处理失败，请确认图片有效后重试。"
        }
        JOptionPane.showMessageDialog(parent, message, "图片处理失败", JOptionPane.ERROR_MESSAGE)
    }

    private fun unwrap(error: Throwable): Throwable {
        var current = error
        while ((current is ExecutionException || current is java.lang.reflect.InvocationTargetException) && current.cause != null) {
            current = current.cause!!
        }
        return current
    }

    private fun <T> onEventThread(block: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return block()
        val result = AtomicReference<T>()
        val failure = AtomicReference<Throwable?>()
        SwingUtilities.invokeAndWait {
            try {
                result.set(block())
            } catch (error: Throwable) {
                failure.set(error)
            }
        }
        failure.get()?.let { throw it }
        return result.get()
    }

    private class SelectionPanel(
        private val image: BufferedImage,
        private val onRegionChanged: (RepairRegion?) -> Unit
    ) : JPanel() {
        private var dragStart: Point? = null
        private var dragEnd: Point? = null
        private var region: RepairRegion? = null
        var onSelectionStateChanged: (Boolean) -> Unit = {}

        val selectedRegion: RepairRegion? get() = region

        init {
            background = panelBackground
            preferredSize = Dimension(720, 480)
            minimumSize = Dimension(280, 220)
            cursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
            toolTipText = "按住鼠标拖动，框选需要去除的水印"
            accessibleContext.accessibleName = "图片水印框选区域"
            accessibleContext.accessibleDescription = "在图片上按住鼠标拖动来框选水印"

            val mouse = object : MouseAdapter() {
                override fun mousePressed(event: MouseEvent) {
                    if (!isEnabled || !viewport().contains(event.point)) return
                    dragStart = clampToViewport(event.point)
                    dragEnd = dragStart
                    region = null
                    publishSelection(null)
                    repaint()
                }

                override fun mouseDragged(event: MouseEvent) {
                    if (!isEnabled || dragStart == null) return
                    dragEnd = clampToViewport(event.point)
                    val mapped = currentDragRectangle()?.let(::mapToSource)
                    onRegionChanged(mapped)
                    repaint()
                }

                override fun mouseReleased(event: MouseEvent) {
                    if (!isEnabled || dragStart == null) return
                    dragEnd = clampToViewport(event.point)
                    region = currentDragRectangle()?.let(::mapToSource)
                    dragStart = null
                    dragEnd = null
                    publishSelection(region)
                    repaint()
                }
            }
            addMouseListener(mouse)
            addMouseMotionListener(mouse)
            addComponentListener(object : ComponentAdapter() {
                override fun componentResized(event: ComponentEvent) = repaint()
            })
        }

        fun clearSelection() {
            dragStart = null
            dragEnd = null
            region = null
            publishSelection(null)
            repaint()
        }

        fun coordinateText(): String = region?.let {
            "像素选区：X ${it.x}  ·  Y ${it.y}  ·  宽 ${it.width}  ·  高 ${it.height}"
        } ?: "尚未框选"

        override fun paintComponent(graphics: Graphics) {
            super.paintComponent(graphics)
            val g = graphics.create() as Graphics2D
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val viewport = viewport()
                g.drawImage(image, viewport.x, viewport.y, viewport.width, viewport.height, null)

                val selection = currentDragRectangle() ?: region?.let { regionToPreview(it, viewport) }
                if (selection != null && selection.width > 0 && selection.height > 0) {
                    g.color = Color(0, 0, 0, 92)
                    g.fillRect(viewport.x, viewport.y, viewport.width, max(0, selection.y - viewport.y))
                    g.fillRect(
                        viewport.x,
                        selection.y + selection.height,
                        viewport.width,
                        max(0, viewport.bottom - selection.y - selection.height)
                    )
                    g.fillRect(viewport.x, selection.y, max(0, selection.x - viewport.x), selection.height)
                    g.fillRect(
                        selection.x + selection.width,
                        selection.y,
                        max(0, viewport.right - selection.x - selection.width),
                        selection.height
                    )
                    g.color = Color.WHITE
                    g.stroke = BasicStroke(4f)
                    g.drawRect(selection.x, selection.y, selection.width, selection.height)
                    g.color = accent
                    g.stroke = BasicStroke(2f)
                    g.drawRect(selection.x, selection.y, selection.width, selection.height)
                }
            } finally {
                g.dispose()
            }
        }

        private fun viewport(): ImageViewport {
            val padding = 18
            val availableWidth = max(1, width - padding * 2)
            val availableHeight = max(1, height - padding * 2)
            val scale = min(
                availableWidth.toDouble() / image.width,
                availableHeight.toDouble() / image.height
            )
            val displayWidth = max(1, floor(image.width * scale).toInt())
            val displayHeight = max(1, floor(image.height * scale).toInt())
            return ImageViewport(
                x = (width - displayWidth) / 2,
                y = (height - displayHeight) / 2,
                width = displayWidth,
                height = displayHeight
            )
        }

        private fun clampToViewport(point: Point): Point {
            val viewport = viewport()
            return Point(
                point.x.coerceIn(viewport.x, viewport.right),
                point.y.coerceIn(viewport.y, viewport.bottom)
            )
        }

        private fun currentDragRectangle(): Rectangle? {
            val start = dragStart ?: return null
            val end = dragEnd ?: return null
            return Rectangle(
                min(start.x, end.x),
                min(start.y, end.y),
                kotlin.math.abs(end.x - start.x),
                kotlin.math.abs(end.y - start.y)
            ).takeIf { it.width > 0 && it.height > 0 }
        }

        private fun mapToSource(selection: Rectangle): RepairRegion? = mapPreviewSelection(
            selection = selection,
            viewport = viewport(),
            imageWidth = image.width,
            imageHeight = image.height
        )

        private fun regionToPreview(region: RepairRegion, viewport: ImageViewport): Rectangle {
            val left = viewport.x + floor(region.x.toDouble() * viewport.width / image.width).toInt()
            val top = viewport.y + floor(region.y.toDouble() * viewport.height / image.height).toInt()
            val right = viewport.x + ceil(
                (region.x + region.width).toDouble() * viewport.width / image.width
            ).toInt()
            val bottom = viewport.y + ceil(
                (region.y + region.height).toDouble() * viewport.height / image.height
            ).toInt()
            return Rectangle(left, top, max(1, right - left), max(1, bottom - top))
        }

        private fun publishSelection(selection: RepairRegion?) {
            onRegionChanged(selection)
            onSelectionStateChanged(selection != null)
        }
    }
}
