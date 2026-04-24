package com.example.lazycal

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "food_entries")
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodName: String,
    val amount: String,
    val calories: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val dayId: String, // Format: YYYY-MM-DD
    val originalInput: String
)

@Entity(tableName = "user_config")
data class UserConfig(
    @PrimaryKey val id: Int = 0, // Single row configuration
    val name: String = "User",
    val dailyCalorieGoal: Int = 2000
)

@Dao
interface FoodDao {
    @Query("SELECT * FROM food_entries WHERE dayId = :dayId ORDER BY timestamp DESC")
    fun getEntriesForDay(dayId: String): Flow<List<FoodEntry>>

    @Query("SELECT SUM(calories) FROM food_entries WHERE dayId = :dayId")
    fun getDailyTotal(dayId: String): Flow<Int?>

    @Query("SELECT DISTINCT dayId FROM food_entries ORDER BY dayId DESC")
    fun getAllDays(): Flow<List<String>>

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

@Database(entities = [FoodEntry::class, UserConfig::class], version = 3, exportSchema = false)
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
