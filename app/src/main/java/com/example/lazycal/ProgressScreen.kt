package com.example.lazycal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ProgressScreen(viewModel: ProgressViewModel, chatViewModel: ChatViewModel) {
    val streak by viewModel.currentStreak.collectAsState()
    val summaries by viewModel.daySummaries.collectAsState()
    val userConfig by viewModel.userConfig.collectAsState()
    val entries by chatViewModel.foodEntries.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 1. Streak Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🔥", fontSize = 40.sp)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = "Current Streak", style = MaterialTheme.typography.labelLarge)
                    Text(text = "$streak Days", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2. Macro Distribution Bar
        Column {
            Text(text = "Today's Macros", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            val totalProtein = entries.sumOf { it.protein }
            val totalCarbs = entries.sumOf { it.carbs }
            val totalFats = entries.sumOf { it.fats }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (totalProtein > 0) Box(Modifier.weight(totalProtein.toFloat()).fillMaxHeight().background(Color(0xFF2196F3)))
                if (totalCarbs > 0) Box(Modifier.weight(totalCarbs.toFloat()).fillMaxHeight().background(Color(0xFF4CAF50)))
                if (totalFats > 0) Box(Modifier.weight(totalFats.toFloat()).fillMaxHeight().background(Color(0xFFFF9800)))
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                MacroLabel("Protein", "${totalProtein}g", Color(0xFF2196F3))
                MacroLabel("Carbs", "${totalCarbs}g", Color(0xFF4CAF50))
                MacroLabel("Fats", "${totalFats}g", Color(0xFFFF9800))
            }
        }

        // 3. Calendar Grid (Contribution style)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Consistency (Last 3 Months)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(8.dp))
            CalendarGrid(summaries, userConfig.dailyCalorieGoal)
        }

        // 4. Histogram
        Column {
            var timeframe by remember { mutableStateOf(HistogramView.Days) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Calorie History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                var expanded by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { expanded = true }) { Text(timeframe.name) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        HistogramView.entries.forEach { view ->
                            DropdownMenuItem(text = { Text(view.name) }, onClick = { timeframe = view; expanded = false })
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Histogram(summaries, timeframe, userConfig.dailyCalorieGoal)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun MacroLabel(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = "$label: $value", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun CalendarGrid(summaries: List<DaySummary>, goal: Int) {
    val summaryMap = summaries.associateBy { it.dayId }
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.MONTH, -2) 
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    
    while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
        calendar.add(Calendar.DAY_OF_YEAR, -1)
    }

    val rows = 7
    val columns = 14
    val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())

    val startCalendar = calendar.clone() as Calendar

    Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Row(modifier = Modifier.padding(start = 32.dp)) {
            val monthCalendar = startCalendar.clone() as Calendar
            for (c in 0 until columns) {
                if (monthCalendar.get(Calendar.DAY_OF_MONTH) <= 7) {
                    Text(
                        text = monthFormat.format(monthCalendar.time),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(16.dp * 7)
                    )
                } else {
                    Spacer(modifier = Modifier.width(16.dp))
                }
                monthCalendar.add(Calendar.DAY_OF_YEAR, 7)
            }
        }

        Row {
            val weekDays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            Column(modifier = Modifier.padding(end = 8.dp)) {
                weekDays.forEach { day ->
                    Text(text = day, fontSize = 10.sp, modifier = Modifier.height(16.dp))
                }
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val gridCalendar = startCalendar.clone() as Calendar
                for (c in 0 until columns) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (r in 0 until rows) {
                            val dateStr = df.format(gridCalendar.time)
                            val summary = summaryMap[dateStr]
                            val isSuccess = summary != null && summary.totalCalories <= goal
                            
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(
                                        if (isSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(2.dp)
                                    )
                                    .border(0.5.dp, Color.LightGray.copy(0.3f), RoundedCornerShape(2.dp))
                            )
                            gridCalendar.add(Calendar.DAY_OF_YEAR, 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Histogram(summaries: List<DaySummary>, view: HistogramView, goal: Int) {
    val df = SimpleDateFormat("yyyy-MM-dd", LocalLocale.current.platformLocale)
    val monthFormat = SimpleDateFormat("MMM", LocalLocale.current.platformLocale)
    val summaryMap = summaries.associateBy { it.dayId }
    
    val data = when (view) {
        HistogramView.Days -> {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -6)
            (0..6).map {
                val dateStr = df.format(cal.time)
                val label = SimpleDateFormat("E", LocalLocale.current.platformLocale).format(cal.time)
                cal.add(Calendar.DAY_OF_YEAR, 1)
                label to (summaryMap[dateStr]?.totalCalories ?: 0)
            }
        }
        HistogramView.Months -> {
            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, -5)
            (0..5).map {
                val monthStr = SimpleDateFormat("yyyy-MM", LocalLocale.current.platformLocale).format(cal.time)
                val label = monthFormat.format(cal.time)
                val avg = summaries.filter { it.dayId.startsWith(monthStr) }
                    .map { it.totalCalories }.let { if (it.isEmpty()) 0 else it.average().toInt() }
                cal.add(Calendar.MONTH, 1)
                label to avg
            }
        }
        HistogramView.Years -> {
            val cal = Calendar.getInstance()
            cal.add(Calendar.YEAR, -2)
            (0..2).map {
                val yearStr = cal.get(Calendar.YEAR).toString()
                val avg = summaries.filter { it.dayId.startsWith(yearStr) }
                    .map { it.totalCalories }.let { if (it.isEmpty()) 0 else it.average().toInt() }
                cal.add(Calendar.YEAR, 1)
                yearStr to avg
            }
        }
    }

    val maxVal = (data.maxOf { it.second }.coerceAtLeast(goal)).toFloat() * 1.2f

    Row(modifier = Modifier.fillMaxWidth().height(220.dp)) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(end = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            Text("${maxVal.toInt()}", fontSize = 10.sp, color = Color.Gray)
            Text("kcal", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("0", fontSize = 10.sp, color = Color.Gray)
        }

        Row(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            data.forEach { (label, value) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(if (maxVal > 0) value.toFloat() / maxVal else 0f)
                            .background(
                                if (value in 1..goal) MaterialTheme.colorScheme.primary
                                else if (value > goal) Color.Red.copy(0.7f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                            )
                    )
                    Text(text = label, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
    }
}
