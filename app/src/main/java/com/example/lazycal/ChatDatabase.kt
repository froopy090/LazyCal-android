package com.example.lazycal

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "food_entries")
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodName: String,
    val amount: String,
    val calories: Int,
    val protein: Int = 0,
    val carbs: Int = 0,
    val fats: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val dayId: String, // Format: YYYY-MM-DD
    val originalInput: String
)

@Entity(tableName = "user_config")
data class UserConfig(
    @PrimaryKey val id: Int = 0, // Single row configuration
    val dailyCalorieGoal: Int = 2000,
    val themeMode: String = "auto" // "auto", "light", "dark"
)

data class DaySummary(
    val dayId: String,
    val totalCalories: Int,
    val totalProtein: Int,
    val totalCarbs: Int,
    val totalFats: Int
)

@Dao
interface FoodDao {
    @Query("SELECT * FROM food_entries WHERE dayId = :dayId ORDER BY timestamp DESC")
    fun getEntriesForDay(dayId: String): Flow<List<FoodEntry>>

    @Query("SELECT SUM(calories) FROM food_entries WHERE dayId = :dayId")
    fun getDailyTotal(dayId: String): Flow<Int?>

    @Query("SELECT dayId, SUM(calories) as totalCalories, SUM(protein) as totalProtein, SUM(carbs) as totalCarbs, SUM(fats) as totalFats FROM food_entries GROUP BY dayId ORDER BY dayId DESC")
    fun getAllDaySummaries(): Flow<List<DaySummary>>

    @Query("SELECT DISTINCT dayId FROM food_entries ORDER BY dayId DESC")
    fun getAllDays(): Flow<List<String>>

    @Query("SELECT * FROM food_entries ORDER BY timestamp DESC")
    suspend fun getAllEntries(): List<FoodEntry>

    @Insert
    suspend fun insert(entry: FoodEntry)

    @Delete
    suspend fun delete(entry: FoodEntry)

    @Query("DELETE FROM food_entries")
    suspend fun deleteAll()
}

@Dao
interface UserConfigDao {
    @Query("SELECT * FROM user_config WHERE id = 0")
    fun getUserConfig(): Flow<UserConfig?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserConfig(config: UserConfig)
}

@Database(entities = [FoodEntry::class, UserConfig::class], version = 6, exportSchema = false)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun userConfigDao(): UserConfigDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null

        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "lazycal_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
