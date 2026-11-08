package com.z.diabetescompanion.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.z.diabetescompanion.data.model.GlucoseReading
import com.z.diabetescompanion.data.model.Thresholds
import com.z.diabetescompanion.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Line chart over time with the doctor's target band shaded behind it. Pure Canvas, no
 * chart library. Handles 0, 1 and many points; axis labels are drawn with the native canvas.
 */
@Composable
fun GlucoseChart(
    readings: List<GlucoseReading>,
    thresholds: Thresholds?,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    accentColor: Color = MaterialTheme.colorScheme.tertiary,
    showAxes: Boolean = true,
    rangeStart: Date? = null,
    rangeEnd: Date? = null,
) {
    val sorted = remember(readings) { readings.sortedBy { it.recordedAt.time } }
    val bandColor = AppTheme.clinical.inRangeContainer
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current
    val labelPx = with(density) { 11.sp.toPx() }
    val dateFormat = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }

    Box(modifier = modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        if (sorted.isEmpty()) return@Box

        Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            val leftPad = if (showAxes) labelPx * 3.2f else 0f
            val bottomPad = if (showAxes) labelPx * 2f else 0f
            val plot = Rect(
                left = leftPad,
                top = labelPx * 0.6f,
                right = size.width - labelPx * 0.5f,
                bottom = size.height - bottomPad,
            )

            val values = sorted.map { it.valueMgdl }
            var minY = floor((minOf(values.min(), thresholds?.targetLow?.toDouble() ?: values.min()) - 10) / 20) * 20
            var maxY = ceil((maxOf(values.max(), thresholds?.targetHigh?.toDouble() ?: values.max()) + 10) / 20) * 20
            if (maxY - minY < 60) { maxY = minY + 60 }
            minY = minY.coerceAtLeast(0.0)

            val startMs = rangeStart?.time ?: sorted.first().recordedAt.time
            val endMs = rangeEnd?.time ?: sorted.last().recordedAt.time
            val spanMs = (endMs - startMs).coerceAtLeast(1L)

            fun x(ms: Long) = plot.left + ((ms - startMs).toFloat() / spanMs) * plot.width
            fun y(v: Double) = plot.bottom - ((v - minY) / (maxY - minY)).toFloat() * plot.height

            // Target band
            if (thresholds != null) {
                val top = y(thresholds.targetHigh.toDouble()).coerceIn(plot.top, plot.bottom)
                val bottom = y(thresholds.targetLow.toDouble()).coerceIn(plot.top, plot.bottom)
                drawRect(
                    color = bandColor,
                    topLeft = Offset(plot.left, top),
                    size = Size(plot.width, (bottom - top).coerceAtLeast(0f)),
                )
            }

            // Grid + y labels
            val steps = 4
            val paint = android.graphics.Paint().apply {
                color = labelColor.toArgb()
                textSize = labelPx
                isAntiAlias = true
            }
            for (i in 0..steps) {
                val v = minY + (maxY - minY) * i / steps
                val yy = y(v)
                drawLine(gridColor, Offset(plot.left, yy), Offset(plot.right, yy), strokeWidth = 1f)
                if (showAxes) {
                    paint.textAlign = android.graphics.Paint.Align.RIGHT
                    drawContext.canvas.nativeCanvas.drawText(v.toInt().toString(), plot.left - labelPx * 0.4f, yy + labelPx * 0.35f, paint)
                }
            }

            // x labels: first, middle, last
            if (showAxes) {
                paint.textAlign = android.graphics.Paint.Align.CENTER
                val labels = listOf(startMs, (startMs + endMs) / 2, endMs).distinct()
                labels.forEach { ms ->
                    drawContext.canvas.nativeCanvas.drawText(
                        dateFormat.format(Date(ms)), x(ms).coerceIn(plot.left + labelPx, plot.right - labelPx),
                        size.height - labelPx * 0.4f, paint,
                    )
                }
            }

            // Line
            val points = sorted.map { Offset(x(it.recordedAt.time), y(it.valueMgdl)) }
            if (points.size >= 2) {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        val p = points[i - 1]; val c = points[i]
                        val mid = (p.x + c.x) / 2
                        cubicTo(mid, p.y, mid, c.y, c.x, c.y)
                    }
                }
                drawPath(path, lineColor, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            points.forEach { drawCircle(lineColor, radius = 3.5.dp.toPx(), center = it) }
            // Latest reading gets the drop's red so the eye lands on "now".
            points.lastOrNull()?.let {
                drawCircle(accentColor, radius = 5.dp.toPx(), center = it)
                drawCircle(Color.White, radius = 2.dp.toPx(), center = it)
            }
        }
    }
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()
)

private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
