package com.example.neli_roseapp.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserProfileEntity::class,
        VitalsEntity::class,
        MedicationEntity::class,
        ScheduleEventEntity::class,
        ActivityEntity::class,
        ContactEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class NeliDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun vitalsDao(): VitalsDao
    abstract fun medicationDao(): MedicationDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun activityDao(): ActivityDao
    abstract fun contactDao(): ContactDao

    companion object {
        @Volatile
        private var INSTANCE: NeliDatabase? = null

        fun getInstance(context: Context): NeliDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NeliDatabase::class.java,
                    "neli_app_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
