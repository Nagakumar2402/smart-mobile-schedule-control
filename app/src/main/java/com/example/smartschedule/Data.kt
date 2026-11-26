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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    /** Package to open for Target.OTHER; empty means ask when sending. */
    val app: String = "",
    /** Notification sound URI; empty means the phone's default. */
    val sound: String = "",
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

@Database(entities = [Schedule::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun schedules(): ScheduleDao

    companion object {
        private val addApp = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE schedules ADD COLUMN app TEXT NOT NULL DEFAULT ''")
            }
        }

        private val addSound = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE schedules ADD COLUMN sound TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile private var instance: AppDatabase? = null

        fun dao(context: Context): ScheduleDao = (instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "schedules.db")
                .addMigrations(addApp, addSound).build().also { instance = it }
        }).schedules()
    }
}
