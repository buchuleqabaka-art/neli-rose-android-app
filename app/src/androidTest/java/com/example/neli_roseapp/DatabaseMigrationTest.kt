package com.example.neli_roseapp

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.neli_roseapp.db.MedicationLogEntity
import com.example.neli_roseapp.db.NeliDatabase
import com.example.neli_roseapp.db.ScheduleCompletionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks on a real device or emulator that updating the app keeps a person's records.
 * In Android Studio: right-click this file and choose Run.
 *
 * The test builds a database exactly as version 2 of the app left it, opens it with the
 * current app and reads the records back. Room compares the upgraded tables with what the app
 * expects while opening; if the migration were wrong, opening would fail and so would the test.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private val databaseName = "neli_migration_test"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    /** The tables and some records as the previous version of the app stored them. */
    private fun createVersion2Database() {
        val db = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        db.execSQL("CREATE TABLE IF NOT EXISTS `user_profiles` (`userId` TEXT NOT NULL, `fullName` TEXT NOT NULL, `phone` TEXT NOT NULL, `email` TEXT NOT NULL, PRIMARY KEY(`userId`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `vitals` (`userId` TEXT NOT NULL, `bp` TEXT NOT NULL, `hr` TEXT NOT NULL, `temp` TEXT NOT NULL, `weight` TEXT NOT NULL, `hydrationGlasses` INTEGER NOT NULL, PRIMARY KEY(`userId`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `medications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` TEXT NOT NULL, `name` TEXT NOT NULL, `time` TEXT NOT NULL, `taken` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `schedule_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` TEXT NOT NULL, `time` TEXT NOT NULL, `title` TEXT NOT NULL, `note` TEXT NOT NULL, `done` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `activities` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` TEXT NOT NULL, `time` TEXT NOT NULL, `name` TEXT NOT NULL, `place` TEXT NOT NULL, `joined` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `contacts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` TEXT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT NOT NULL)")

        db.execSQL("INSERT INTO user_profiles VALUES ('u1', 'Ann Example', '+27821234567', 'ann@example.com')")
        db.execSQL("INSERT INTO vitals VALUES ('u1', '120/80', '72 bpm', '36.6', '70', 5)")
        db.execSQL("INSERT INTO medications (userId, name, time, taken) VALUES ('u1', 'Metformin 500mg', 'Morning 08:00', 1)")
        db.execSQL("INSERT INTO medications (userId, name, time, taken) VALUES ('u1', 'Amlodipine 5mg', 'Evening 20:00', 0)")
        db.execSQL("INSERT INTO schedule_events (userId, time, title, note, done) VALUES ('u1', '08:00', 'Breakfast', 'porridge', 1)")
        db.execSQL("INSERT INTO schedule_events (userId, time, title, note, done) VALUES ('u1', '18:00', 'Dinner', '', 0)")
        db.execSQL("INSERT INTO activities (userId, time, name, place, joined) VALUES ('u1', '15:00', 'Garden Walk', 'Outdoor Garden', 1)")
        db.execSQL("INSERT INTO contacts (userId, name, phone) VALUES ('u1', 'Sarah', '+27 82 111 2333')")
        db.version = 2
        db.close()
    }

    private fun openWithCurrentApp(): NeliDatabase =
        Room.databaseBuilder(context, NeliDatabase::class.java, databaseName)
            .addMigrations(NeliDatabase.MIGRATION_2_3)
            .build()

    @Test
    fun updatingFromVersion2KeepsTheRecords() = runBlocking<Unit> {
        createVersion2Database()
        val db = openWithCurrentApp()
        try {
            val today = CareDates.today()

            assertEquals("Ann Example", db.userDao().getProfile("u1")?.fullName)

            // the old free-text vitals became one reading with numbers
            val reading = db.vitalsDao().latestReading("u1")
            assertNotNull(reading)
            assertEquals(120, reading?.systolic)
            assertEquals(80, reading?.diastolic)
            assertEquals(72, reading?.heartRate)
            // it had no date, so it does not count as a reading made today
            assertNull(db.vitalsDao().latestReadingOn("u1", today))

            // both medicines are still listed; the old "taken" mark is not counted as today's
            val medications = db.medicationDao().activeOn("u1", today)
            assertEquals(listOf("Metformin 500mg", "Amlodipine 5mg"), medications.map { it.name })
            assertTrue(db.medicationDao().takenIdsOn("u1", today).isEmpty())
            assertEquals(1, db.medicationDao().takenIdsOn("u1", CareDates.LEGACY_DATE).size)

            // both events are still listed, as daily events, with nothing ticked for today
            val events = db.scheduleDao().eventsOn("u1", today)
            assertEquals(listOf("Breakfast", "Dinner"), events.map { it.title })
            assertTrue(events.all { it.repeatDaily })
            assertTrue(db.scheduleDao().completedIdsOn("u1", today).isEmpty())

            // the old glass count is kept as an old record; today starts at zero
            assertNull(db.hydrationDao().glassesOn("u1", today))
            assertEquals(5, db.hydrationDao().glassesOn("u1", CareDates.LEGACY_DATE))

            val contacts = db.contactDao().forUser("u1")
            assertEquals(listOf("Sarah"), contacts.map { it.name })
            assertNull(db.contactDao().caregiver("u1"))

            assertEquals(1, db.activityDao().getActivities("u1").size)
        } finally {
            db.close()
        }
    }

    @Test
    fun eachDayKeepsItsOwnProgress() = runBlocking<Unit> {
        createVersion2Database()
        val db = openWithCurrentApp()
        try {
            val today = CareDates.today()
            val tomorrow = CareDates.plusDays(today, 1)
            val medication = db.medicationDao().activeOn("u1", today).first()
            val event = db.scheduleDao().eventsOn("u1", today).first()

            db.medicationDao().insertLog(MedicationLogEntity(medication.id, "u1", today, System.currentTimeMillis()))
            db.scheduleDao().insertCompletion(ScheduleCompletionEntity(event.id, "u1", today, System.currentTimeMillis()))

            // marked for today only: tomorrow starts unmarked, and today's record stays
            assertEquals(listOf(medication.id), db.medicationDao().takenIdsOn("u1", today))
            assertTrue(db.medicationDao().takenIdsOn("u1", tomorrow).isEmpty())
            assertEquals(listOf(event.id), db.scheduleDao().completedIdsOn("u1", today))
            assertTrue(db.scheduleDao().completedIdsOn("u1", tomorrow).isEmpty())
            assertEquals(2, db.scheduleDao().eventsOn("u1", tomorrow).size)
        } finally {
            db.close()
        }
    }
}
