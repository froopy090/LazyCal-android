package com.example.lazycal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lazycal.ui.theme.LazyCalTheme
import kotlinx.coroutines.launch

enum class TabItem(val title: String, val iconRes: Int) {
    Tracker("Tracker", R.drawable.ic_tracker),
    Progress("Progress", R.drawable.ic_progress)
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LazyCalTheme {
                val viewModel: ChatViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val archivedDays by viewModel.archivedDays.collectAsState()
                val selectedDay by viewModel.selectedDay.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }
                val inputErrorMessage by viewModel.inputErrorMessage.collectAsState()
                
                var currentTab by remember { mutableStateOf(TabItem.Tracker) }
                var showSettings by remember { mutableStateOf(false) }

                // Handle transient input errors via Snackbar
                LaunchedEffect(inputErrorMessage) {
                    inputErrorMessage?.let {
                        snackbarHostState.showSnackbar(it)
                        viewModel.clearInputError()
                    }
                }

                if (showSettings) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = { showSettings = false }
                    )
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
                                                viewModel.selectDay(day)
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
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_menu),
                                                contentDescription = "Menu"
                                            )
                                        }
                                    },
                                    actions = {
                                        IconButton(onClick = { showSettings = true }) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_settings),
                                                contentDescription = "Settings"
                                            )
                                        }
                                    }
                                )
                            },
                            bottomBar = {
                                NavigationBar {
                                    TabItem.values().forEach { tab ->
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
                                    ChatState.CheckingModel -> {
                                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                    }
                                    ChatState.ModelMissing -> {
                                        WelcomeScreen(onDownloadClick = { viewModel.startDownload() })
                                    }
                                    ChatState.Downloading -> {
                                        DownloadingScreen(onCheckClick = { viewModel.onDownloadComplete() })
                                    }
                                    ChatState.Initializing -> {
                                        Column(
                                            modifier = Modifier.align(Alignment.Center),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            CircularProgressIndicator()
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Initializing Parser...")
                                        }
                                    }
                                    ChatState.Ready -> {
                                        when (currentTab) {
                                            TabItem.Tracker -> ChatScreen(viewModel)
                                            TabItem.Progress -> ProgressScreen()
                                        }
                                    }
                                    is ChatState.Error -> {
                                        Column(
                                            modifier = Modifier.align(Alignment.Center),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = (uiState as ChatState.Error).message,
                                                color = Color.Red,
                                                modifier = Modifier.padding(16.dp)
                                            )
                                            Button(onClick = { viewModel.onDownloadComplete() }) {
                                                Text("Retry Initialization")
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
                        Icon(painterResource(id = R.drawable.ic_menu), contentDescription = "Back", modifier = Modifier.size(24.dp))
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = goal,
                onValueChange = { if (it.all { char -> char.isDigit() }) goal = it },
                label = { Text("Daily Calorie Goal") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { 
                    viewModel.saveUserConfig(name, goal.toIntOrNull() ?: 2000)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Settings")
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { 
                    viewModel.deleteModel()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete All Data & Model")
            }
        }
    }
}

@Composable
fun ProgressScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Progress Page (Coming Soon)", style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
fun WelcomeScreen(onDownloadClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Lazy Calorie Tracker", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("AI-powered local calorie tracking. Describe what you ate, and let Gemma estimate the rest.", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onDownloadClick) {
            Text("Download Parser (1.5GB)")
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
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onCheckClick) {
            Text("Refresh / Check Status")
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
    var inputText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Welcome Header
        Text(
            text = "Welcome, ${userConfig.name}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Daily Total Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Daily Total", style = MaterialTheme.typography.labelLarge)
                Text("$dailyTotal / ${userConfig.dailyCalorieGoal} kcal", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                
                LinearProgressIndicator(
                    progress = { (dailyTotal.toFloat() / userConfig.dailyCalorieGoal.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    color = if (dailyTotal > userConfig.dailyCalorieGoal) Color.Red else MaterialTheme.colorScheme.primary
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries) { entry ->
                FoodEntryItem(entry)
            }
        }

        if (isProcessing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }

        if (!isReadOnly) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("e.g. 2 slices of pepperoni pizza") },
                    enabled = !isProcessing,
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
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
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Archived log.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun FoodEntryItem(entry: FoodEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = entry.foodName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = entry.amount, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "Input: \"${entry.originalInput}\"", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Text(
                text = "${entry.calories} kcal",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
