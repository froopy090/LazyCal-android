package com.francescocanossi.lazycal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.francescocanossi.lazycal.screens.ChatScreen
import com.francescocanossi.lazycal.screens.DownloadingScreen
import com.francescocanossi.lazycal.screens.FoodDetailScreen
import com.francescocanossi.lazycal.screens.HistoryScreen
import com.francescocanossi.lazycal.screens.ProgressScreen
import com.francescocanossi.lazycal.screens.SettingsScreen
import com.francescocanossi.lazycal.screens.WelcomeScreen
import com.francescocanossi.lazycal.ui.theme.LazyCalTheme

enum class TabItem(val title: String, val iconRes: Int) {
    Tracker("Tracker", R.drawable.ic_tracker),
    Progress("Progress", R.drawable.ic_progress),
    History("History", R.drawable.ic_history)
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val chatViewModel: ChatViewModel = viewModel()
            val progressViewModel: ProgressViewModel = viewModel()
            val supportViewModel: SupportViewModel = viewModel()
            val userConfig by supportViewModel.userConfig.collectAsState()
            
            val systemInDarkTheme = isSystemInDarkTheme()
            val darkTheme = remember(userConfig.themeMode, systemInDarkTheme) {
                when (userConfig.themeMode) {
                    "light" -> false
                    "dark" -> true
                    else -> systemInDarkTheme
                }
            }

            LazyCalTheme(darkTheme = darkTheme) {
                val uiState by chatViewModel.uiState.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }
                val inputErrorMessage by chatViewModel.inputErrorMessage.collectAsState()
                val isOnline by chatViewModel.isOnline.collectAsState()
                
                val tabs = remember { TabItem.entries }
                val pagerState = rememberPagerState(pageCount = { tabs.size })
                val currentTab = tabs[pagerState.currentPage]
                
                var showSettings by rememberSaveable { mutableStateOf(false) }
                var showCalorieCalculator by rememberSaveable { mutableStateOf(false) }
                var showProfile by rememberSaveable { mutableStateOf(false) }

                val isReadOnly by chatViewModel.isReadOnly.collectAsState()
                val detailEntry by chatViewModel.detailEntry.collectAsState()
                val totalEntriesCount by supportViewModel.totalEntriesCount.collectAsState()
                val uriHandler = LocalUriHandler.current

                val scope = rememberCoroutineScope()

                val shouldShowSupportPrompt = remember(userConfig, totalEntriesCount) {
                    !userConfig.hasDonatedOrDismissed && 
                    userConfig.launchCount >= 10 && 
                    totalEntriesCount >= 20
                }

                if (shouldShowSupportPrompt) {
                    AlertDialog(
                        onDismissRequest = { /* Don't dismiss on outside tap to ensure decision */ },
                        title = { Text("Enjoying LazyCal?") },
                        text = { Text("I hope this app is helping you reach your goals! I'm a solo developer, and your support is greatly appreciated.") },
                        confirmButton = {
                            Button(onClick = { 
                                supportViewModel.dismissDonationPrompt()
                                uriHandler.openUri("https://ko-fi.com/froopy070") 
                            }) {
                                Text("Support my work")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { supportViewModel.dismissDonationPrompt() }) {
                                Text("Maybe later")
                            }
                        }
                    )
                }

                BackHandler(enabled = showSettings || showCalorieCalculator || showProfile || currentTab != TabItem.Tracker || isReadOnly || detailEntry != null) {
                    if (detailEntry != null) {
                        chatViewModel.dismissDetail()
                    } else if (showProfile) {
                        showProfile = false
                    } else if (showCalorieCalculator) {
                        showCalorieCalculator = false
                    } else if (showSettings) {
                        showSettings = false
                    } else if (currentTab != TabItem.Tracker) {
                        scope.launch { pagerState.animateScrollToPage(tabs.indexOf(TabItem.Tracker)) }
                        chatViewModel.resetToToday()
                    } else if (isReadOnly) {
                        chatViewModel.resetToToday()
                    }
                }

                LaunchedEffect(inputErrorMessage) {
                    inputErrorMessage?.let {
                        snackbarHostState.showSnackbar(it)
                        chatViewModel.clearInputError()
                    }
                }

                if (detailEntry != null) {
                    FoodDetailScreen(
                        entry = detailEntry!!,
                        viewModel = chatViewModel,
                        onBack = { chatViewModel.dismissDetail() }
                    )
                } else if (showProfile) {
                    com.francescocanossi.lazycal.screens.ProfileScreen(
                        viewModel = chatViewModel,
                        onBack = { showProfile = false }
                    )
                } else if (showCalorieCalculator) {
                    com.francescocanossi.lazycal.screens.CalorieCalculatorScreen(
                        viewModel = chatViewModel,
                        onBack = { showCalorieCalculator = false }
                    )
                } else if (showSettings) {
                    SettingsScreen(
                        viewModel = chatViewModel,
                        onBack = { showSettings = false },
                        onNavigateToCalculator = { showCalorieCalculator = true },
                        onNavigateToProfile = { showProfile = true }
                    )
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            if (uiState == ChatState.Ready) {
                                TopAppBar(
                                    title = {
                                        Text(
                                            "LazyCal",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    actions = {
                                        IconButton(onClick = { showSettings = true }) {
                                            Icon(
                                                painterResource(id = R.drawable.ic_settings),
                                                contentDescription = "Settings"
                                            )
                                        }
                                    }
                                )
                            }
                        },
                        bottomBar = {
                            if (uiState == ChatState.Ready) {
                                NavigationBar {
                                    tabs.forEachIndexed { index, tab ->
                                        NavigationBarItem(
                                            icon = {
                                                Icon(
                                                    painterResource(tab.iconRes),
                                                    contentDescription = tab.title
                                                )
                                            },
                                            label = { Text(tab.title) },
                                            selected = pagerState.currentPage == index,
                                            onClick = {
                                                scope.launch { pagerState.animateScrollToPage(index) }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                                when (uiState) {
                                ChatState.CheckingModel -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                ChatState.ModelMissing -> WelcomeScreen(isOnline = isOnline, onDownloadClick = { chatViewModel.startDownload() })
                                ChatState.Downloading -> DownloadingScreen()
                                ChatState.Ready -> {
                                    HorizontalPager(
                                        state = pagerState,
                                        modifier = Modifier.fillMaxSize(),
                                        userScrollEnabled = true,
                                        beyondViewportPageCount = 1,
                                        key = { tabs[it] }
                                    ) { page ->
                                        when (tabs[page]) {
                                            TabItem.Tracker -> ChatScreen(chatViewModel)
                                            TabItem.Progress -> ProgressScreen(progressViewModel)
                                            TabItem.History -> HistoryScreen(
                                                chatViewModel,
                                                onDaySelected = {
                                                    scope.launch { pagerState.animateScrollToPage(tabs.indexOf(TabItem.Tracker)) }
                                                })
                                        }
                                    }
                                }
                                is ChatState.Error -> Column(
                                    modifier = Modifier.align(Alignment.Center),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(text = (uiState as ChatState.Error).message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                                    Button(onClick = { chatViewModel.retry() }) { Text("Retry") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
