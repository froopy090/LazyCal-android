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