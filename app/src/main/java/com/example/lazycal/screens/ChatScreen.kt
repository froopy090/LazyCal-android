package com.example.lazycal.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.lazycal.ChatViewModel
import com.example.lazycal.DaySummary
import com.example.lazycal.FoodEntry
import com.example.lazycal.R
import com.example.lazycal.ui.theme.LazyCalTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

@Composable
fun WelcomeScreen(onDownloadClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Lazy Calorie Tracker",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("AI-powered local calorie tracking.")
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onDownloadClick) {
            Text("Download Parser")
        }
    }
}

@Composable
fun DownloadingScreen(onCheckClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text("Downloading model...")
        Button(onClick = onCheckClick) {
            Text("Check Status")
        }
    }
}

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val entries by viewModel.foodEntries.collectAsState()
    val dailyTotal by viewModel.dailyTotal.collectAsState()
    val userConfig by viewModel.userConfig.collectAsState()
    val isReadOnly by viewModel.isReadOnly.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val weeklySummaries by viewModel.weeklySummaries.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val tempFile = remember { File(context.externalCacheDir, "temp_food.jpg") }
    val imageUri = remember {
        FileProvider.getUriForFile(context, "${context.packageName}.provider", tempFile)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            viewModel.sendImage(tempFile.absolutePath)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(imageUri)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val file = File(context.externalCacheDir, "gallery_temp.jpg")
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(it)?.use { input ->
                            file.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    viewModel.sendImage(file.absolutePath)
                } catch (e: Exception) {
                    Log.e("ChatScreen", "Failed to load image from gallery", e)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        WeeklyTracker(
            weeklySummaries = weeklySummaries,
            calorieGoal = userConfig.dailyCalorieGoal,
            selectedDayId = selectedDay,
            onDayClick = { viewModel.selectDay(it) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        CalorieSummaryCard(
            dailyTotal = dailyTotal,
            calorieGoal = userConfig.dailyCalorieGoal
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries) { entry ->
                FoodEntryItem(
                    entry = entry,
                    onDelete = { viewModel.deleteEntry(entry) },
                    isReadOnly = isReadOnly
                )
            }
        }
        if (isProcessing) {
            var processingMessage by remember { mutableStateOf("LazyCal is thinking...") }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(5000)
                    processingMessage = if (processingMessage == "LazyCal is thinking...") {
                        "Calculating your calories..."
                    } else {
                        "LazyCal is thinking..."
                    }
                }
            }
            Text(
                text = processingMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                textAlign = TextAlign.Center
            )
        }
        if (!isReadOnly) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("What did you eat?") },
                    enabled = !isProcessing,
                    singleLine = true
                )
                IconButton(
                    onClick = {
                        val permissionCheckResult = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                        if (permissionCheckResult == PackageManager.PERMISSION_GRANTED) {
                            cameraLauncher.launch(imageUri)
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    enabled = !isProcessing
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Camera"
                    )
                }
                IconButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    enabled = !isProcessing
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "Gallery"
                    )
                }
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    },
                    enabled = !isProcessing
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_send),
                        contentDescription = "Send"
                    )
                }
            }
        }
    }
}

@Composable
fun WeeklyTracker(
    weeklySummaries: List<DaySummary>,
    calorieGoal: Int,
    selectedDayId: String,
    onDayClick: (String) -> Unit
) {
    val days = remember { listOf("S", "M", "T", "W", "T", "F", "S") }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        weeklySummaries.forEachIndexed { index, summary ->
            val dayOfMonth = remember(summary.dayId) {
                val calendar = Calendar.getInstance()
                val parts = summary.dayId.split("-")
                if (parts.size == 3) {
                    calendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                }
                calendar.get(Calendar.DAY_OF_MONTH)
            }
            val isSelected = summary.dayId == selectedDayId

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onDayClick(summary.dayId) }
                    .padding(4.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(40.dp)
                ) {
                    val progress = (summary.totalCalories.toFloat() / calorieGoal.toFloat()).coerceIn(0f, 1f)
                    val color = if (summary.totalCalories > calorieGoal) LazyCalTheme.colors.error else MaterialTheme.colorScheme.primary
                    
                    Canvas(modifier = Modifier.size(36.dp)) {
                        drawArc(
                            color = color.copy(alpha = 0.2f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                        )
                        drawArc(
                            color = color,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                    Text(
                        text = days[index],
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CalorieSummaryCard(
    dailyTotal: Int,
    calorieGoal: Int
) {
    val isOver = dailyTotal > calorieGoal
    val diff = if (isOver) dailyTotal - calorieGoal else calorieGoal - dailyTotal
    val progress = (dailyTotal.toFloat() / calorieGoal.toFloat()).coerceIn(0f, 1.2f) // Allow slightly over for visual

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = diff.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isOver) "Calories over" else "Calories left",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(contentAlignment = Alignment.Center) {
                val color = if (isOver) LazyCalTheme.colors.error else MaterialTheme.colorScheme.primary
                
                Canvas(modifier = Modifier.size(100.dp)) {
                    drawArc(
                        color = color.copy(alpha = 0.1f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 12.dp.toPx())
                    )
                    drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = 360f * (progress.coerceAtMost(1f)),
                        useCenter = false,
                        style = Stroke(width = 12.dp.toPx())
                    )
                }
            }
        }
    }
}

@Composable
fun FoodEntryItem(
    entry: FoodEntry,
    onDelete: () -> Unit,
    isReadOnly: Boolean
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Entry") },
            text = { Text("Are you sure you want to delete \"${entry.foodName}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.foodName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(text = entry.amount, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = "P: ${entry.protein}g C: ${entry.carbs}g F: ${entry.fats}g",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "${entry.calories} kcal",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            if (!isReadOnly) {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_delete),
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
