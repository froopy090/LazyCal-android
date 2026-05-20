package com.francescocanossi.lazycal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CsvManager(private val foodDao: FoodDao) {

    suspend fun getExportCSV(): String = withContext(Dispatchers.IO) {
        val entries = foodDao.getAllEntries()
        val csv = StringBuilder("Date,Food Item,Amount,Calories,Protein(g),Carbs(g),Fats(g),Original Input\n")
        entries.forEach { entry ->
            csv.append("${entry.dayId},")
            csv.append("\"${entry.foodName.replace("\"", "\"\"")}\",")
            csv.append("\"${entry.amount.replace("\"", "\"\"")}\",")
            csv.append("${entry.calories},")
            csv.append("${entry.protein},")
            csv.append("${entry.carbs},")
            csv.append("${entry.fats},")
            csv.append("\"${entry.originalInput.replace("\"", "\"\"")}\"\n")
        }
        csv.toString()
    }

    suspend fun importFromCSV(csvData: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val lines = csvData.lines()
            if (lines.isEmpty()) return@withContext false
            
            // Skip header
            val dataLines = lines.drop(1).filter { it.isNotBlank() }
            if (dataLines.isEmpty()) return@withContext false

            // Wipe existing logs before importing
            foodDao.deleteAll()
            
            dataLines.forEach { line ->
                val parts = parseCsvLine(line)
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
