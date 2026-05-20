package com.francescocanossi.lazycal.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.francescocanossi.lazycal.ChatViewModel
import com.francescocanossi.lazycal.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ChatViewModel, onBack: () -> Unit) {
    val userConfig by viewModel.userConfig.collectAsState()
    
    var showGoalDialog by remember { mutableStateOf(false) }
    var tempGoal by remember { mutableStateOf(userConfig.dailyCalorieGoal.toString()) }
    
    var showDeleteModelDialog by remember { mutableStateOf(false) }
    var showDeleteLogsDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            scope.launch {
                val csvData = viewModel.getExportCSV()
                context.contentResolver.openOutputStream(it)?.use { output ->
                    output.write(csvData.toByteArray())
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            scope.launch {
                context.contentResolver.openInputStream(selectedUri)?.use { input ->
                    val csvData = input.bufferedReader().use { reader -> reader.readText() }
                    viewModel.importFromCSV(csvData)
                }
            }
        }
    }

    val themeOptions = listOf("auto", "light", "dark")
    val scrollState = rememberScrollState()

    if (showGoalDialog) {
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            title = { Text("Set Daily Calorie Goal") },
            text = {
                OutlinedTextField(
                    value = tempGoal,
                    onValueChange = { if (it.all { c -> c.isDigit() }) tempGoal = it },
                    label = { Text("Calories (kcal)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newGoal = tempGoal.toIntOrNull() ?: userConfig.dailyCalorieGoal
                        viewModel.saveUserConfig(newGoal, userConfig.themeMode)
                        showGoalDialog = false
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog = false; tempGoal = userConfig.dailyCalorieGoal.toString() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteLogsDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteLogsDialog = false },
            title = { Text("Backup your data?") },
            text = { Text("Would you like to export your entries as a CSV file before deleting your food logs?") },
            confirmButton = {
                TextButton(onClick = {
                    exportLauncher.launch("lazycal_backup.csv")
                    showDeleteLogsDialog = false
                }) {
                    Text("Yes, let me export")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteLogs()
                        showDeleteLogsDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("No, delete logs")
                }
            }
        )
    }

    if (showDeleteModelDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteModelDialog = false },
            title = { Text("Delete AI Model?") },
            text = { Text("This will permanently delete the AI model (2.58 GB). You will need to download it again to use the app.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteModel()
                        showDeleteModelDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Model")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteModelDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset AI Engine?") },
            text = { Text("This will restart the AI engine and clear the current conversation history. This can help if the AI is behaving unexpectedly.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetEngine()
                        showResetDialog = false
                    }
                ) {
                    Text("Reset Engine")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(id = R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Goal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        tempGoal = userConfig.dailyCalorieGoal.toString()
                        showGoalDialog = true 
                    },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Daily Calorie Goal", style = MaterialTheme.typography.bodyMedium)
                        Text("${userConfig.dailyCalorieGoal} kcal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Icon(painterResource(id = R.drawable.ic_settings), contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text("Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            themeOptions.forEach { text ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .selectable(
                            selected = (text == userConfig.themeMode),
                            onClick = { viewModel.saveUserConfig(userConfig.dailyCalorieGoal, text) },
                            role = Role.RadioButton,
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (text == userConfig.themeMode),
                        onClick = null // null recommended for accessibility with selectable modifier
                    )
                    Text(
                        text = text.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("AI Engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("GPU Acceleration", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                "Uses the device's GPU for faster text processing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = userConfig.useGpu,
                            onCheckedChange = { viewModel.toggleGpu(it) }
                        )
                    }
                    if (userConfig.useGpu) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Note: GPU acceleration significantly improves text processing speed but is currently incompatible with image analysis. Camera and gallery options will be disabled.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Data Management", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { exportLauncher.launch("lazycal_backup.csv") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Export (CSV)")
                }
                Button(
                    onClick = { importLauncher.launch("*/*") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Text("Import (CSV)")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Support", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { uriHandler.openUri("https://ko-fi.com/froopy070") },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_kofi),
                        contentDescription = "Ko-fi",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Buy me a coffee", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            Text("Troubleshooting", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Button(
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
            ) {
                Text("Reset AI Engine")
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Danger Zone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showDeleteModelDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Model")
                }
                Button(
                    onClick = { showDeleteLogsDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Logs")
                }
            }
        }
    }
}
