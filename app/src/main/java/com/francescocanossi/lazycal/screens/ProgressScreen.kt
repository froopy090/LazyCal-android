package com.francescocanossi.lazycal.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.francescocanossi.lazycal.ui.theme.LazyCalTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.SelectableDates
import java.util.TimeZone
import com.francescocanossi.lazycal.DaySummary
import com.francescocanossi.lazycal.ProgressViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.francescocanossi.lazycal.R
import com.francescocanossi.lazycal.WeightEntry
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(viewModel: ProgressViewModel) {
    val streak by viewModel.currentStreak.collectAsState()
    val summaries by viewModel.daySummaries.collectAsState()
    val userConfig by viewModel.userConfig.collectAsState()
    val weightHistory by viewModel.weightHistory.collectAsState()

    val locale = LocalLocale.current.platformLocale
    val summaryMap = remember(summaries) { summaries.associateBy { it.dayId } }
    val todayStr = remember(locale) { SimpleDateFormat("yyyy-MM-dd", locale).format(java.util.Date()) }
    val weightDays = remember(weightHistory) { weightHistory.map { it.dayId }.toSet() }

    var weightToDelete by remember { mutableStateOf<WeightEntry?>(null) }
    var weightToEdit by remember { mutableStateOf<WeightEntry?>(null) }
    var editWeightValue by remember { mutableStateOf("") }
    var editDateValue by remember { mutableStateOf("") }
    
    var showAddWeightDialog by remember { mutableStateOf(false) }
    var showMacroDetail by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(todayStr) }
    var showMainDatePicker by remember { mutableStateOf(false) }
    var newWeightValue by remember { mutableStateOf("") }
    var newDateValue by remember { mutableStateOf(todayStr) }
    
    val datePickerState = rememberDatePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = utcTimeMillis
                }
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(calendar.time)

                val todayCalendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }

                if (utcTimeMillis > todayCalendar.timeInMillis) return false
                return dateStr == todayStr || summaryMap.containsKey(dateStr) || weightDays.contains(dateStr)
            }
        }
    )
    var showDatePicker by remember { mutableStateOf(false) }
    var datePickerTarget by remember { mutableStateOf("edit") } // "edit" or "add"

    if (showMacroDetail) {
        val selectedSummary = summaryMap[selectedDate]
        MacroDetailDialog(
            protein = selectedSummary?.totalProtein ?: 0,
            carbs = selectedSummary?.totalCarbs ?: 0,
            fats = selectedSummary?.totalFats ?: 0,
            onDismiss = { showMacroDetail = false }
        )
    }

    if (showMainDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showMainDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Date(millis)
                        selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
                    }
                    showMainDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMainDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Date(millis)
                        val formatted = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
                        if (datePickerTarget == "edit") {
                            editDateValue = formatted
                        } else {
                            newDateValue = formatted
                        }
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showAddWeightDialog) {
        AlertDialog(
            onDismissRequest = { showAddWeightDialog = false },
            title = { Text("Add new weight entry") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newWeightValue,
                        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) newWeightValue = it },
                        label = { Text("Weight (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = newDateValue,
                            onValueChange = { },
                            label = { Text("Date") },
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { 
                                    datePickerTarget = "add"
                                    showDatePicker = true 
                                }) {
                                    Icon(painterResource(id = R.drawable.ic_history), contentDescription = "Select Date")
                                }
                            }
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Transparent)
                                .clickable { 
                                    datePickerTarget = "add"
                                    showDatePicker = true 
                                }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val weight = newWeightValue.toDoubleOrNull()
                        if (weight != null) {
                            viewModel.addWeightEntry(weight, newDateValue)
                        }
                        showAddWeightDialog = false
                        newWeightValue = ""
                        newDateValue = todayStr
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddWeightDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (weightToEdit != null) {
        AlertDialog(
            onDismissRequest = { weightToEdit = null },
            title = { Text("Edit weight entry") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editWeightValue,
                        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) editWeightValue = it },
                        label = { Text("Weight (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = editDateValue,
                            onValueChange = { },
                            label = { Text("Date") },
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { 
                                    datePickerTarget = "edit"
                                    showDatePicker = true 
                                }) {
                                    Icon(painterResource(id = R.drawable.ic_history), contentDescription = "Select Date")
                                }
                            }
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Transparent)
                                .clickable { 
                                    datePickerTarget = "edit"
                                    showDatePicker = true 
                                }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newWeight = editWeightValue.toDoubleOrNull()
                        if (newWeight != null && editDateValue.isNotEmpty()) {
                            weightToEdit?.let { 
                                viewModel.updateWeightEntry(it.copy(weight = newWeight, dayId = editDateValue)) 
                            }
                        }
                        weightToEdit = null
                    }
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { weightToEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (weightToDelete != null) {
        AlertDialog(
            onDismissRequest = { weightToDelete = null },
            title = { Text("Delete weight entry?") },
            text = { Text("This will permanently remove this weight record from your history.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        weightToDelete?.let { viewModel.deleteWeightEntry(it) }
                        weightToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { weightToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val weekData = remember(summaryMap, userConfig, locale, selectedDate) {
        val df = SimpleDateFormat("yyyy-MM-dd", locale)
        val calendar = Calendar.getInstance()
        val dateParts = selectedDate.split("-")
        if (dateParts.size == 3) {
            calendar.set(dateParts[0].toInt(), dateParts[1].toInt() - 1, dateParts[2].toInt())
        }
        
        // Find Sunday of the selected week
        while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }

        (0..6).map {
            val dateStr = df.format(calendar.time)
            val calories = summaryMap[dateStr]?.totalCalories ?: 0
            val dayNum = calendar.get(Calendar.DAY_OF_MONTH)
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            dayNum to calories
        }
    }

    val (weeklyTotal, weeklyGoal) = remember(summaryMap, userConfig, locale, selectedDate) {
        val df = SimpleDateFormat("yyyy-MM-dd", locale)
        val calendar = Calendar.getInstance()
        val dateParts = selectedDate.split("-")
        if (dateParts.size == 3) {
            calendar.set(dateParts[0].toInt(), dateParts[1].toInt() - 1, dateParts[2].toInt())
        }
        
        while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }

        var total = 0
        (0..6).forEach {
            val dateStr = df.format(calendar.time)
            total += summaryMap[dateStr]?.totalCalories ?: 0
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        total to (userConfig.dailyCalorieGoal * 7)
    }

    val cardColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 0. Date Selector Card
        Card(
            modifier = Modifier.fillMaxWidth().clickable { showMainDatePicker = true },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Date",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val displayDate = remember(selectedDate, locale) {
                        try {
                            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDate)
                            SimpleDateFormat("EEEE, d MMMM yyyy", locale).format(date)
                        } catch (_: Exception) {
                            selectedDate
                        }
                    }
                    Text(displayDate, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier.height(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedDate != todayStr) {
                        IconButton(onClick = { selectedDate = todayStr }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_history),
                                contentDescription = "Today",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // 1. Top Card: Macros for specific date
        val selectedSummary = summaryMap[selectedDate]
        val totalProtein = selectedSummary?.totalProtein ?: 0
        val totalCarbs = selectedSummary?.totalCarbs ?: 0
        val totalFats = selectedSummary?.totalFats ?: 0

        val macroTitle = remember(selectedDate, locale, todayStr) {
            try {
                if (selectedDate == todayStr) {
                    "Today's Macros"
                } else {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val date = sdf.parse(selectedDate)
                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.DAY_OF_YEAR, -1)
                    val yesterdayStr = sdf.format(calendar.time)
                    
                    if (selectedDate == yesterdayStr) {
                        "Yesterday's Macros"
                    } else {
                        val formatted = SimpleDateFormat("d MMMM", locale).format(date!!)
                        "Macros for $formatted"
                    }
                }
            } catch (_: Exception) {
                "Daily Macros"
            }
        }
        
        MacroCircleCard(
            modifier = Modifier.fillMaxWidth(),
            title = macroTitle,
            protein = totalProtein,
            carbs = totalCarbs,
            fats = totalFats,
            backgroundColor = cardColor,
            onClick = { showMacroDetail = true }
        )

        // 2. Weekly Section Group
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val weekRangeTitle = remember(selectedDate, locale) {
                val calendar = Calendar.getInstance()
                val dateParts = selectedDate.split("-")
                if (dateParts.size == 3) {
                    calendar.set(dateParts[0].toInt(), dateParts[1].toInt() - 1, dateParts[2].toInt())
                }
                while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
                    calendar.add(Calendar.DAY_OF_YEAR, -1)
                }
                val start = calendar.time
                calendar.add(Calendar.DAY_OF_YEAR, 6)
                val end = calendar.time
                
                val df = SimpleDateFormat("d MMM", locale)
                "Week: ${df.format(start)} - ${df.format(end)}"
            }

            Text(
                text = weekRangeTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            StreakProgressCard(
                modifier = Modifier.fillMaxWidth(),
                streak = streak,
                weekCalories = weekData.map { it.second },
                weekDayNumbers = weekData.map { it.first },
                goal = userConfig.dailyCalorieGoal,
                backgroundColor = cardColor
            )

            WeeklyTotalCard(
                total = weeklyTotal,
                goal = weeklyGoal,
                backgroundColor = cardColor
            )

            CalorieTrendCard(
                summaries = summaries,
                goal = userConfig.dailyCalorieGoal,
                backgroundColor = cardColor,
                selectedDate = selectedDate
            )
        }

        // 4. Weight History Section
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Weight Progress",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { 
                    newDateValue = todayStr
                    showAddWeightDialog = true 
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_add),
                        contentDescription = "Add weight",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            if (weightHistory.size < 2) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor)
                ) {
                    Box(modifier = Modifier.padding(32.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            "Not enough data to show a chart. Add more weight entries in your Profile.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                WeightChartCard(weightHistory, cardColor)
            }
            
            weightHistory.reversed().forEach { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("${entry.weight} kg", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(entry.dayId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                        Row {
                            IconButton(onClick = { 
                                weightToEdit = entry 
                                editWeightValue = entry.weight.toString()
                                editDateValue = entry.dayId
                            }) {
                                Icon(
                                    painterResource(id = R.drawable.ic_settings),
                                    contentDescription = "Edit entry",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(onClick = { weightToDelete = entry }) {
                                Icon(
                                    painterResource(id = R.drawable.ic_delete),
                                    contentDescription = "Delete entry",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun WeightChartCard(history: List<WeightEntry>, backgroundColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "Weight Trend",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            WeightLineChart(history)
        }
    }
}

@Composable
fun WeightLineChart(history: List<WeightEntry>) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    
    Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val minWeight = history.minOf { it.weight } - 2
            val maxWeight = history.maxOf { it.weight } + 2
            val weightRange = maxWeight - minWeight
            
            val width = size.width
            val height = size.height
            
            val points = history.mapIndexed { index, entry ->
                val x = if (history.size > 1) {
                    (index.toFloat() / (history.size - 1)) * width
                } else {
                    width / 2
                }
                val y = height - ((entry.weight.toFloat() - minWeight.toFloat()) / weightRange.toFloat() * height)
                Offset(x, y)
            }
            
            // Draw path
            val path = Path().apply {
                if (points.isNotEmpty()) {
                    moveTo(points[0].x, points[0].y)
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                }
            }
            
            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(width = 3.dp.toPx())
            )
            
            // Draw points
            points.forEach { point ->
                drawCircle(
                    color = primaryColor,
                    radius = 4.dp.toPx(),
                    center = point
                )
            }

            // Draw labels for min/max
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = onSurfaceColor.toArgb()
                    textSize = 30f
                }
                drawText("${maxWeight.toInt()} kg", 0f, 30f, paint)
                drawText("${minWeight.toInt()} kg", 0f, height, paint)
            }
        }
    }
}

@Composable
fun MacroCircleCard(
    modifier: Modifier,
    title: String,
    protein: Int,
    carbs: Int,
    fats: Int,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    val macroColors = LazyCalTheme.colors
    
    val total = remember(protein, carbs, fats) { (protein + carbs + fats).toFloat().coerceAtLeast(0f) }
    val pRatio = remember(protein, total) { if (total > 0) protein / total else 0f }
    val cRatio = remember(carbs, total) { if (total > 0) carbs / total else 0f }
    val fRatio = remember(fats, total) { if (total > 0) fats / total else 0f }

    Card(
        modifier = modifier
            .height(200.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 12.dp.toPx()
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

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MacroLegendItem(macroColors.protein, "Protein", "${protein}g")
                    MacroLegendItem(macroColors.carbs, "Carbs", "${carbs}g")
                    MacroLegendItem(macroColors.fats, "Fats", "${fats}g")
                }
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
fun StreakProgressCard(
    modifier: Modifier,
    streak: Int,
    weekCalories: List<Int>,
    weekDayNumbers: List<Int>,
    goal: Int,
    backgroundColor: Color
) {
    val themeColors = LazyCalTheme.colors

    Card(
        modifier = modifier.height(160.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = streak.toString(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = themeColors.fats
            )
            Text(text = "Day Streak", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            
            Spacer(modifier = Modifier.height(8.dp))
            val days = listOf("S", "M", "T", "W", "T", "F", "S")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                days.forEachIndexed { i, day ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = day,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f)
                        )
                        
                        val calories = weekCalories.getOrElse(i) { 0 }
                        val dotColor = when {
                            calories == 0 -> Color.LightGray.copy(0.4f)
                            calories < goal * 0.8 -> themeColors.fats // Yellow
                            calories <= goal -> themeColors.success // Green
                            else -> themeColors.error // Red
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )

                        Text(
                            text = weekDayNumbers.getOrElse(i) { "" }.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WeeklyTotalCard(total: Int, goal: Int, backgroundColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "Weekly Calories",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = total.toString(),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = " / $goal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "kcal",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Optional progress bar
            val progress = (total.toFloat() / goal.toFloat()).coerceIn(0f, 1.2f)
            val barColor = if (total > goal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceAtMost(1f))
                        .fillMaxSize()
                        .background(barColor)
                )
            }
            
            if (total > goal) {
                Text(
                    text = "Weekly goal exceeded",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun CalorieTrendCard(summaries: List<DaySummary>, goal: Int, backgroundColor: Color, selectedDate: String) {
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
            
            CalorieLineChart(summaries, goal, selectedDate)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun CalorieLineChart(summaries: List<DaySummary>, goal: Int, selectedDate: String) {
    val themeColors = LazyCalTheme.colors
    val locale = LocalLocale.current.platformLocale
    
    val data = remember(summaries, locale, selectedDate) {
        val df = SimpleDateFormat("yyyy-MM-dd", locale)
        val dayFormat = SimpleDateFormat("E", locale)
        val summaryMap = summaries.associateBy { it.dayId }
        
        val calendar = Calendar.getInstance()
        val dateParts = selectedDate.split("-")
        if (dateParts.size == 3) {
            calendar.set(dateParts[0].toInt(), dateParts[1].toInt() - 1, dateParts[2].toInt())
        }
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
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    Box(modifier = Modifier
        .fillMaxWidth()
        .height(220.dp)
        .pointerInput(data, yMax) {
            detectTapGestures { offset ->
                val paddingLeftPx = 40.dp.toPx()
                val paddingBottomPx = 30.dp.toPx()
                val chartWidth = size.width - paddingLeftPx
                val chartHeight = size.height - paddingBottomPx
                val spacing = if (data.size > 1) chartWidth / (data.size - 1) else chartWidth
                
                var foundIndex: Int? = null
                data.forEachIndexed { index, pair ->
                    val x = paddingLeftPx + index * spacing
                    val y = chartHeight - (pair.second.toFloat() / yMax * chartHeight)
                    
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
            val paint = Paint().apply {
                this.color = labelColor
                this.textSize = 10.sp.toPx()
                this.textAlign = Paint.Align.RIGHT
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
                val isSelected = selectedIndex == index
                val color = when {
                    pair.second == 0 -> Color.LightGray.copy(0.4f)
                    pair.second < goal * 0.8 -> themeColors.fats
                    pair.second <= goal -> themeColors.success
                    else -> themeColors.error
                }
                
                drawCircle(
                    color = color,
                    radius = if (isSelected) 7.dp.toPx() else 5.dp.toPx(),
                    center = Offset(x, y)
                )

                if (isSelected) {
                    drawCircle(
                        color = Color.White,
                        radius = 9.dp.toPx(),
                        center = Offset(x, y),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    
                    drawContext.canvas.nativeCanvas.drawText(
                        "${pair.second} kcal",
                        x,
                        y - 12.dp.toPx(),
                        Paint().apply {
                            this.color = labelColor
                            this.textSize = 12.sp.toPx()
                            this.textAlign = Paint.Align.CENTER
                            this.isFakeBoldText = true
                        }
                    )
                }
                
                // X Labels
                drawContext.canvas.nativeCanvas.drawText(
                    pair.first,
                    x,
                    height,
                    Paint().apply {
                        this.color = labelColor
                        this.textSize = 12.sp.toPx()
                        this.textAlign = Paint.Align.CENTER
                    }
                )
            }
        }
    }
}

@Composable
fun MacroDetailDialog(
    protein: Int,
    carbs: Int,
    fats: Int,
    onDismiss: () -> Unit
) {
    val macroColors = LazyCalTheme.colors
    val total = (protein + carbs + fats).toFloat().coerceAtLeast(1f)
    
    val pRatio = protein / total
    val cRatio = carbs / total
    val fRatio = fats / total

    var selectedMacro by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Daily Macros Detail", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(220.dp)
                        .pointerInput(protein, carbs, fats) {
                            detectTapGestures { offset ->
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val delta = offset - center
                                val distance = sqrt(delta.x * delta.x + delta.y * delta.y)
                                val radius = size.width / 2f
                                
                                if (distance in (radius * 0.5f)..radius) {
                                    var angle = Math.toDegrees(atan2(delta.y.toDouble(), delta.x.toDouble())).toFloat()
                                    if (angle < 0) angle += 360f
                                    
                                    val adjustedAngle = (angle + 90f) % 360f
                                    
                                    val pAngle = pRatio * 360f
                                    val cAngle = cRatio * 360f
                                    
                                    selectedMacro = when {
                                        adjustedAngle < pAngle -> "Protein"
                                        adjustedAngle < pAngle + cAngle -> "Carbs"
                                        else -> "Fats"
                                    }
                                } else {
                                    selectedMacro = null
                                }
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val baseStrokeWidth = 35.dp.toPx()
                        val highlightStrokeWidth = 45.dp.toPx()
                        
                        fun drawMacroArc(color: Color, startAngle: Float, sweepAngle: Float, isSelected: Boolean) {
                            val currentStroke = if (isSelected) highlightStrokeWidth else baseStrokeWidth
                            val arcSize = Size(size.width - highlightStrokeWidth, size.height - highlightStrokeWidth)
                            val topLeft = Offset(highlightStrokeWidth / 2, highlightStrokeWidth / 2)
                            
                            drawArc(
                                color = if (selectedMacro == null || isSelected) color else color.copy(alpha = 0.4f),
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(currentStroke)
                            )
                        }

                        drawMacroArc(
                            color = macroColors.protein,
                            startAngle = -90f,
                            sweepAngle = 360f * pRatio,
                            isSelected = selectedMacro == "Protein"
                        )
                        drawMacroArc(
                            color = macroColors.carbs,
                            startAngle = -90f + (360f * pRatio),
                            sweepAngle = 360f * cRatio,
                            isSelected = selectedMacro == "Carbs"
                        )
                        drawMacroArc(
                            color = macroColors.fats,
                            startAngle = -90f + (360f * (pRatio + cRatio)),
                            sweepAngle = 360f * fRatio,
                            isSelected = selectedMacro == "Fats"
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (selectedMacro != null) {
                            val quad = when (selectedMacro) {
                                "Protein" -> MacroQuad(macroColors.protein, "Protein", protein, pRatio)
                                "Carbs" -> MacroQuad(macroColors.carbs, "Carbs", carbs, cRatio)
                                else -> MacroQuad(macroColors.fats, "Fats", fats, fRatio)
                            }
                            Text(quad.label, style = MaterialTheme.typography.labelLarge, color = quad.color, fontWeight = FontWeight.Bold)
                            Text(quad.value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("${(quad.ratio * 100).toInt()}%", style = MaterialTheme.typography.bodyLarge)
                        } else {
                            Text("${total.toInt()}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                            Text("total grams", style = MaterialTheme.typography.labelMedium)
                            Text("Tap a section", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        }
                    }
                }
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    MacroDetailRow(macroColors.protein, "Protein", protein, pRatio)
                    MacroDetailRow(macroColors.carbs, "Carbs", carbs, cRatio)
                    MacroDetailRow(macroColors.fats, "Fats", fats, fRatio)
                }
                
                Text(
                    "Tap on chart sections to see details",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

private data class MacroQuad(val color: Color, val label: String, val grams: Int, val ratio: Float) {
    val value: String get() = "${grams}g"
}

@Composable
fun MacroDetailRow(color: Color, label: String, grams: Int, ratio: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${grams}g", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(12.dp))
            Text("${(ratio * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
