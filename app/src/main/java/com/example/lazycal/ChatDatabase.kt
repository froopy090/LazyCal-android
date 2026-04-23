package com.example.lazycal

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val dayId: String // Format: YYYY-MM-DD
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE dayId = :dayId ORDER BY timestamp ASC")
    fun getMessagesForDay(dayId: String): Flow<List<MessageEntity>>

    @Query("SELECT DISTINCT dayId FROM messages ORDER BY dayId DESC")
    fun getAllDays(): Flow<List<String>>

    @Insert
    suspend fun insert(message: MessageEntity)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}

@Database(entities = [MessageEntity::class], version = 1)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null

        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
