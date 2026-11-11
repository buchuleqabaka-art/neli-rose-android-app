package com.example.neli_roseapp.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserProfileEntity::class,
        VitalReadingEntity::class,
        HydrationDayEntity::class,
        MedicationEntity::class,
        MedicationLogEntity::class,
        ScheduleEventEntity::class,
        ScheduleCompletionEntity::class,
        SymptomLogEntity::class,
        ActivityEntity::class,
        ContactEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class NeliDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun vitalsDao(): VitalsDao
    abstract fun hydrationDao(): HydrationDao
    abstract fun medicationDao(): MedicationDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun symptomDao(): SymptomDao
    abstract fun activityDao(): ActivityDao
    abstract fun contactDao(): ContactDao

    companion object {
        @Volatile
        private var INSTANCE: NeliDatabase? = null

        /** Version 2 to 3: the same records, now with dates. Nothing is dropped without being copied first. */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (statement in MigrationSql.V2_TO_V3) {
                    db.execSQL(statement)
                }
            }
        }

        fun getInstance(context: Context): NeliDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NeliDatabase::class.java,
                    "neli_app_database"
                )
                    .addMigrations(MIGRATION_2_3)
                    // Version 1 was never part of this repository, so its tables are unknown and
                    // cannot be converted. A device still on version 1 starts with an empty database.
                    // Every later version is upgraded by a migration that keeps the records.
                    .fallbackToDestructiveMigrationFrom(true, 1)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * The statements that turn a version 2 database into version 3, in order.
 *
 * Version 2 kept one undated set of vitals per person and a single taken/done flag per item.
 * Version 3 gives every record a date. Old flags had no date, so they are kept under
 * '0000-00-00' (CareDates.LEGACY_DATE) instead of being counted as today's.
 * Old medicines and events become daily items that start on the day of the upgrade.
 */
object MigrationSql {
    val V2_TO_V3: List<String> = listOf(
        // vitals -> vital_readings (numbers instead of free text) and hydration_days
        """CREATE TABLE IF NOT EXISTS `vital_readings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` TEXT NOT NULL, `date` TEXT NOT NULL, `recordedAt` INTEGER NOT NULL, `systolic` INTEGER, `diastolic` INTEGER, `heartRate` INTEGER, `temperatureC` REAL, `weightKg` REAL)""",
        """INSERT INTO `vital_readings` (`userId`, `date`, `recordedAt`, `systolic`, `diastolic`, `heartRate`, `temperatureC`, `weightKg`)
           SELECT `userId`, '0000-00-00', 0,
                  CASE WHEN instr(`bp`, '/') > 1 AND CAST(`bp` AS INTEGER) > 0 AND CAST(substr(`bp`, instr(`bp`, '/') + 1) AS INTEGER) > 0 THEN CAST(`bp` AS INTEGER) END,
                  CASE WHEN instr(`bp`, '/') > 1 AND CAST(`bp` AS INTEGER) > 0 AND CAST(substr(`bp`, instr(`bp`, '/') + 1) AS INTEGER) > 0 THEN CAST(substr(`bp`, instr(`bp`, '/') + 1) AS INTEGER) END,
                  CASE WHEN CAST(`hr` AS INTEGER) > 0 THEN CAST(`hr` AS INTEGER) END,
                  CASE WHEN CAST(replace(`temp`, ',', '.') AS REAL) > 0 THEN CAST(replace(`temp`, ',', '.') AS REAL) END,
                  CASE WHEN CAST(replace(`weight`, ',', '.') AS REAL) > 0 THEN CAST(replace(`weight`, ',', '.') AS REAL) END
           FROM `vitals`
           WHERE (instr(`bp`, '/') > 1 AND CAST(`bp` AS INTEGER) > 0 AND CAST(substr(`bp`, instr(`bp`, '/') + 1) AS INTEGER) > 0)
              OR CAST(`hr` AS INTEGER) > 0
              OR CAST(replace(`temp`, ',', '.') AS REAL) > 0
              OR CAST(replace(`weight`, ',', '.') AS REAL) > 0""",
        """CREATE TABLE IF NOT EXISTS `hydration_days` (`userId` TEXT NOT NULL, `date` TEXT NOT NULL, `glasses` INTEGER NOT NULL, PRIMARY KEY(`userId`, `date`))""",
        """INSERT OR REPLACE INTO `hydration_days` (`userId`, `date`, `glasses`) SELECT `userId`, '0000-00-00', `hydrationGlasses` FROM `vitals`""",
        """DROP TABLE `vitals`""",

        // medications: the taken flag becomes a dated log
        """CREATE TABLE IF NOT EXISTS `medications_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` TEXT NOT NULL, `name` TEXT NOT NULL, `time` TEXT NOT NULL, `startDate` TEXT NOT NULL, `endDate` TEXT)""",
        """INSERT INTO `medications_new` (`id`, `userId`, `name`, `time`, `startDate`, `endDate`) SELECT `id`, `userId`, `name`, `time`, date('now', 'localtime'), NULL FROM `medications`""",
        """CREATE TABLE IF NOT EXISTS `medication_logs` (`medicationId` INTEGER NOT NULL, `userId` TEXT NOT NULL, `date` TEXT NOT NULL, `takenAt` INTEGER NOT NULL, PRIMARY KEY(`medicationId`, `date`))""",
        """INSERT OR REPLACE INTO `medication_logs` (`medicationId`, `userId`, `date`, `takenAt`) SELECT `id`, `userId`, '0000-00-00', 0 FROM `medications` WHERE `taken` = 1""",
        """DROP TABLE `medications`""",
        """ALTER TABLE `medications_new` RENAME TO `medications`""",

        // schedule_events: the done flag becomes a dated completion
        """CREATE TABLE IF NOT EXISTS `schedule_events_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` TEXT NOT NULL, `title` TEXT NOT NULL, `note` TEXT NOT NULL, `time` TEXT NOT NULL, `startDate` TEXT NOT NULL, `repeatDaily` INTEGER NOT NULL, `endDate` TEXT)""",
        """INSERT INTO `schedule_events_new` (`id`, `userId`, `title`, `note`, `time`, `startDate`, `repeatDaily`, `endDate`) SELECT `id`, `userId`, `title`, `note`, `time`, date('now', 'localtime'), 1, NULL FROM `schedule_events`""",
        """CREATE TABLE IF NOT EXISTS `schedule_completions` (`eventId` INTEGER NOT NULL, `userId` TEXT NOT NULL, `date` TEXT NOT NULL, `completedAt` INTEGER NOT NULL, PRIMARY KEY(`eventId`, `date`))""",
        """INSERT OR REPLACE INTO `schedule_completions` (`eventId`, `userId`, `date`, `completedAt`) SELECT `id`, `userId`, '0000-00-00', 0 FROM `schedule_events` WHERE `done` = 1""",
        """DROP TABLE `schedule_events`""",
        """ALTER TABLE `schedule_events_new` RENAME TO `schedule_events`""",

        // contacts: one of them can now be marked as the caregiver
        """CREATE TABLE IF NOT EXISTS `contacts_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` TEXT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT NOT NULL, `isCaregiver` INTEGER NOT NULL)""",
        """INSERT INTO `contacts_new` (`id`, `userId`, `name`, `phone`, `isCaregiver`) SELECT `id`, `userId`, `name`, `phone`, 0 FROM `contacts`""",
        """DROP TABLE `contacts`""",
        """ALTER TABLE `contacts_new` RENAME TO `contacts`""",

        // new in version 3
        """CREATE TABLE IF NOT EXISTS `symptom_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` TEXT NOT NULL, `symptom` TEXT NOT NULL, `notes` TEXT NOT NULL, `date` TEXT NOT NULL, `loggedAt` INTEGER NOT NULL)"""
    )
}
