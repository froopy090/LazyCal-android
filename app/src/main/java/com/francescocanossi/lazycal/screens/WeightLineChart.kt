package com.francescocanossi.lazycal.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.francescocanossi.lazycal.WeightEntry
import kotlin.math.sqrt

@Composable
fun WeightLineChart(history: List<WeightEntry>) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    
    if (history.isEmpty()) return

    val weights = remember(history) { history.map { it.weight } }
    val minW = (weights.minOrNull() ?: 0.0) - 2.0
    val maxW = (weights.maxOrNull() ?: 0.0) + 2.0
    val weightRange = (maxW - minW).coerceAtLeast(1.0)

    Box(modifier = Modifier
        .fillMaxWidth()
        .height(200.dp)
        .pointerInput(history) {
            detectTapGestures { offset ->
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                
                var foundIndex: Int? = null
                history.forEachIndexed { index, wEntry ->
                    val x = if (history.size > 1) (index.toFloat() / (history.size - 1)) * w else w / 2
                    val ratio = (wEntry.weight - minW) / weightRange
                    val y = h - (ratio.toFloat() * h)
                    
                    val dx = offset.x - x
                    val dy = offset.y - y
                    if (sqrt(dx * dx + dy * dy) < 25.dp.toPx()) {
                        foundIndex = index
                    }
                }
                selectedIndex = foundIndex
            }
        }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            
            val points = history.mapIndexed { index, wEntry ->
                val x = if (history.size > 1) (index.toFloat() / (history.size - 1)) * w else w / 2
                val ratio = (wEntry.weight - minW) / weightRange
                val y = h - (ratio.toFloat() * h)
                Offset(x, y)
            }
            
            val path = Path().apply {
                if (points.isNotEmpty()) {
                    moveTo(points[0].x, points[0].y)
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                }
            }
            
            drawPath(path = path, color = primaryColor, style = Stroke(width = 3.dp.toPx()))
            
            points.forEachIndexed { index, point ->
                val isSelected = selectedIndex == index
                drawCircle(
                    color = primaryColor,
                    radius = if (isSelected) 7.dp.toPx() else 4.dp.toPx(),
                    center = point
                )
                
                if (isSelected) {
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.3f),
                        radius = 10.dp.toPx(),
                        center = point
                    )
                    
                    drawContext.canvas.nativeCanvas.drawText(
                        "${history[index].weight} kg",
                        point.x,
                        point.y - 18.dp.toPx(),
                        Paint().apply {
                            this.color = labelColor
                            this.textSize = 12.sp.toPx()
                            this.textAlign = Paint.Align.CENTER
                            this.isFakeBoldText = true
                        }
                    )
                }
            }

            drawContext.canvas.nativeCanvas.apply {
                val paint = Paint().apply {
                    color = labelColor
                    textSize = 30f
                }
                drawText("${maxW.toInt()} kg", 0f, 30f, paint)
                drawText("${minW.toInt()} kg", 0f, h, paint)
            }
        }
    }
}
