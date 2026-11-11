package com.example.neli_roseapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.neli_roseapp.db.ActivityEntity
import com.example.neli_roseapp.db.ContactEntity
import com.example.neli_roseapp.db.HydrationDayEntity
import com.example.neli_roseapp.db.MedicationEntity
import com.example.neli_roseapp.db.MedicationLogEntity
import com.example.neli_roseapp.db.NeliDatabase
import com.example.neli_roseapp.db.ScheduleCompletionEntity
import com.example.neli_roseapp.db.ScheduleEventEntity
import com.example.neli_roseapp.db.SymptomLogEntity
import com.example.neli_roseapp.db.UserProfileEntity
import com.example.neli_roseapp.db.VitalReadingEntity
import com.google.firebase.auth.FirebaseAuth
import java.time.LocalTime
import java.util.Locale

// Data models used across fragments
data class TodayActivity(val time: String, val name: String, val place: String, val joined: Boolean = false)

/** A medicine together with whether it was taken on the day being shown. */
data class MedicationStatus(val medication: MedicationEntity, val taken: Boolean)

/** A schedule event together with whether it was ticked off on the day being shown. */
data class ScheduleStatus(val event: ScheduleEventEntity, val done: Boolean)

/** Everything the Home screen shows, worked out from the saved records. */
data class Dashboard(
    val medsTaken: Int,
    val medsTotal: Int,
    val activitiesJoined: Int,
    val activitiesTotal: Int,
    val vitalsToday: VitalReadingEntity?,
    val reminder: NextReminder
)

/** Opens the phone dialler with the number filled in. Returns false when the device has no dialler. */
fun Context.dial(number: String): Boolean {
    val clean = CareRules.cleanPhone(number)
    return runCatching {
        startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", clean, null)))
    }.isSuccess
}

/**
 * The single access point for a person's records.
 *
 * Everything is read from and written to the Room database, keyed by the signed-in person's
 * Firebase id. There is no shared fallback account: without a signed-in person nothing is saved
 * and reads come back empty.
 */
object DataManager {
    private const val PREFS_NAME = "neli_user_prefs"

    /**
     * The home's programme for the day. It is still fixed in the app, because there is no screen
     * yet where staff can change it. Only the "joined" choice is saved per person.
     */
    private val defaultActivities = listOf(
        TodayActivity("15:00", "Garden Walk", "Outdoor Garden"),
        TodayActivity("16:00", "Crafts and knitting", "Crafts Room")
    )

    /** The signed-in person's id, or null when nobody is signed in. */
    fun currentUserId(): String? =
        runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()

    private fun db(context: Context) = NeliDatabase.getInstance(context)

    private fun capitalizeWords(input: String): String {
        if (input.isBlank()) return "User"
        return input.split(" ", ".", "_", "-").filter { it.isNotBlank() }.joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }

    /**
     * Run once after sign-in, before any screen reads data.
     * Earlier versions kept the records in SharedPreferences. Anything that exists only there is
     * copied into the database the first time each account is used after the update.
     */
    suspend fun prepareForUser(context: Context) {
        val uid = currentUserId() ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val doneKey = "${uid}_moved_to_database"
        if (prefs.getBoolean(doneKey, false)) return
        LegacyPrefsImport.run(prefs, db(context), uid)
        prefs.edit().putBoolean(doneKey, true).apply()
    }

    // --- User profile ---

    suspend fun saveUserProfile(context: Context, name: String, phone: String, email: String) {
        val uid = currentUserId() ?: return
        db(context).userDao().upsertProfile(
            UserProfileEntity(userId = uid, fullName = capitalizeWords(name), phone = phone, email = email)
        )
    }

    /** Creates the local profile the first time an account signs in on this device. An existing one is kept. */
    suspend fun ensureUserProfile(context: Context, fallbackName: String, email: String) {
        val uid = currentUserId() ?: return
        val dao = db(context).userDao()
        if (dao.getProfile(uid) == null) {
            dao.upsertProfile(
                UserProfileEntity(userId = uid, fullName = capitalizeWords(fallbackName), phone = "", email = email)
            )
        }
    }

    suspend fun getUserProfile(context: Context): UserProfileEntity? {
        val uid = currentUserId() ?: return null
        return db(context).userDao().getProfile(uid)
    }

    suspend fun getUserName(context: Context): String {
        val saved = getUserProfile(context)?.fullName?.takeIf { it.isNotBlank() }
        val firebaseUser = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
        val rawName = saved ?: firebaseUser?.displayName ?: firebaseUser?.email?.substringBefore("@") ?: "User"
        return capitalizeWords(rawName)
    }

    // --- Vitals ---

    /** Adds a reading for today. Earlier readings are kept. */
    suspend fun saveVitals(context: Context, values: VitalsValues): Boolean {
        val uid = currentUserId() ?: return false
        db(context).vitalsDao().insertReading(
            VitalReadingEntity(
                userId = uid,
                date = CareDates.today(),
                recordedAt = System.currentTimeMillis(),
                systolic = values.systolic,
                diastolic = values.diastolic,
                heartRate = values.heartRate,
                temperatureC = values.temperatureC,
                weightKg = values.weightKg
            )
        )
        return true
    }

    suspend fun getLatestVitals(context: Context): VitalReadingEntity? {
        val uid = currentUserId() ?: return null
        return db(context).vitalsDao().latestReading(uid)
    }

    suspend fun getVitalsOn(context: Context, date: String): VitalReadingEntity? {
        val uid = currentUserId() ?: return null
        return db(context).vitalsDao().latestReadingOn(uid, date)
    }

    // --- Hydration (one count per day) ---

    suspend fun getHydration(context: Context, date: String): Int {
        val uid = currentUserId() ?: return 0
        return db(context).hydrationDao().glassesOn(uid, date) ?: 0
    }

    suspend fun saveHydration(context: Context, date: String, glasses: Int) {
        val uid = currentUserId() ?: return
        db(context).hydrationDao().upsert(HydrationDayEntity(userId = uid, date = date, glasses = glasses))
    }

    // --- Medications (taken or not is recorded per day) ---

    suspend fun getMedications(context: Context, date: String): List<MedicationStatus> {
        val uid = currentUserId() ?: return emptyList()
        val dao = db(context).medicationDao()
        val takenIds = dao.takenIdsOn(uid, date).toSet()
        return dao.activeOn(uid, date)
            .sortedWith(compareBy({ CareTimes.minutesOfDay(it.time) ?: Int.MAX_VALUE }, { it.id }))
            .map { MedicationStatus(it, it.id in takenIds) }
    }

    suspend fun addMedication(context: Context, name: String, time: String): Boolean {
        val uid = currentUserId() ?: return false
        db(context).medicationDao().insert(
            MedicationEntity(userId = uid, name = name, time = time, startDate = CareDates.today(), endDate = null)
        )
        return true
    }

    suspend fun updateMedication(context: Context, medication: MedicationEntity, name: String, time: String) {
        db(context).medicationDao().update(medication.copy(name = name, time = time))
    }

    /** Stops a medicine from today. Days before today keep their record of it. */
    suspend fun removeMedication(context: Context, medication: MedicationEntity) {
        val dao = db(context).medicationDao()
        val today = CareDates.today()
        if (CareRules.removalPlan(true, medication.startDate, today) == RemovalPlan.STOP_FROM_TODAY) {
            dao.deleteLogsFrom(medication.id, today)
            dao.update(medication.copy(endDate = CareDates.plusDays(today, -1)))
        } else {
            dao.deleteAllLogs(medication.id)
            dao.deleteById(medication.id)
        }
    }

    suspend fun setMedicationTaken(context: Context, medicationId: Long, date: String, taken: Boolean) {
        val uid = currentUserId() ?: return
        val dao = db(context).medicationDao()
        if (taken) {
            dao.insertLog(
                MedicationLogEntity(medicationId = medicationId, userId = uid, date = date, takenAt = System.currentTimeMillis())
            )
        } else {
            dao.deleteLog(medicationId, date)
        }
    }

    // --- Schedule events (done or not is recorded per day) ---

    suspend fun getScheduleEvents(context: Context, date: String): List<ScheduleStatus> {
        val uid = currentUserId() ?: return emptyList()
        val dao = db(context).scheduleDao()
        val doneIds = dao.completedIdsOn(uid, date).toSet()
        return dao.eventsOn(uid, date).map { ScheduleStatus(it, it.id in doneIds) }
    }

    suspend fun addScheduleEvent(
        context: Context,
        title: String,
        note: String,
        time: String,
        date: String,
        repeatDaily: Boolean
    ): Boolean {
        val uid = currentUserId() ?: return false
        db(context).scheduleDao().insert(
            ScheduleEventEntity(
                userId = uid,
                title = title,
                note = note,
                time = time,
                startDate = date,
                repeatDaily = repeatDaily,
                endDate = null
            )
        )
        return true
    }

    /** A one-off event can be moved to another day. A repeating event keeps the day it started on. */
    suspend fun updateScheduleEvent(
        context: Context,
        event: ScheduleEventEntity,
        title: String,
        note: String,
        time: String,
        date: String
    ) {
        val dao = db(context).scheduleDao()
        val moved = !event.repeatDaily && date != event.startDate
        if (moved) dao.deleteAllCompletions(event.id)
        dao.update(
            event.copy(
                title = title,
                note = note,
                time = time,
                startDate = if (event.repeatDaily) event.startDate else date
            )
        )
    }

    /** A repeating event stops from today and keeps its earlier days. A one-off event is deleted. */
    suspend fun removeScheduleEvent(context: Context, event: ScheduleEventEntity) {
        val dao = db(context).scheduleDao()
        val today = CareDates.today()
        if (CareRules.removalPlan(event.repeatDaily, event.startDate, today) == RemovalPlan.STOP_FROM_TODAY) {
            dao.deleteCompletionsFrom(event.id, today)
            dao.update(event.copy(endDate = CareDates.plusDays(today, -1)))
        } else {
            dao.deleteAllCompletions(event.id)
            dao.deleteById(event.id)
        }
    }

    suspend fun setEventDone(context: Context, eventId: Long, date: String, done: Boolean) {
        val uid = currentUserId() ?: return
        val dao = db(context).scheduleDao()
        if (done) {
            dao.insertCompletion(
                ScheduleCompletionEntity(eventId = eventId, userId = uid, date = date, completedAt = System.currentTimeMillis())
            )
        } else {
            dao.deleteCompletion(eventId, date)
        }
    }

    // --- Symptoms ---

    suspend fun logSymptom(context: Context, symptom: String, notes: String): Boolean {
        val uid = currentUserId() ?: return false
        db(context).symptomDao().insert(
            SymptomLogEntity(
                userId = uid,
                symptom = symptom,
                notes = notes,
                date = CareDates.today(),
                loggedAt = System.currentTimeMillis()
            )
        )
        return true
    }

    suspend fun getRecentSymptoms(context: Context, limit: Int): List<SymptomLogEntity> {
        val uid = currentUserId() ?: return emptyList()
        return db(context).symptomDao().recent(uid, limit)
    }

    // --- Contacts ---

    suspend fun getContacts(context: Context): List<ContactEntity> {
        val uid = currentUserId() ?: return emptyList()
        return db(context).contactDao().forUser(uid)
    }

    suspend fun addContact(context: Context, name: String, phone: String): Boolean {
        val uid = currentUserId() ?: return false
        db(context).contactDao().insert(ContactEntity(userId = uid, name = name, phone = phone, isCaregiver = false))
        return true
    }

    suspend fun updateContact(context: Context, contact: ContactEntity, name: String, phone: String) {
        db(context).contactDao().update(contact.copy(name = name, phone = phone))
    }

    suspend fun deleteContact(context: Context, contact: ContactEntity) {
        db(context).contactDao().deleteById(contact.id)
    }

    /** Makes this contact the person's caregiver. Any earlier choice is replaced. */
    suspend fun setCaregiver(context: Context, contactId: Long) {
        val uid = currentUserId() ?: return
        db(context).contactDao().setCaregiver(uid, contactId)
    }

    suspend fun getCaregiver(context: Context): ContactEntity? {
        val uid = currentUserId() ?: return null
        return db(context).contactDao().caregiver(uid)
    }

    // --- Activities ---

    suspend fun getActivities(context: Context): List<TodayActivity> {
        val uid = currentUserId() ?: return defaultActivities
        val saved = db(context).activityDao().getActivities(uid)
        if (saved.isEmpty()) return defaultActivities
        return saved.map { TodayActivity(it.time, it.name, it.place, it.joined) }
    }

    suspend fun saveActivities(context: Context, activities: List<TodayActivity>) {
        val uid = currentUserId() ?: return
        val dao = db(context).activityDao()
        dao.deleteActivitiesForUser(uid)
        dao.insertActivities(
            activities.map { ActivityEntity(userId = uid, time = it.time, name = it.name, place = it.place, joined = it.joined) }
        )
    }

    // --- Home screen ---

    /** Today's counts, vitals status and next reminder, all worked out from the saved records. */
    suspend fun getDashboard(context: Context): Dashboard {
        val today = CareDates.today()
        val medications = getMedications(context, today)
        val events = getScheduleEvents(context, today)
        val activities = getActivities(context)

        val pending = medications.filter { !it.taken }.map {
            PendingItem(it.medication.name, it.medication.time, ReminderCalculator.KIND_MEDICATION)
        } + events.filter { !it.done }.map {
            PendingItem(it.event.title, it.event.time, ReminderCalculator.KIND_SCHEDULE)
        }
        val now = LocalTime.now()

        return Dashboard(
            medsTaken = medications.count { it.taken },
            medsTotal = medications.size,
            activitiesJoined = activities.count { it.joined },
            activitiesTotal = activities.size,
            vitalsToday = getVitalsOn(context, today),
            reminder = ReminderCalculator.next(
                pending,
                now.hour * 60 + now.minute,
                medications.isNotEmpty() || events.isNotEmpty()
            )
        )
    }
}
