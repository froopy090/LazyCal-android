package com.francescocanossi.lazycal.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.francescocanossi.lazycal.ChatViewModel
import com.francescocanossi.lazycal.FoodEntry
import com.francescocanossi.lazycal.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodDetailScreen(
    entry: FoodEntry,
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<EditField?>(null) }
    var tempValue by remember(entry, showEditDialog) {
        mutableStateOf(
            when (showEditDialog) {
                EditField.CALORIES -> entry.calories.toString()
                EditField.PROTEIN -> entry.protein.toString()
                EditField.CARBS -> entry.carbs.toString()
                EditField.FATS -> entry.fats.toString()
                null -> ""
            }
        )
    }
    val keyboardController = LocalSoftwareKeyboardController.current

    showEditDialog?.let { field ->
        AlertDialog(
            onDismissRequest = {
                keyboardController?.hide()
                showEditDialog = null
            },
            title = { Text("Adjust ${field.label}") },
            text = {
                OutlinedTextField(
                    value = tempValue,
                    onValueChange = { if (it.all { c -> c.isDigit() }) tempValue = it },
                    label = { Text("${field.label} (${field.unit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        keyboardController?.hide()
                        val newValue = tempValue.toIntOrNull()
                        if (newValue != null) {
                            val updatedEntry = when (field) {
                                EditField.CALORIES -> entry.copy(calories = newValue)
                                EditField.PROTEIN -> entry.copy(protein = newValue)
                                EditField.CARBS -> entry.copy(carbs = newValue)
                                EditField.FATS -> entry.copy(fats = newValue)
                            }
                            viewModel.updateEntry(updatedEntry)
                        }
                        showEditDialog = null
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    keyboardController?.hide()
                    showEditDialog = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDatePicker) {
        val calendar = java.util.Calendar.getInstance()
        val parts = entry.dayId.split("-")
        if (parts.size == 3) {
            calendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        }
        
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newDayId = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                viewModel.updateEntry(entry.copy(dayId = newDayId))
                showDatePicker = false
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        ).apply {
            setOnDismissListener { showDatePicker = false }
            show()
        }
        showDatePicker = false // Reset after showing
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Entry Summary") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = entry.foodName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Original description: ${entry.originalInput}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        showEditDialog = EditField.CALORIES
                    },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Calories", style = MaterialTheme.typography.bodyMedium)
                        Text("${entry.calories} kcal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Icon(painterResource(id = R.drawable.ic_settings), contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DetailRow("Date", entry.dayId, onEdit = { showDatePicker = true })
                    DetailRow("Amount", entry.amount)
                    DetailRow("Protein", "${entry.protein}g", onEdit = { showEditDialog = EditField.PROTEIN })
                    DetailRow("Carbs", "${entry.carbs}g", onEdit = { showEditDialog = EditField.CARBS })
                    DetailRow("Fats", "${entry.fats}g", onEdit = { showEditDialog = EditField.FATS })
                }
            }
        }
    }
}

enum class EditField(val label: String, val unit: String) {
    CALORIES("Calories", "kcal"),
    PROTEIN("Protein", "g"),
    CARBS("Carbs", "g"),
    FATS("Fats", "g")
}

@Composable
fun DetailRow(label: String, value: String, onEdit: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onEdit != null) { onEdit?.invoke() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            if (onEdit != null) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_settings),
                    contentDescription = "Edit $label",
                    modifier = Modifier.padding(start = 8.dp).height(16.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
            }
        }
    }
}
