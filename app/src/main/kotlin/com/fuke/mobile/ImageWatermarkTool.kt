package com.fuke.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val WatermarkOrange = Color(0xFFC65A12)
private val WatermarkCream = Color(0xFFFFF8EF)
private val WatermarkPaper = Color(0xFFFFFEFC)
private val WatermarkLine = Color(0xFFE8D8C6)
private const val MAX_EDIT_DIMENSION = 3072

@Composable
fun ImageWatermarkDialog(
    context: Context,
    source: File,
    onDismiss: () -> Unit,
    onSaved: (Uri) -> Unit
) {
    val bitmapResult = remember(source) { runCatching { decodeEditableBitmap(source) } }
    val bitmap = bitmapResult.getOrNull()
    val scope = rememberCoroutineScope()
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    var processing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(bitmapResult.exceptionOrNull()?.message.orEmpty()) }

    Dialog(onDismissRequest = { if (!processing) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = WatermarkPaper)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("图片去水印", fontSize = 21.sp)
                Text("用手指框住水印，边框尽量贴近水印；处理只修改框内区域。", fontSize = 11.sp, color = Color(0xFF765F50))
                if (bitmap != null) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .background(WatermarkCream, RoundedCornerShape(14.dp))
                            .onSizeChanged { canvasSize = it }
                            .pointerInput(bitmap, canvasSize) {
                                detectDragGestures(
                                    onDragStart = { point ->
                                        val rect = fittedImageRect(size, bitmap.width, bitmap.height)
                                        dragStart = point.clampTo(rect)
                                        dragEnd = point.clampTo(rect)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val rect = fittedImageRect(size, bitmap.width, bitmap.height)
                                        dragEnd = change.position.clampTo(rect)
                                    }
                                )
                            }
                    ) {
                        val rect = fittedImageRect(size, bitmap.width, bitmap.height)
                        drawImage(
                            image = bitmap.asImageBitmap(),
                            dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                            dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt())
                        )
                        val start = dragStart
                        val end = dragEnd
                        if (start != null && end != null) {
                            val selection = normalizedRect(start, end)
                            drawRect(WatermarkOrange.copy(alpha = .16f), selection.topLeft, selection.size)
                            drawRect(WatermarkOrange, selection.topLeft, selection.size, style = Stroke(width = 3.dp.toPx()))
                        }
                    }
                } else {
                    Text(error.ifBlank { "无法读取这张图片。" }, color = Color(0xFFB3261E), modifier = Modifier.padding(vertical = 24.dp))
                }
                if (error.isNotBlank() && bitmap != null) Text(error, color = Color(0xFFB3261E), fontSize = 11.sp)
                Text("说明：采用本地边缘像素智能填充，不上传图片；复杂纹理可能需要分几次框选。", fontSize = 10.sp, color = Color(0xFF765F50))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onDismiss, enabled = !processing, modifier = Modifier.weight(1f)) { Text("取消") }
                    Button(
                        onClick = {
                            val start = dragStart
                            val end = dragEnd
                            if (bitmap == null || start == null || end == null || canvasSize == IntSize.Zero) {
                                error = "请先在图片上框住水印。"
                                return@Button
                            }
                            val imageRect = fittedImageRect(canvasSize, bitmap.width, bitmap.height)
                            val selected = normalizedRect(start, end)
                            val pixelRect = selectionToPixels(selected, imageRect, bitmap.width, bitmap.height)
                            if (pixelRect.width() < 6 || pixelRect.height() < 6) {
                                error = "框选区域太小，请重新框住完整水印。"
                                return@Button
                            }
                            processing = true
                            error = ""
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.Default) { removeWatermark(bitmap, pixelRect) }
                                }.mapCatching { result ->
                                    withContext(Dispatchers.IO) {
                                        val output = File(context.cacheDir, "解析去水印-${System.currentTimeMillis()}.jpg")
                                        FileOutputStream(output).use { stream ->
                                            check(result.compress(Bitmap.CompressFormat.JPEG, 96, stream)) { "图片编码失败。" }
                                        }
                                        val uri = MediaFiles.publish(context, output)
                                        output.delete()
                                        source.delete()
                                        uri
                                    }
                                }.onSuccess(onSaved)
                                    .onFailure { error = it.message ?: "图片处理失败。" }
                                processing = false
                            }
                        },
                        enabled = bitmap != null && !processing,
                        modifier = Modifier.weight(1.45f),
                        colors = ButtonDefaults.buttonColors(containerColor = WatermarkOrange)
                    ) {
                        if (processing) {
                            CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Text("处理中")
                        } else Text("去除并保存")
                    }
                }
            }
        }
    }
}

private fun decodeEditableBitmap(source: File): Bitmap {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取这张图片。" }
        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_EDIT_DIMENSION) sampleSize *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
        }
        val decoded = checkNotNull(BitmapFactory.decodeFile(source.absolutePath, options)) { "无法读取这张图片。" }
        if (decoded.isMutable && decoded.config == Bitmap.Config.ARGB_8888) return decoded
        return decoded.copy(Bitmap.Config.ARGB_8888, true).also { decoded.recycle() }
    }
    return decodeEditableBitmapModern(source)
}

@RequiresApi(Build.VERSION_CODES.P)
private fun decodeEditableBitmapModern(source: File): Bitmap {
    val decoderSource = ImageDecoder.createSource(source)
    return ImageDecoder.decodeBitmap(decoderSource) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.isMutableRequired = true
        val width = info.size.width
        val height = info.size.height
        val longest = max(width, height)
        if (longest > MAX_EDIT_DIMENSION) {
            val scale = MAX_EDIT_DIMENSION.toFloat() / longest
            decoder.setTargetSize((width * scale).roundToInt(), (height * scale).roundToInt())
        }
    }
}

private fun fittedImageRect(size: IntSize, imageWidth: Int, imageHeight: Int): Rect =
    fittedImageRect(androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()), imageWidth, imageHeight)

private fun fittedImageRect(size: androidx.compose.ui.geometry.Size, imageWidth: Int, imageHeight: Int): Rect {
    if (size.width <= 0f || size.height <= 0f) return Rect.Zero
    val scale = min(size.width / imageWidth, size.height / imageHeight)
    val width = imageWidth * scale
    val height = imageHeight * scale
    return Rect((size.width - width) / 2f, (size.height - height) / 2f, (size.width + width) / 2f, (size.height + height) / 2f)
}

private fun Offset.clampTo(rect: Rect) = Offset(x.coerceIn(rect.left, rect.right), y.coerceIn(rect.top, rect.bottom))

private fun normalizedRect(a: Offset, b: Offset) = Rect(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))

private fun selectionToPixels(selection: Rect, imageRect: Rect, width: Int, height: Int): android.graphics.Rect {
    val scaleX = width / imageRect.width
    val scaleY = height / imageRect.height
    val left = floor((selection.left - imageRect.left) * scaleX).toInt().coerceIn(0, width - 1)
    val top = floor((selection.top - imageRect.top) * scaleY).toInt().coerceIn(0, height - 1)
    val right = ceil((selection.right - imageRect.left) * scaleX).toInt().coerceIn(left + 1, width)
    val bottom = ceil((selection.bottom - imageRect.top) * scaleY).toInt().coerceIn(top + 1, height)
    return android.graphics.Rect(left, top, right, bottom)
}

internal fun removeWatermark(source: Bitmap, selected: android.graphics.Rect): Bitmap {
    val output = source.copy(Bitmap.Config.ARGB_8888, true)
    val padding = max(3, min(source.width, source.height) / 500)
    val left = (selected.left - padding).coerceAtLeast(1)
    val top = (selected.top - padding).coerceAtLeast(1)
    val right = (selected.right + padding).coerceAtMost(source.width - 1)
    val bottom = (selected.bottom + padding).coerceAtMost(source.height - 1)
    val width = (right - left).coerceAtLeast(1)
    val height = (bottom - top).coerceAtLeast(1)
    val horizontalWeight = height.toFloat() / (width + height)

    for (y in top until bottom) {
        val verticalFraction = (y - top + 1f) / (height + 1f)
        val leftColor = source.getPixel(left - 1, y)
        val rightColor = source.getPixel(right, y)
        for (x in left until right) {
            val horizontalFraction = (x - left + 1f) / (width + 1f)
            val topColor = source.getPixel(x, top - 1)
            val bottomColor = source.getPixel(x, bottom)
            val horizontal = blendColor(leftColor, rightColor, horizontalFraction)
            val vertical = blendColor(topColor, bottomColor, verticalFraction)
            output.setPixel(x, y, blendColor(horizontal, vertical, horizontalWeight))
        }
    }
    return output
}

private fun blendColor(first: Int, second: Int, fraction: Float): Int {
    val t = fraction.coerceIn(0f, 1f)
    val inverse = 1f - t
    val a = ((first ushr 24 and 0xff) * inverse + (second ushr 24 and 0xff) * t).roundToInt()
    val r = ((first ushr 16 and 0xff) * inverse + (second ushr 16 and 0xff) * t).roundToInt()
    val g = ((first ushr 8 and 0xff) * inverse + (second ushr 8 and 0xff) * t).roundToInt()
    val b = ((first and 0xff) * inverse + (second and 0xff) * t).roundToInt()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
