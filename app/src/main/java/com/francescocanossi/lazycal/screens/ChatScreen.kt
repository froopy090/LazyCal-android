package com.francescocanossi.lazycal.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import android.content.res.Configuration
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.francescocanossi.lazycal.ChatViewModel
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import java.util.TimeZone
import com.francescocanossi.lazycal.DaySummary
import com.francescocanossi.lazycal.WeightEntry
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
    val isReadOnly by viewModel.isReadOnly.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val weeklySummaries by viewModel.weeklySummaries.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
    val archivedDays by viewModel.archivedDays.collectAsState()
    val weightHistory by viewModel.weightHistory.collectAsState()
    val todayId = viewModel.todayId

    val daysWithData = remember(archivedDays, weightHistory) {
        (archivedDays.map { it.dayId } + weightHistory.map { it.dayId }).toSet()
    }

    var inputText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scrollState = rememberScrollState()
    val portraitListState = rememberLazyListState()
    val landscapeListState = rememberLazyListState()
    
    val isPortraitAtTop by remember {
        derivedStateOf {
            portraitListState.firstVisibleItemIndex == 0 && portraitListState.firstVisibleItemScrollOffset == 0
        }
    }
    val isLandscapeAtTop by remember {
        derivedStateOf {
            landscapeListState.firstVisibleItemIndex == 0 && landscapeListState.firstVisibleItemScrollOffset == 0
        }
    }

    var isCalorieBoxVisible by remember { mutableStateOf(true) }

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

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 0.dp)
            ) {
                if (isLandscape) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
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
                                daysWithData = daysWithData,
                                onDayClick = { viewModel.selectDay(it) },
                                onResetClick = { viewModel.resetToToday() }
                            )

                            AnimatedVisibility(
                                visible = isCalorieBoxVisible,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    CalorieSummaryCard(
                                        dailyTotal = dailyTotal,
                                        calorieGoal = userConfig.dailyCalorieGoal,
                                        activityLevel = userConfig.activityLevel
                                    )
                                }
                            }

                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                IconButton(
                                    onClick = { isCalorieBoxVisible = !isCalorieBoxVisible },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .padding(bottom = 0.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isCalorieBoxVisible) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (isCalorieBoxVisible) "Toggle Calorie Box" else "Show Calorie Box",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        VerticalDivider(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // Right Side: Food Entries
                        LazyColumn(
                            state = landscapeListState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(top = 0.dp),
                            contentPadding = PaddingValues(
                                top = if (isLandscapeAtTop) 8.dp else 0.dp,
                                bottom = 84 .dp,
                                start = 0.dp,
                                end = 0.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(entries, key = { it.id }) { entry ->
                                FoodEntryItem(
                                    entry = entry,
                                    onDelete = { viewModel.deleteEntry(entry) },
                                    onClick = { viewModel.showDetail(entry) },
                                    isReadOnly = isReadOnly
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
                        daysWithData = daysWithData,
                        onDayClick = { viewModel.selectDay(it) },
                        onResetClick = { viewModel.resetToToday() }
                    )

                    AnimatedVisibility(
                        visible = isCalorieBoxVisible,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(24.dp))
                            CalorieSummaryCard(
                                dailyTotal = dailyTotal,
                                calorieGoal = userConfig.dailyCalorieGoal,
                                activityLevel = userConfig.activityLevel
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        IconButton(
                            onClick = { isCalorieBoxVisible = !isCalorieBoxVisible },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isCalorieBoxVisible) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isCalorieBoxVisible) "Toggle Calorie Box" else "Show Calorie Box",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 4.dp, bottom = 0.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    LazyColumn(
                        state = portraitListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(
                            top = if (isPortraitAtTop) 8.dp else 0.dp,
                            bottom = 84.dp,
                            start = 0.dp,
                            end = 0.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(entries, key = { it.id }) { entry ->
                            FoodEntryItem(
                                entry = entry,
                                onDelete = { viewModel.deleteEntry(entry) },
                                onClick = { viewModel.showDetail(entry) },
                                isReadOnly = isReadOnly
                            )
                        }
                    }
                }
            }

            if (!isReadOnly && selectedDay != todayId) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 24.dp, bottom = 100.dp)
                        .size(40.dp)
                        .clickable { viewModel.toggleEditMode() },
                    shape = CircleShape,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Fine modifica",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (isReadOnly && selectedDay != todayId && !isProcessing) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 24.dp, bottom = 24.dp)
                        .size(48.dp)
                        .clickable { viewModel.toggleEditMode() },
                    shape = CircleShape,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Modify this day",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            } else if (!isReadOnly || isProcessing) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .fillMaxWidth()
                    ) {
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
                                    delay(2500)
                                    messageIndex = (messageIndex + 1) % messages.size
                                }
                            }
                            Text(
                                text = messages[messageIndex],
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                textAlign = TextAlign.Center
                            )
                        }

                        if (!isReadOnly) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 0.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = {
                                        Text(
                                            "What did you eat?",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    enabled = !isProcessing,
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.large,
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        disabledIndicatorColor = Color.Transparent
                                    )
                                )
                                IconButton(
                                    onClick = {
                                        val permissionCheckResult =
                                            ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.CAMERA
                                            )
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
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyTracker(
    weeklySummaries: List<DaySummary>,
    calorieGoal: Int,
    selectedDayId: String,
    daysWithData: Set<String>,
    onDayClick: (String) -> Unit,
    onResetClick: () -> Unit
) {
    val todayId = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date()) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    // Calculate page count and initial page
    val pageCount = remember(weeklySummaries) { (weeklySummaries.size / 7).coerceAtLeast(1) }
    val initialPage = remember(weeklySummaries, selectedDayId) {
        val index = weeklySummaries.indexOfFirst { it.dayId == selectedDayId }
        if (index != -1) index / 7 else (pageCount - 1).coerceAtLeast(0)
    }
    
    val pagerState = rememberPagerState(initialPage = initialPage) { pageCount }
    val scope = rememberCoroutineScope()
    val todayPageIndex = remember(weeklySummaries, todayId) {
        val index = weeklySummaries.indexOfFirst { it.dayId == todayId }
        if (index != -1) index / 7 else -1
    }
    
    // Scroll to page when selectedDayId changes (e.g. from outside or DatePicker)
    LaunchedEffect(selectedDayId, weeklySummaries) {
        val index = weeklySummaries.indexOfFirst { it.dayId == selectedDayId }
        if (index != -1) {
            val targetPage = index / 7
            if (pagerState.currentPage != targetPage) {
                pagerState.animateScrollToPage(targetPage)
            }
        }
    }

    val datePickerState = key(selectedDayId) {
        rememberDatePickerState(
            initialSelectedDateMillis = remember {
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                    sdf.parse(selectedDayId)?.time
                } catch (_: Exception) {
                    null
                }
            }
        )
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        sdf.timeZone = TimeZone.getTimeZone("UTC")
                        val formatted = sdf.format(java.util.Date(millis))
                        onDayClick(formatted)
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

    val weekRangeTitle = remember(weeklySummaries, pagerState.currentPage) {
        if (weeklySummaries.isEmpty()) ""
        else {
            val page = pagerState.currentPage
            val startIndex = page * 7
            val endIndex = (startIndex + 6).coerceAtMost(weeklySummaries.size - 1)
            
            val sdfInput = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val sdfOutput = SimpleDateFormat("d MMM", Locale.getDefault())
            try {
                val start = sdfInput.parse(weeklySummaries[startIndex].dayId)
                val end = sdfInput.parse(weeklySummaries[endIndex].dayId)
                "Week: ${sdfOutput.format(start!!)} - ${sdfOutput.format(end!!)}"
            } catch (_: Exception) {
                "Weekly Tracker"
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { showDatePicker = true }
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "Select Date",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                
                Spacer(modifier = Modifier.width(4.dp))
                
                Text(
                    text = weekRangeTitle,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            if (selectedDayId != todayId || pagerState.currentPage != todayPageIndex) {
                IconButton(
                    onClick = { 
                        onResetClick()
                        if (todayPageIndex != -1 && pagerState.currentPage != todayPageIndex) {
                            scope.launch { pagerState.animateScrollToPage(todayPageIndex) }
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_history),
                        contentDescription = "Today",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val startIndex = page * 7
                val weekDays = weeklySummaries.subList(startIndex, (startIndex + 7).coerceAtMost(weeklySummaries.size))
                
                weekDays.forEach { summary ->
                    val isFuture = summary.dayId > todayId
                    val calendar = remember(summary.dayId) {
                        Calendar.getInstance().apply {
                            val parts = summary.dayId.split("-")
                            if (parts.size == 3) {
                                set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                            }
                        }
                    }
                    val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
                    val dayOfWeekLabel = remember(summary.dayId) {
                        SimpleDateFormat("E", Locale.getDefault()).format(calendar.time).first().toString().uppercase()
                    }
                    val isSelected = summary.dayId == selectedDayId

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { onDayClick(summary.dayId) }
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(36.dp)
                        ) {
                            val progress = remember(summary.totalCalories, calorieGoal, isFuture) {
                                when {
                                    isFuture -> 0f
                                    calorieGoal <= 0 -> if (summary.totalCalories > 0) 1f else 0f
                                    else -> (summary.totalCalories.toFloat() / calorieGoal.toFloat()).coerceIn(0f, 1f)
                                }
                            }
                            val color = when {
                                summary.totalCalories == 0 -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                summary.totalCalories < calorieGoal * 0.8 -> LazyCalTheme.colors.fats
                                summary.totalCalories <= calorieGoal -> LazyCalTheme.colors.success
                                else -> LazyCalTheme.colors.error
                            }
                            
                            Canvas(modifier = Modifier.size(32.dp)) {
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
                                text = dayOfWeekLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    summary.dayId == todayId -> MaterialTheme.colorScheme.primary
                                    isSelected -> Color.White
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                fontSize = 10.sp
                            )
                        }
                        Text(
                            text = dayOfMonth.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                summary.dayId == todayId -> MaterialTheme.colorScheme.primary
                                isSelected -> Color.White
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CalorieSummaryCard(
    dailyTotal: Int,
    calorieGoal: Int,
    activityLevel: String? = null
) {
    val isOver = dailyTotal > calorieGoal
    val diff = if (isOver) dailyTotal - calorieGoal else calorieGoal - dailyTotal
    val progress = remember(dailyTotal, calorieGoal) {
        if (calorieGoal <= 0) {
            if (dailyTotal > 0) 1.2f else 0f
        } else {
            (dailyTotal.toFloat() / calorieGoal.toFloat()).coerceIn(0f, 1.2f)
        }
    }

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
                Text(
                    text = "Consumed: $dailyTotal / $calorieGoal kcal",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
                activityLevel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Box(contentAlignment = Alignment.Center) {
                val color = when {
                    dailyTotal == 0 -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    dailyTotal < calorieGoal * 0.8 -> LazyCalTheme.colors.fats
                    dailyTotal <= calorieGoal -> LazyCalTheme.colors.success
                    else -> LazyCalTheme.colors.error
                }
                
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

                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
    }
}

@Composable
fun FoodEntryItem(
    entry: FoodEntry,
    onDelete: () -> Unit,
    onClick: () -> Unit,
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = entry.foodName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (entry.amount.isNotBlank()) {
                    Text(
                        text = entry.amount,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "P: ${entry.protein}g C: ${entry.carbs}g F: ${entry.fats}g",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${entry.calories}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "kcal",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
            
            if (!isReadOnly) {
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_delete),
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

fun Modifier.fadingEdge(
    topEdgeHeight: Dp = 16.dp,
    bottomEdgeHeight: Dp = 16.dp
): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val topEdgeHeightPx = topEdgeHeight.toPx()
        val bottomEdgeHeightPx = bottomEdgeHeight.toPx()

        if (topEdgeHeightPx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    topEdgeHeightPx to Color.Black,
                    startY = 0f,
                    endY = topEdgeHeightPx
                ),
                blendMode = BlendMode.DstIn
            )
        }

        if (bottomEdgeHeightPx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    size.height - bottomEdgeHeightPx to Color.Black,
                    size.height to Color.Transparent,
                    startY = size.height - bottomEdgeHeightPx,
                    endY = size.height
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }
