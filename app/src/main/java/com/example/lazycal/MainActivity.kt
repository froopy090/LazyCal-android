package com.example.lazycal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lazycal.ui.theme.LazyCalTheme
import kotlinx.coroutines.launch

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

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            Spacer(Modifier.height(12.dp))
                            Text("History", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                            Divider()
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
                        topBar = {
                            TopAppBar(
                                title = { Text("Lazy Cal Chat") },
                                navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_menu),
                                            contentDescription = "Menu"
                                        )
                                    }
                                },
                                actions = {
                                    SettingsMenu(onDeleteModel = { viewModel.deleteModel() })
                                }
                            )
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
                                        Text("Initializing Engine...")
                                    }
                                }
                                ChatState.Ready -> {
                                    ChatScreen(viewModel)
                                }
                                is ChatState.Error -> {
                                    Text(
                                        text = (uiState as ChatState.Error).message,
                                        color = Color.Red,
                                        modifier = Modifier.align(Alignment.Center)
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
fun SettingsMenu(onDeleteModel: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(id = R.drawable.ic_settings),
                contentDescription = "Settings"
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Delete Downloaded Model") },
                onClick = {
                    expanded = false
                    onDeleteModel()
                }
            )
        }
    }
}

@Composable
fun WelcomeScreen(onDownloadClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Welcome to Local AI Chat", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text("The Gemma 4 model needs to be downloaded (approx. 1.5GB).")
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onDownloadClick) {
            Text("Download Model")
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
        Text("Downloading model via Download Manager...")
        Text("Please check your notification bar for progress.")
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onCheckClick) {
            Text("I'm done downloading / Refresh")
        }
    }
}

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val messages by viewModel.messages.collectAsState()
    val incompleteResponse by viewModel.incompleteResponse.collectAsState()
    val isReadOnly by viewModel.isReadOnly.collectAsState()
    var inputText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            reverseLayout = false
        ) {
            items(messages) { message ->
                ChatBubble(message)
            }
            incompleteResponse?.let {
                item {
                    ChatBubble(ChatMessage(it, false))
                }
            }
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
                    placeholder = { Text("Type a message...") }
                )
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    }
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
                    text = "This is a read-only archived chat.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val color = if (message.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = alignment) {
        Surface(
            color = color,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
