package com.example.neli_roseapp

import android.content.SharedPreferences
import com.example.neli_roseapp.db.ActivityEntity
import com.example.neli_roseapp.db.ContactEntity
import com.example.neli_roseapp.db.HydrationDayEntity
import com.example.neli_roseapp.db.MedicationEntity
import com.example.neli_roseapp.db.MedicationLogEntity
import com.example.neli_roseapp.db.NeliDatabase
import com.example.neli_roseapp.db.ScheduleCompletionEntity
import com.example.neli_roseapp.db.ScheduleEventEntity
import com.example.neli_roseapp.db.UserProfileEntity
import com.example.neli_roseapp.db.VitalReadingEntity
import org.json.JSONArray

/**
 * Earlier versions wrote every record twice: to SharedPreferences (which the screens read)
 * and to Room (a copy). The database is now the only place records live.
 *
 * The database migration already carries over the Room copy. This import covers what is left:
 * for each kind of record it only acts when the database has nothing for this person, and then
 * copies what the old SharedPreferences store holds. Nothing is deleted from SharedPreferences.
 *
 * Old "taken" and "done" marks and the old glass count had no date, so they are stored under
 * CareDates.LEGACY_DATE rather than being counted as today's.
 */
object LegacyPrefsImport {

    suspend fun run(prefs: SharedPreferences, db: NeliDatabase, uid: String) {
        val today = CareDates.today()

        // profile
        val name = prefs.getString("${uid}_name", null)
        if (!name.isNullOrBlank() && db.userDao().getProfile(uid) == null) {
            db.userDao().upsertProfile(
                UserProfileEntity(
                    userId = uid,
                    fullName = name,
                    phone = prefs.getString("${uid}_phone", "") ?: "",
                    email = prefs.getString("${uid}_email", "") ?: ""
                )
            )
        }

        // vitals (one undated reading)
        if (db.vitalsDao().countReadings(uid) == 0) {
            val values = VitalsValidator.parseLegacy(
                prefs.getString("${uid}_bp", "") ?: "",
                prefs.getString("${uid}_hr", "") ?: "",
                prefs.getString("${uid}_temp", "") ?: "",
                prefs.getString("${uid}_weight", "") ?: ""
            )
            if (!values.isEmpty) {
                db.vitalsDao().insertReading(
                    VitalReadingEntity(
                        userId = uid,
                        date = CareDates.LEGACY_DATE,
                        recordedAt = 0,
                        systolic = values.systolic,
                        diastolic = values.diastolic,
                        heartRate = values.heartRate,
                        temperatureC = values.temperatureC,
                        weightKg = values.weightKg
                    )
                )
            }
        }

        // hydration
        if (prefs.contains("${uid}_glasses") && db.hydrationDao().countDays(uid) == 0) {
            db.hydrationDao().upsert(
                HydrationDayEntity(userId = uid, date = CareDates.LEGACY_DATE, glasses = prefs.getInt("${uid}_glasses", 0))
            )
        }

        // medications
        if (db.medicationDao().countAll(uid) == 0) {
            for (item in readArray(prefs.getString("${uid}_meds", null))) {
                val medName = item.optString("name")
                if (medName.isBlank()) continue
                val id = db.medicationDao().insert(
                    MedicationEntity(userId = uid, name = medName, time = item.optString("time"), startDate = today, endDate = null)
                )
                if (item.optBoolean("taken", false)) {
                    db.medicationDao().insertLog(
                        MedicationLogEntity(medicationId = id, userId = uid, date = CareDates.LEGACY_DATE, takenAt = 0)
                    )
                }
            }
        }

        // schedule events (the old list was shown every day, so they become daily events)
        if (db.scheduleDao().countAll(uid) == 0) {
            for (item in readArray(prefs.getString("${uid}_events", null))) {
                val title = item.optString("title")
                if (title.isBlank()) continue
                val id = db.scheduleDao().insert(
                    ScheduleEventEntity(
                        userId = uid,
                        title = title,
                        note = item.optString("note"),
                        time = item.optString("time"),
                        startDate = today,
                        repeatDaily = true,
                        endDate = null
                    )
                )
                if (item.optBoolean("done", false)) {
                    db.scheduleDao().insertCompletion(
                        ScheduleCompletionEntity(eventId = id, userId = uid, date = CareDates.LEGACY_DATE, completedAt = 0)
                    )
                }
            }
        }

        // contacts
        if (db.contactDao().countAll(uid) == 0) {
            for (item in readArray(prefs.getString("${uid}_contacts", null))) {
                val contactName = item.optString("name")
                if (contactName.isBlank()) continue
                db.contactDao().insert(
                    ContactEntity(userId = uid, name = contactName, phone = item.optString("phone"), isCaregiver = false)
                )
            }
        }

        // activities (which ones the person joined)
        if (db.activityDao().getActivities(uid).isEmpty()) {
            val activities = readArray(prefs.getString("${uid}_activities", null)).mapNotNull { item ->
                val activityName = item.optString("name")
                if (activityName.isBlank()) {
                    null
                } else {
                    ActivityEntity(
                        userId = uid,
                        time = item.optString("time"),
                        name = activityName,
                        place = item.optString("place"),
                        joined = item.optBoolean("joined", false)
                    )
                }
            }
            if (activities.isNotEmpty()) db.activityDao().insertActivities(activities)
        }
    }

    /** The objects of a stored JSON list. Text that is missing or cannot be read gives an empty list. */
    private fun readArray(json: String?): List<org.json.JSONObject> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        }.getOrDefault(emptyList())
    }
}
