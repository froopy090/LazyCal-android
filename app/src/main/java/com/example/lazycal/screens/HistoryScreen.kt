package com.example.lazycal.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lazycal.ChatViewModel
import com.example.lazycal.DaySummary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun HistoryScreen(
    chatViewModel: ChatViewModel,
    onDaySelected: () -> Unit
) {
    val archivedDays by chatViewModel.archivedDays.collectAsState()
    val selectedDay by chatViewModel.selectedDay.collectAsState()
    val isReadOnly by chatViewModel.isReadOnly.collectAsState()

    val categorizedDays = remember(archivedDays) {
        val weekAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.time
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        
        val thisWeek = mutableListOf<DaySummary>()
        val older = mutableListOf<DaySummary>()
        
        archivedDays.filter { it.dayId != chatViewModel.todayId }.forEach { summary ->
            try {
                val date = sdf.parse(summary.dayId)
                if (date != null && date.after(weekAgo)) {
                    thisWeek.add(summary)
                } else {
                    older.add(summary)
                }
            } catch (_: Exception) {
                older.add(summary)
            }
        }
        Pair(thisWeek, older)
    }
    val thisWeekDays = categorizedDays.first
    val olderDays = categorizedDays.second

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Text(
                    "Today",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                val todaySummary = archivedDays.find { it.dayId == chatViewModel.todayId }
                NavigationDrawerItem(
                    label = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Today's Track")
                            if (todaySummary != null) {
                                Text(
                                    "${todaySummary.totalCalories} kcal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    selected = !isReadOnly,
                    onClick = {
                        chatViewModel.resetToToday()
                        onDaySelected()
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(16.dp))
            }

            if (thisWeekDays.isNotEmpty()) {
                item {
                    Text(
                        "This Week",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(thisWeekDays, key = { it.dayId }) { day ->
                    NavigationDrawerItem(
                        label = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(day.dayId)
                                Text(
                                    "${day.totalCalories} kcal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        selected = day.dayId == selectedDay,
                        onClick = {
                            chatViewModel.selectDay(day.dayId)
                            onDaySelected()
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            if (olderDays.isNotEmpty()) {
                item {
                    Text(
                        "Older",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(olderDays, key = { it.dayId }) { day ->
                    NavigationDrawerItem(
                        label = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(day.dayId)
                                Text(
                                    "${day.totalCalories} kcal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        selected = day.dayId == selectedDay,
                        onClick = {
                            chatViewModel.selectDay(day.dayId)
                            onDaySelected()
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
    }
}
