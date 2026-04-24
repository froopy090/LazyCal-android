package com.example.lazycal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lazycal.ui.theme.LazyCalTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.platform.LocalLocale

enum class TabItem(val title: String, val iconRes: Int) {
    Tracker("Tracker", R.drawable.ic_tracker),
    Progress("Progress", R.drawable.ic_progress)
}

enum class HistogramView {
    Days, Months, Years
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LazyCalTheme {
                val chatViewModel: ChatViewModel = viewModel()
                val progressViewModel: ProgressViewModel = viewModel()
                
                val uiState by chatViewModel.uiState.collectAsState()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val archivedDays by chatViewModel.archivedDays.collectAsState()
                val selectedDay by chatViewModel.selectedDay.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }
                val inputErrorMessage by chatViewModel.inputErrorMessage.collectAsState()
                
                var currentTab by remember { mutableStateOf(TabItem.Tracker) }
                var showSettings by remember { mutableStateOf(false) }

                LaunchedEffect(inputErrorMessage) {
                    inputErrorMessage?.let {
                        snackbarHostState.showSnackbar(it)
                        chatViewModel.clearInputError()
                    }
                }

                if (showSettings) {
                    SettingsScreen(viewModel = chatViewModel, onBack = { showSettings = false })
                } else {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            ModalDrawerSheet {
                                Spacer(Modifier.height(12.dp))
                                Text("History", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                                HorizontalDivider()
                                LazyColumn {
                                    items(archivedDays) { day ->
                                        NavigationDrawerItem(
                                            label = { Text(day) },
                                            selected = day == selectedDay,
                                            onClick = {
                                                chatViewModel.selectDay(day)
                                                scope.launch { drawerState.close() }
                                            },
                                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                        )
                                    }
                                }
                            }
                        }
                    ) {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            topBar = {
                                TopAppBar(
                                    title = { Text("Lazy Cal") },
                                    navigationIcon = {
                                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                            Icon(painterResource(id = R.drawable.ic_menu), contentDescription = "Menu")
                                        }
                                    },
                                    actions = {
                                        IconButton(onClick = { showSettings = true }) {
                                            Icon(painterResource(id = R.drawable.ic_settings), contentDescription = "Settings")
                                        }
                                    }
                                )
                            },
                            bottomBar = {
                                NavigationBar {
                                    TabItem.entries.forEach { tab ->
                                        NavigationBarItem(
                                            icon = { Icon(painterResource(tab.iconRes), contentDescription = tab.title) },
                                            label = { Text(tab.title) },
                                            selected = currentTab == tab,
                                            onClick = { currentTab = tab }
                                        )
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                                when (uiState) {
                                    ChatState.CheckingModel -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                    ChatState.ModelMissing -> WelcomeScreen(onDownloadClick = { chatViewModel.startDownload() })
                                    ChatState.Downloading -> DownloadingScreen(onCheckClick = { chatViewModel.onDownloadComplete() })
                                    ChatState.Initializing -> Column(
                                        modifier = Modifier.align(Alignment.Center),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        CircularProgressIndicator()
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Initializing Parser...")
                                    }
                                    ChatState.Ready -> {
                                        when (currentTab) {
                                            TabItem.Tracker -> ChatScreen(chatViewModel)
                                            TabItem.Progress -> ProgressScreen(progressViewModel, chatViewModel)
                                        }
                                    }
                                    is ChatState.Error -> Column(
                                        modifier = Modifier.align(Alignment.Center),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(text = (uiState as ChatState.Error).message, color = Color.Red, modifier = Modifier.padding(16.dp))
                                        Button(onClick = { chatViewModel.onDownloadComplete() }) { Text("Retry") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

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
    
    // Adjust to start of week (Sunday)
    while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
        calendar.add(Calendar.DAY_OF_YEAR, -1)
    }

    val rows = 7
    val columns = 14 // Approx 3 months
    val df = SimpleDateFormat("yyyy-MM-dd", LocalLocale.current.platformLocale)
    val monthFormat = SimpleDateFormat("MMM", LocalLocale.current.platformLocale)

    val startCalendar = calendar.clone() as Calendar

    Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        // Month names row
        Row(modifier = Modifier.padding(start = 32.dp)) {
            val monthCalendar = startCalendar.clone() as Calendar
            for (c in 0 until columns) {
                if (monthCalendar.get(Calendar.DAY_OF_MONTH) <= 7) {
                    Text(
                        text = monthFormat.format(monthCalendar.time),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(16.dp * 7) // Rough estimate
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
        // Y-Axis label
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ChatViewModel, onBack: () -> Unit) {
    val userConfig by viewModel.userConfig.collectAsState()
    var name by remember(userConfig) { mutableStateOf(userConfig.name) }
    var goal by remember(userConfig) { mutableStateOf(userConfig.dailyCalorieGoal.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(id = R.drawable.ic_menu), contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = goal, onValueChange = { if (it.all { c -> c.isDigit() }) goal = it }, label = { Text("Goal") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Button(onClick = { viewModel.saveUserConfig(name, goal.toIntOrNull() ?: 2000); onBack() }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = { viewModel.deleteModel(); onBack() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete Data") }
        }
    }
}

@Composable
fun WelcomeScreen(onDownloadClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Lazy Calorie Tracker", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("AI-powered local calorie tracking.")
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onDownloadClick) { Text("Download Parser") }
    }
}

@Composable
fun DownloadingScreen(onCheckClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text("Downloading model...")
        Button(onClick = onCheckClick) { Text("Check Status") }
    }
}

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val entries by viewModel.foodEntries.collectAsState()
    val dailyTotal by viewModel.dailyTotal.collectAsState()
    val userConfig by viewModel.userConfig.collectAsState()
    val isReadOnly by viewModel.isReadOnly.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    var inputText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Welcome, ${userConfig.name}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Daily Total", style = MaterialTheme.typography.labelLarge)
                Text("$dailyTotal / ${userConfig.dailyCalorieGoal} kcal", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                LinearProgressIndicator(progress = { (dailyTotal.toFloat() / userConfig.dailyCalorieGoal.toFloat()).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = if (dailyTotal > userConfig.dailyCalorieGoal) Color.Red else MaterialTheme.colorScheme.primary)
            }
        }
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries) { entry -> FoodEntryItem(entry) }
        }
        if (isProcessing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        if (!isReadOnly) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = inputText, onValueChange = { inputText = it }, modifier = Modifier.weight(1f), placeholder = { Text("What did you eat?") }, enabled = !isProcessing, singleLine = true)
                IconButton(onClick = { if (inputText.isNotBlank()) { viewModel.sendMessage(inputText); inputText = "" } }, enabled = !isProcessing) { Icon(painterResource(id = R.drawable.ic_send), contentDescription = "Send") }
            }
        }
    }
}

@Composable
fun FoodEntryItem(entry: FoodEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = entry.foodName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = entry.amount, style = MaterialTheme.typography.bodySmall)
                Text(text = "P: ${entry.protein}g C: ${entry.carbs}g F: ${entry.fats}g", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(text = "${entry.calories} kcal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}
