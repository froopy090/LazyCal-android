package com.francescocanossi.lazycal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CsvManager(private val foodDao: FoodDao, private val weightDao: WeightDao) {

    suspend fun getExportCSV(): String = withContext(Dispatchers.IO) {
        val foodEntries = foodDao.getAllEntries()
        val weightEntries = weightDao.getAllWeightEntriesSync()
        
        val csv = StringBuilder("Type,Date,Name/Value,Amount,Calories,Protein(g),Carbs(g),Fats(g),Original Input\n")
        
        foodEntries.forEach { entry ->
            csv.append("FOOD,")
            csv.append("${entry.dayId},")
            csv.append("\"${entry.foodName.replace("\"", "\"\"")}\",")
            csv.append("\"${entry.amount.replace("\"", "\"\"")}\",")
            csv.append("${entry.calories},")
            csv.append("${entry.protein},")
            csv.append("${entry.carbs},")
            csv.append("${entry.fats},")
            csv.append("\"${entry.originalInput.replace("\"", "\"\"")}\"\n")
        }
        
        weightEntries.forEach { entry ->
            csv.append("WEIGHT,")
            csv.append("${entry.dayId},")
            csv.append("${entry.weight},")
            csv.append(",,,,,") // Empty fields for food specific columns
            csv.append("\n")
        }
        
        csv.toString()
    }

    suspend fun importFromCSV(csvData: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val lines = csvData.lines()
            if (lines.isEmpty()) return@withContext false
            
            val header = lines.firstOrNull() ?: return@withContext false
            val dataLines = lines.drop(1).filter { it.isNotBlank() }
            
            val isOldFormat = !header.startsWith("Type")

            dataLines.forEach { line ->
                val parts = parseCsvLine(line)
                if (isOldFormat) {
                    if (parts.size >= 8) {
                        val entry = FoodEntry(
                            dayId = parts[0],
                            foodName = parts[1],
                            amount = parts[2],
                            calories = parts[3].toIntOrNull() ?: 0,
                            protein = parts[4].toIntOrNull() ?: 0,
                            carbs = parts[5].toIntOrNull() ?: 0,
                            fats = parts[6].toIntOrNull() ?: 0,
                            originalInput = parts[7]
                        )
                        foodDao.insert(entry)
                    }
                } else {
                    if (parts.size >= 3) {
                        val type = parts[0]
                        when (type) {
                            "FOOD" -> {
                                if (parts.size >= 9) {
                                    val entry = FoodEntry(
                                        dayId = parts[1],
                                        foodName = parts[2],
                                        amount = parts[3],
                                        calories = parts[4].toIntOrNull() ?: 0,
                                        protein = parts[5].toIntOrNull() ?: 0,
                                        carbs = parts[6].toIntOrNull() ?: 0,
                                        fats = parts[7].toIntOrNull() ?: 0,
                                        originalInput = parts[8]
                                    )
                                    foodDao.insert(entry)
                                }
                            }
                            "WEIGHT" -> {
                                val weight = parts[2].toDoubleOrNull()
                                if (weight != null) {
                                    val entry = WeightEntry(
                                        dayId = parts[1],
                                        weight = weight
                                    )
                                    weightDao.insert(entry)
                                }
                            }
                        }
                    }
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var currentPart = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    currentPart.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(currentPart.toString())
                currentPart = StringBuilder()
            } else {
                currentPart.append(c)
            }
            i++
        }
        result.add(currentPart.toString())
        return result
    }
}
