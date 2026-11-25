package com.example.smartschedule

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "schedules")
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val target: Target,
    val phone: String,
    val message: String,
    val triggerAt: Long,
    val daily: Boolean,
    val enabled: Boolean = true,
)

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY enabled DESC, triggerAt")
    fun observe(): Flow<List<Schedule>>

    @Query("SELECT * FROM schedules WHERE enabled = 1")
    suspend fun enabled(): List<Schedule>

    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun byId(id: Long): Schedule?

    @Insert
    suspend fun insert(schedule: Schedule): Long

    @Update
    suspend fun update(schedule: Schedule)

    @Delete
    suspend fun delete(schedule: Schedule)
}

@Database(entities = [Schedule::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun schedules(): ScheduleDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun dao(context: Context): ScheduleDao = (instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "schedules.db")
                .build().also { instance = it }
        }).schedules()
    }
}
