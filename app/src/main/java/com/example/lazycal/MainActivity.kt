package com.example.lazycal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lazycal.ui.theme.LazyCalTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
                val chatViewModel: ChatViewModel = viewModel()
                val progressViewModel: ProgressViewModel = viewModel()
                
                val uiState by chatViewModel.uiState.collectAsState()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val archivedDays by chatViewModel.archivedDays.collectAsState()
                val selectedDay by chatViewModel.selectedDay.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }
                val inputErrorMessage by chatViewModel.inputErrorMessage.collectAsState()
                
                var currentTab by rememberSaveable { mutableStateOf(TabItem.Tracker) }
                var showSettings by rememberSaveable { mutableStateOf(false) }

                val isReadOnly by chatViewModel.isReadOnly.collectAsState()

                val categorizedDays = remember(archivedDays) {
                    val weekAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.time
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    
                    val thisWeek = mutableListOf<DaySummary>()
                    val older = mutableListOf<DaySummary>()
                    
                    archivedDays.filter { it.dayId != chatViewModel.todayId }.forEach { summary ->
                        try {
                            val date = sdf.parse(summary.dayId)
                            if (date != null && date.after(weekAgo)) {
                                thisWeek.add(summary)
                            } else {
                                older.add(summary)
                            }
                        } catch (_: Exception) {
                            older.add(summary)
                        }
                    }
                    Pair(thisWeek, older)
                }
                val thisWeekDays = categorizedDays.first
                val olderDays = categorizedDays.second

                BackHandler(enabled = showSettings || currentTab != TabItem.Tracker || isReadOnly) {
                    if (showSettings) {
                        showSettings = false
                    } else if (currentTab != TabItem.Tracker) {
                        currentTab = TabItem.Tracker
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

                if (showSettings) {
                    SettingsScreen(viewModel = chatViewModel, onBack = { showSettings = false })
                } else {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            ModalDrawerSheet {
                                Spacer(Modifier.height(12.dp))
                                Text("History", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(vertical = 12.dp)
                                ) {
                                    item {
                                        Text("Today", modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                        val todaySummary = archivedDays.find { it.dayId == chatViewModel.todayId }
                                        NavigationDrawerItem(
                                            label = {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Today's Track")
                                                    if (todaySummary != null) {
                                                        Text("${todaySummary.totalCalories} kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            },
                                            selected = !isReadOnly && currentTab == TabItem.Tracker,
                                            onClick = {
                                                chatViewModel.resetToToday()
                                                currentTab = TabItem.Tracker
                                                scope.launch { drawerState.close() }
                                            },
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        Spacer(Modifier.height(16.dp))
                                    }

                                    if (thisWeekDays.isNotEmpty()) {
                                        item {
                                            Text("This Week", modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                        }
                                        items(thisWeekDays) { day ->
                                            NavigationDrawerItem(
                                                label = {
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                        Text(day.dayId)
                                                        Text("${day.totalCalories} kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                },
                                                selected = day.dayId == selectedDay && currentTab == TabItem.Tracker,
                                                onClick = {
                                                    chatViewModel.selectDay(day.dayId)
                                                    currentTab = TabItem.Tracker
                                                    scope.launch { drawerState.close() }
                                                },
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                        }
                                        item { Spacer(Modifier.height(16.dp)) }
                                    }

                                    if (olderDays.isNotEmpty()) {
                                        item {
                                            Text("Older", modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                        }
                                        items(olderDays) { day ->
                                            NavigationDrawerItem(
                                                label = {
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                        Text(day.dayId)
                                                        Text("${day.totalCalories} kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                },
                                                selected = day.dayId == selectedDay && currentTab == TabItem.Tracker,
                                                onClick = {
                                                    chatViewModel.selectDay(day.dayId)
                                                    currentTab = TabItem.Tracker
                                                    scope.launch { drawerState.close() }
                                                },
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                        }
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
                                                onClick = {
                                                    if (tab == TabItem.Tracker) {
                                                        chatViewModel.resetToToday()
                                                    }
                                                    currentTab = tab
                                                }
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
                                        Text(text = (uiState as ChatState.Error).message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
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