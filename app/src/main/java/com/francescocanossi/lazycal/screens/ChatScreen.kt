package com.francescocanossi.lazycal.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import android.content.res.Configuration
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.francescocanossi.lazycal.ChatViewModel
import com.francescocanossi.lazycal.DaySummary
import com.francescocanossi.lazycal.FoodEntry
import com.francescocanossi.lazycal.R
import com.francescocanossi.lazycal.ui.theme.LazyCalTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun WelcomeScreen(isOnline: Boolean, onDownloadClick: () -> Unit) {
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
        Button(onClick = onDownloadClick, enabled = isOnline) {
            Text("Download Gemma4 AI (2.58 GB)")
        }
        if (!isOnline) {
            Text(
                "No internet connection. Please connect to download the model.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun DownloadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text("Downloading model...")
        Text(
            "This may take a minute depending on your connection.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val entries by viewModel.foodEntries.collectAsState()
    val dailyTotal by viewModel.dailyTotal.collectAsState()
    val userConfig by viewModel.userConfig.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val weeklySummaries by viewModel.weeklySummaries.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scrollState = rememberScrollState()

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
                } catch (_: Exception) {
                    // Ignore gallery errors
                }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxHeight = this.maxHeight
        val maxWidth = this.maxWidth

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            if (isLandscape) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Side: Summaries
                    Column(
                        modifier = Modifier
                            .width(maxWidth * 0.4f)
                            .verticalScroll(scrollState)
                    ) {
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
                    }

                    // Right Side: Food Entries
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(entries, key = { it.id }) { entry ->
                            FoodEntryItem(
                                entry = entry,
                                onDelete = { viewModel.deleteEntry(entry) },
                                onClick = { viewModel.showDetail(entry) }
                            )
                        }
                    }
                }
            } else {
                // Portrait Layout
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
                    items(entries, key = { it.id }) { entry ->
                        FoodEntryItem(
                            entry = entry,
                            onDelete = { viewModel.deleteEntry(entry) },
                            onClick = { viewModel.showDetail(entry) }
                        )
                    }
                }
            }

            if (isProcessing) {
                val messages = remember {
                    listOf(
                        "Calculating your calories...",
                        "AI is running locally on device, this may take a while.",
                        "Hint: Being specific with portions leads to better results.",
                        "Hint: If you already know the calories, just include them!",
                        "Did you know? Accuracy is highest when you specify weights.",
                        "Tip: You can tap on the food entry to manually modify values."
                    )
                }
                var messageIndex by remember { mutableStateOf(0) }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(4000)
                        messageIndex = (messageIndex + 1) % messages.size
                    }
                }
                Text(
                    text = messages[messageIndex],
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center
                )
            }
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
    val todayId = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date()) }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        weeklySummaries.forEachIndexed { index, summary ->
            val isFuture = summary.dayId > todayId
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
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else Color.Transparent
                    )
                    .padding(4.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(40.dp)
                ) {
                    val progress = remember(summary.totalCalories, calorieGoal, isFuture) {
                        when {
                            isFuture -> 0f
                            calorieGoal <= 0 -> if (summary.totalCalories > 0) 1f else 0f
                            else -> (summary.totalCalories.toFloat() / calorieGoal.toFloat()).coerceIn(0f, 1f)
                        }
                    }
                    val isOverGoal = summary.totalCalories > calorieGoal
                    val color = if (isOverGoal) LazyCalTheme.colors.error else MaterialTheme.colorScheme.primary
                    val strokeWidth = if (isSelected) 3.dp else 2.dp
                    
                    Canvas(modifier = Modifier.size(36.dp)) {
                        drawArc(
                            color = color.copy(alpha = 0.2f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                        )
                        drawArc(
                            color = color,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            style = Stroke(width = strokeWidth.toPx())
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
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
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
    onClick: () -> Unit
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
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
