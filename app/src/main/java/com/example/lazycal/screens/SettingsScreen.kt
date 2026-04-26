package com.example.lazycal.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.lazycal.ChatViewModel
import com.example.lazycal.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ChatViewModel, onBack: () -> Unit) {
    val userConfig by viewModel.userConfig.collectAsState()
    var goal by remember(userConfig) { mutableStateOf(userConfig.dailyCalorieGoal.toString()) }
    var selectedTheme by remember(userConfig) { mutableStateOf(userConfig.themeMode) }
    
    var showBackupPrompt by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
        uri?.let {
            scope.launch {
                context.contentResolver.openInputStream(it)?.use { input ->
                    val csvData = input.bufferedReader().use { it.readText() }
                    viewModel.importFromCSV(csvData)
                }
            }
        }
    }

    val themeOptions = listOf("auto", "light", "dark")

    if (showBackupPrompt) {
        AlertDialog(
            onDismissRequest = { showBackupPrompt = false },
            title = { Text("Backup your data?") },
            text = { Text("Would you like to export your entries as a CSV file before deleting everything?") },
            confirmButton = {
                TextButton(onClick = { showBackupPrompt = false }) {
                    Text("Yes, let me export")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBackupPrompt = false
                        showDeleteDialog = true
                    }
                ) {
                    Text("No, I've already backed up")
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Data and Model?") },
            text = { Text("This will permanently delete all your food entries and the AI model. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteModel()
                        showDeleteDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
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
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Goal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = goal, onValueChange = { if (it.all { c -> c.isDigit() }) goal = it }, label = { Text("Daily Calorie Goal") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text("Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            themeOptions.forEach { text ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .selectable(
                            selected = (text == selectedTheme),
                            onClick = { selectedTheme = text },
                            role = Role.RadioButton,
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (text == selectedTheme),
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
            Button(onClick = { viewModel.saveUserConfig(goal.toIntOrNull() ?: 2000, selectedTheme); onBack() }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
            
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
            
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = { showBackupPrompt = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete AI Model and Data") }
        }
    }
}
