package com.francescocanossi.lazycal.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.francescocanossi.lazycal.ChatViewModel
import com.francescocanossi.lazycal.R
import kotlinx.coroutines.launch

enum class Gender(val label: String) {
    MALE("Male"),
    FEMALE("Female")
}

enum class ActivityLevel(val label: String, val factor: Double, val description: String) {
    SEDENTARY("Sedentary", 1.2, "Little or no exercise"),
    LIGHTLY_ACTIVE("Lightly Active", 1.375, "Light exercise 1-3 days/week"),
    MODERATELY_ACTIVE("Moderately Active", 1.55, "Moderate exercise 3-5 days/week"),
    VERY_ACTIVE("Very Active", 1.725, "Hard exercise 6-7 days/week"),
    EXTRA_ACTIVE("Extra Active", 1.9, "Very hard exercise & physical job")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalorieCalculatorScreen(viewModel: ChatViewModel, onBack: () -> Unit) {
    val userConfig by viewModel.userConfig.collectAsState()
    
    var age by remember { mutableStateOf(userConfig.age?.toString() ?: "") }
    var weight by remember { mutableStateOf(userConfig.weight?.toString() ?: "") }
    var height by remember { mutableStateOf(userConfig.height?.toString() ?: "") }
    var gender by remember { mutableStateOf(if (userConfig.gender == "Female") Gender.FEMALE else Gender.MALE) }
    var activityLevel by remember { 
        mutableStateOf(
            ActivityLevel.entries.find { it.label == userConfig.activityLevel } ?: ActivityLevel.SEDENTARY
        ) 
    }
    
    var calculatedTDEE by remember { mutableStateOf<Int?>(null) }
    
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    // Update local state when userConfig changes (initial load)
    LaunchedEffect(userConfig) {
        if (age.isEmpty()) age = userConfig.age?.toString() ?: ""
        if (weight.isEmpty()) weight = userConfig.weight?.toString() ?: ""
        if (height.isEmpty()) height = userConfig.height?.toString() ?: ""
        if (userConfig.gender != null) {
            gender = if (userConfig.gender == "Female") Gender.FEMALE else Gender.MALE
        }
        if (userConfig.activityLevel != null && activityLevel.label != userConfig.activityLevel) {
            ActivityLevel.entries.find { it.label == userConfig.activityLevel }?.let {
                activityLevel = it
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calorie Calculator") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(id = R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Enter your details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            OutlinedTextField(
                value = age,
                onValueChange = { if (it.all { c -> c.isDigit() }) age = it },
                label = { Text("Age") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = weight,
                onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) weight = it },
                label = { Text("Weight (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = height,
                onValueChange = { if (it.all { c -> c.isDigit() }) height = it },
                label = { Text("Height (cm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            
            Text("Gender", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Row(modifier = Modifier.fillMaxWidth()) {
                Gender.entries.forEach { option ->
                    Row(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .selectable(
                                selected = (gender == option),
                                onClick = { gender = option },
                                role = Role.RadioButton
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = (gender == option), onClick = null)
                        Text(text = option.label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            
            Text("Activity Level", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            ActivityLevel.entries.forEach { level ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .selectable(
                            selected = (activityLevel == level),
                            onClick = { activityLevel = level },
                            role = Role.RadioButton
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = (activityLevel == level), onClick = null)
                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        Text(text = level.label, style = MaterialTheme.typography.bodyLarge)
                        Text(text = level.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            
            Button(
                onClick = {
                    val a = age.toIntOrNull()
                    val w = weight.toDoubleOrNull()
                    val h = height.toDoubleOrNull()
                    
                    if (a != null && w != null && h != null) {
                        val bmr = if (gender == Gender.MALE) {
                            10 * w + 6.25 * h - 5 * a + 5
                        } else {
                            10 * w + 6.25 * h - 5 * a - 161
                        }
                        calculatedTDEE = (bmr * activityLevel.factor).toInt()
                        
                        // Also save profile details
                        viewModel.saveUserConfig(
                            goal = userConfig.dailyCalorieGoal,
                            themeMode = userConfig.themeMode,
                            age = a,
                            weight = w,
                            height = h,
                            gender = gender.label,
                            activityLevel = activityLevel.label
                        )

                        // Auto-scroll to show results
                        scope.launch {
                            kotlinx.coroutines.delay(100) // Small delay to let the result card appear
                            scrollState.animateScrollTo(scrollState.maxValue)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = age.isNotEmpty() && weight.isNotEmpty() && height.isNotEmpty()
            ) {
                Text("Calculate TDEE")
            }
            
            calculatedTDEE?.let { tdee ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Your estimated daily maintenance calories:")
                        Text("$tdee kcal", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                viewModel.saveUserConfig(
                                    goal = tdee,
                                    themeMode = userConfig.themeMode,
                                    age = age.toIntOrNull(),
                                    weight = weight.toDoubleOrNull(),
                                    height = height.toDoubleOrNull(),
                                    gender = gender.label,
                                    activityLevel = activityLevel.label
                                )
                                onBack()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Set as daily goal")
                        }
                    }
                }
            }
        }
    }
}
