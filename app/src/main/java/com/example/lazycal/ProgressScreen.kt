package com.example.lazycal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLocale
import com.example.lazycal.ui.theme.LazyCalTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun ProgressScreen(viewModel: ProgressViewModel, chatViewModel: ChatViewModel) {
    val streak by viewModel.currentStreak.collectAsState()
    val summaries by viewModel.daySummaries.collectAsState()
    val userConfig by viewModel.userConfig.collectAsState()
    val entries by chatViewModel.foodEntries.collectAsState()

    val cardColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Progress",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // 1. Top Cards Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val totalProtein = entries.sumOf { it.protein }
            val totalCarbs = entries.sumOf { it.carbs }
            val totalFats = entries.sumOf { it.fats }
            
            MacroCircleCard(
                modifier = Modifier.weight(1.2f),
                protein = totalProtein,
                carbs = totalCarbs,
                fats = totalFats,
                backgroundColor = cardColor
            )
            
            StreakProgressCard(
                modifier = Modifier.weight(0.8f),
                streak = streak,
                backgroundColor = cardColor
            )
        }

        // 2. Calorie Trend Line Chart
        CalorieTrendCard(
            summaries = summaries,
            goal = userConfig.dailyCalorieGoal,
            backgroundColor = cardColor
        )
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun MacroCircleCard(modifier: Modifier, protein: Int, carbs: Int, fats: Int, backgroundColor: Color) {
    val macroColors = LazyCalTheme.colors
    
    val total = remember(protein, carbs, fats) { (protein + carbs + fats).toFloat().coerceAtLeast(1f) }
    val pRatio = remember(protein, total) { protein / total }
    val cRatio = remember(carbs, total) { carbs / total }
    val fRatio = remember(fats, total) { fats / total }

    Card(
        modifier = modifier.height(200.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 10.dp.toPx()
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                    
                    drawArc(
                        color = macroColors.protein,
                        startAngle = -90f,
                        sweepAngle = 360f * pRatio,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(strokeWidth)
                    )
                    drawArc(
                        color = macroColors.carbs,
                        startAngle = -90f + (360f * pRatio),
                        sweepAngle = 360f * cRatio,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(strokeWidth)
                    )
                    drawArc(
                        color = macroColors.fats,
                        startAngle = -90f + (360f * (pRatio + cRatio)),
                        sweepAngle = 360f * fRatio,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(strokeWidth)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${total.toInt()}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("total g", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MacroLegendItem(macroColors.protein, "Protein", "${protein}g")
                MacroLegendItem(macroColors.carbs, "Carbs", "${carbs}g")
                MacroLegendItem(macroColors.fats, "Fats", "${fats}g")
            }
        }
    }
}

@Composable
fun MacroLegendItem(color: Color, label: String, amount: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(amount, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StreakProgressCard(modifier: Modifier, streak: Int, backgroundColor: Color) {
    val themeColors = LazyCalTheme.colors
    Card(
        modifier = modifier.height(200.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "🔥", fontSize = 44.sp)
            Text(
                text = streak.toString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = themeColors.fats
            )
            Text(text = "Day Streak", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { i ->
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (i < (streak % 7)) themeColors.fats else Color.LightGray.copy(0.4f))
                    )
                }
            }
        }
    }
}

@Composable
fun CalorieTrendCard(summaries: List<DaySummary>, goal: Int, backgroundColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Calorie Progress", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = "Goal: $goal", style = MaterialTheme.typography.labelSmall)
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            CalorieLineChart(summaries, goal)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun CalorieLineChart(summaries: List<DaySummary>, goal: Int) {
    val themeColors = LazyCalTheme.colors
    val locale = LocalLocale.current.platformLocale
    
    val data = remember(summaries, locale) {
        val df = SimpleDateFormat("yyyy-MM-dd", locale)
        val dayFormat = SimpleDateFormat("E", locale)
        val summaryMap = summaries.associateBy { it.dayId }
        
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -6)
        
        (0..6).map {
            val dateStr = df.format(calendar.time)
            val label = dayFormat.format(calendar.time).first().toString()
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            label to (summaryMap[dateStr] ?: DaySummary(dateStr, 0, 0, 0, 0)).totalCalories
        }
    }

    val maxDataVal = remember(data) { data.maxOf { it.second }.toFloat() }
    val yMax = remember(maxDataVal, goal) { (maxDataVal.coerceAtLeast(goal.toFloat()) * 1.3f).coerceAtLeast(1000f) }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val paddingLeft = 40.dp.toPx()
            val paddingBottom = 30.dp.toPx()
            val chartWidth = width - paddingLeft
            val chartHeight = height - paddingBottom
            val spacing = if (data.size > 1) chartWidth / (data.size - 1) else chartWidth
            
            fun getStepY(value: Float) = chartHeight - (value / yMax * chartHeight)

            // Draw Y-Axis labels
            val ySteps = 4
            val paint = android.graphics.Paint().apply {
                this.color = labelColor
                this.textSize = 10.sp.toPx()
                this.textAlign = android.graphics.Paint.Align.RIGHT
            }
            for (i in 0..ySteps) {
                val valY = (yMax / ySteps) * i
                val y = getStepY(valY)
                drawContext.canvas.nativeCanvas.drawText(
                    valY.toInt().toString(),
                    paddingLeft - 8.dp.toPx(),
                    y + 4.dp.toPx(),
                    paint
                )
            }

            // Draw Goal Line (Dotted)
            val goalY = getStepY(goal.toFloat())
            drawLine(
                color = Color.Gray.copy(alpha = 0.3f),
                start = Offset(paddingLeft, goalY),
                end = Offset(width, goalY),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f),
                strokeWidth = 1.dp.toPx()
            )

            val points = data.mapIndexed { index, pair ->
                Offset(paddingLeft + index * spacing, getStepY(pair.second.toFloat()))
            }

            if (points.isNotEmpty()) {
                // Smoothing algorithm: Cubic Bezier
                val path = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    for (i in 0 until points.size - 1) {
                        val p0 = points[i]
                        val p1 = points[i + 1]
                        val controlPoint1 = Offset(p0.x + (p1.x - p0.x) / 2f, p0.y)
                        val controlPoint2 = Offset(p0.x + (p1.x - p0.x) / 2f, p1.y)
                        cubicTo(controlPoint1.x, controlPoint1.y, controlPoint2.x, controlPoint2.y, p1.x, p1.y)
                    }
                }

                // Fill area between line and goal line
                val fillPath = Path().apply {
                    moveTo(points[0].x, goalY)
                    lineTo(points[0].x, points[0].y)
                    for (i in 0 until points.size - 1) {
                        val p0 = points[i]
                        val p1 = points[i + 1]
                        val controlPoint1 = Offset(p0.x + (p1.x - p0.x) / 2f, p0.y)
                        val controlPoint2 = Offset(p0.x + (p1.x - p0.x) / 2f, p1.y)
                        cubicTo(controlPoint1.x, controlPoint1.y, controlPoint2.x, controlPoint2.y, p1.x, p1.y)
                    }
                    lineTo(points.last().x, goalY)
                    close()
                }

                // Draw Red shading (Above Goal)
                clipRect(top = 0f, bottom = goalY) {
                    drawPath(fillPath, color = themeColors.errorShading)
                }
                // Draw Green shading (Below Goal)
                clipRect(top = goalY, bottom = chartHeight) {
                    drawPath(fillPath, color = themeColors.successShading)
                }

                // Draw colored lines
                clipRect(top = 0f, bottom = goalY) {
                    drawPath(path, color = themeColors.error, style = Stroke(width = 3.dp.toPx()))
                }
                clipRect(top = goalY, bottom = chartHeight) {
                    drawPath(path, color = themeColors.success, style = Stroke(width = 3.dp.toPx()))
                }
            }

            data.forEachIndexed { index, pair ->
                val x = paddingLeft + index * spacing
                val y = getStepY(pair.second.toFloat())
                val color = if (pair.second > goal) themeColors.error else themeColors.success
                
                drawCircle(
                    color = color,
                    radius = 5.dp.toPx(),
                    center = Offset(x, y)
                )
                
                // X Labels
                drawContext.canvas.nativeCanvas.drawText(
                    pair.first,
                    x,
                    height,
                    android.graphics.Paint().apply {
                        this.color = labelColor
                        this.textSize = 12.sp.toPx()
                        this.textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
            }
        }
    }
}
