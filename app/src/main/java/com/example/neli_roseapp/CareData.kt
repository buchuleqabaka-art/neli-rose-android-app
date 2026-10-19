package com.example.neli_roseapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.neli_roseapp.db.ActivityEntity
import com.example.neli_roseapp.db.ContactEntity
import com.example.neli_roseapp.db.MedicationEntity
import com.example.neli_roseapp.db.NeliDatabase
import com.example.neli_roseapp.db.ScheduleEventEntity
import com.example.neli_roseapp.db.UserProfileEntity
import com.example.neli_roseapp.db.VitalsEntity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

// Data models used across fragments
data class CareContact(val name: String, val phone: String)
data class Med(val name: String, val time: String, val taken: Boolean)
data class DayEvent(val time: String, val title: String, val note: String = "", val done: Boolean = false)
data class TodayActivity(val time: String, val name: String, val place: String, val joined: Boolean = false)

object CareData {
    val sarah = CareContact("Sarah (Daughter)", "+27 82 111 233")
    val doctor = CareContact("Dr. Mokoena", "+27 11 555 7788")
    val nurse = CareContact("Nurse Thandi Dlamini", "+27 11 555 0000")
}

// Opens the phone dialer with the number filled in
fun Context.dial(number: String) {
    val clean = number.filter { it.isDigit() || it == '+' }
    startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", clean, null)))
}

// DataManager persists state using Room Database + SharedPreferences hand-in-hand
object DataManager {
    private const val PREFS_NAME = "neli_user_prefs"
    private val scope = CoroutineScope(Dispatchers.IO)

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getUserKey(): String {
        val uid = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
        return uid ?: "default_user"
    }

    private fun getDb(context: Context) = NeliDatabase.getInstance(context)

    private fun capitalizeWords(input: String): String {
        if (input.isBlank()) return "User"
        return input.split(" ", ".", "_", "-").filter { it.isNotBlank() }.joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }

    // --- User Profile (Room + SharedPreferences) ---
    fun saveUserProfile(context: Context, name: String, phone: String, email: String) {
        val key = getUserKey()
        val formattedName = capitalizeWords(name)
        getPrefs(context).edit()
            .putString("${key}_name", formattedName)
            .putString("${key}_phone", phone)
            .putString("${key}_email", email)
            .apply()

        scope.launch {
            runCatching {
                getDb(context).userDao().insertProfile(
                    UserProfileEntity(userId = key, fullName = formattedName, phone = phone, email = email)
                )
            }
        }
    }

    fun getUserName(context: Context): String {
        val firebaseUser = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
        val savedName = getPrefs(context).getString("${getUserKey()}_name", null)
        val rawName = savedName ?: firebaseUser?.displayName ?: firebaseUser?.email?.substringBefore("@") ?: "User"
        return capitalizeWords(rawName)
    }

    // --- Vitals & Hydration (Room + SharedPreferences) ---
    fun saveVitals(context: Context, bp: String, hr: String, temp: String, weight: String) {
        val key = getUserKey()
        val currentGlasses = getHydration(context)
        getPrefs(context).edit()
            .putString("${key}_bp", bp)
            .putString("${key}_hr", hr)
            .putString("${key}_temp", temp)
            .putString("${key}_weight", weight)
            .apply()

        scope.launch {
            runCatching {
                getDb(context).vitalsDao().insertVitals(
                    VitalsEntity(
                        userId = key,
                        bp = bp,
                        hr = hr,
                        temp = temp,
                        weight = weight,
                        hydrationGlasses = currentGlasses
                    )
                )
            }
        }
    }

    fun getVitals(context: Context): Map<String, String> {
        val prefs = getPrefs(context)
        val key = getUserKey()
        return mapOf(
            "bp" to (prefs.getString("${key}_bp", "") ?: ""),
            "hr" to (prefs.getString("${key}_hr", "") ?: ""),
            "temp" to (prefs.getString("${key}_temp", "") ?: ""),
            "weight" to (prefs.getString("${key}_weight", "") ?: "")
        )
    }

    fun saveHydration(context: Context, glasses: Int) {
        val key = getUserKey()
        getPrefs(context).edit().putInt("${key}_glasses", glasses).apply()

        val vitals = getVitals(context)
        scope.launch {
            runCatching {
                getDb(context).vitalsDao().insertVitals(
                    VitalsEntity(
                        userId = key,
                        bp = vitals["bp"] ?: "",
                        hr = vitals["hr"] ?: "",
                        temp = vitals["temp"] ?: "",
                        weight = vitals["weight"] ?: "",
                        hydrationGlasses = glasses
                    )
                )
            }
        }
    }

    fun getHydration(context: Context): Int {
        return getPrefs(context).getInt("${getUserKey()}_glasses", 6)
    }

    // --- Medications (Room + SharedPreferences) ---
    fun saveMeds(context: Context, meds: List<Med>) {
        val key = getUserKey()
        val array = JSONArray()
        meds.forEach { med ->
            val obj = JSONObject()
            obj.put("name", med.name)
            obj.put("time", med.time)
            obj.put("taken", med.taken)
            array.put(obj)
        }
        getPrefs(context).edit().putString("${key}_meds", array.toString()).apply()

        scope.launch {
            runCatching {
                val db = getDb(context)
                db.medicationDao().deleteMedsForUser(key)
                val entities = meds.map { MedicationEntity(userId = key, name = it.name, time = it.time, taken = it.taken) }
                db.medicationDao().insertMeds(entities)
            }
        }
    }

    fun getMeds(context: Context): List<Med> {
        val json = getPrefs(context).getString("${getUserKey()}_meds", null)
        if (json.isNullOrBlank()) {
            return listOf(
                Med("Metformin 500mg", "Morning 08:00", true),
                Med("Vitamin D + Calcium", "Afternoon 14:00", true),
                Med("Amlodipine 5mg", "Evening 20:00", false)
            )
        }
        val list = mutableListOf<Med>()
        runCatching {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(Med(obj.getString("name"), obj.getString("time"), obj.getBoolean("taken")))
            }
        }
        return if (list.isNotEmpty()) list else listOf(
            Med("Metformin 500mg", "Morning 08:00", true),
            Med("Vitamin D + Calcium", "Afternoon 14:00", true),
            Med("Amlodipine 5mg", "Evening 20:00", false)
        )
    }

    // --- Schedule Events (Room + SharedPreferences) ---
    fun saveScheduleEvents(context: Context, events: List<DayEvent>) {
        val key = getUserKey()
        val array = JSONArray()
        events.forEach { event ->
            val obj = JSONObject()
            obj.put("time", event.time)
            obj.put("title", event.title)
            obj.put("note", event.note)
            obj.put("done", event.done)
            array.put(obj)
        }
        getPrefs(context).edit().putString("${key}_events", array.toString()).apply()

        scope.launch {
            runCatching {
                val db = getDb(context)
                db.scheduleDao().deleteEventsForUser(key)
                val entities = events.map { ScheduleEventEntity(userId = key, time = it.time, title = it.title, note = it.note, done = it.done) }
                db.scheduleDao().insertEvents(entities)
            }
        }
    }

    fun getScheduleEvents(context: Context): List<DayEvent> {
        val json = getPrefs(context).getString("${getUserKey()}_events", null)
        if (json.isNullOrBlank()) {
            return listOf(
                DayEvent("07:00", "Wake up & Morning Routine", done = true),
                DayEvent("08:00", "Breakfast", done = true),
                DayEvent("10:00", "Morning Exercise", done = true),
                DayEvent("13:00", "Lunch", done = true),
                DayEvent("14:00", "Afternoon Medication"),
                DayEvent("18:00", "Dinner")
            )
        }
        val list = mutableListOf<DayEvent>()
        runCatching {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    DayEvent(
                        obj.getString("time"),
                        obj.getString("title"),
                        obj.optString("note", ""),
                        obj.getBoolean("done")
                    )
                )
            }
        }
        return if (list.isNotEmpty()) list else listOf(
            DayEvent("07:00", "Wake up & Morning Routine", done = true),
            DayEvent("08:00", "Breakfast", done = true),
            DayEvent("10:00", "Morning Exercise", done = true),
            DayEvent("13:00", "Lunch", done = true),
            DayEvent("14:00", "Afternoon Medication"),
            DayEvent("18:00", "Dinner")
        )
    }

    // --- Activities (Room + SharedPreferences) ---
    fun saveActivities(context: Context, activities: List<TodayActivity>) {
        val key = getUserKey()
        val array = JSONArray()
        activities.forEach { act ->
            val obj = JSONObject()
            obj.put("time", act.time)
            obj.put("name", act.name)
            obj.put("place", act.place)
            obj.put("joined", act.joined)
            array.put(obj)
        }
        getPrefs(context).edit().putString("${key}_activities", array.toString()).apply()

        scope.launch {
            runCatching {
                val db = getDb(context)
                db.activityDao().deleteActivitiesForUser(key)
                val entities = activities.map { ActivityEntity(userId = key, time = it.time, name = it.name, place = it.place, joined = it.joined) }
                db.activityDao().insertActivities(entities)
            }
        }
    }

    fun getActivities(context: Context): List<TodayActivity> {
        val json = getPrefs(context).getString("${getUserKey()}_activities", null)
        if (json.isNullOrBlank()) {
            return listOf(
                TodayActivity("15:00", "Garden Walk", "Outdoor Garden"),
                TodayActivity("16:00", "Crafts and knitting", "Crafts Room")
            )
        }
        val list = mutableListOf<TodayActivity>()
        runCatching {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    TodayActivity(
                        obj.getString("time"),
                        obj.getString("name"),
                        obj.getString("place"),
                        obj.getBoolean("joined")
                    )
                )
            }
        }
        return if (list.isNotEmpty()) list else listOf(
            TodayActivity("15:00", "Garden Walk", "Outdoor Garden"),
            TodayActivity("16:00", "Crafts and knitting", "Crafts Room")
        )
    }

    // --- Contacts (Room + SharedPreferences) ---
    fun saveContacts(context: Context, contacts: List<CareContact>) {
        val key = getUserKey()
        val array = JSONArray()
        contacts.forEach { c ->
            val obj = JSONObject()
            obj.put("name", c.name)
            obj.put("phone", c.phone)
            array.put(obj)
        }
        getPrefs(context).edit().putString("${key}_contacts", array.toString()).apply()

        scope.launch {
            runCatching {
                val db = getDb(context)
                db.contactDao().deleteContactsForUser(key)
                val entities = contacts.map { ContactEntity(userId = key, name = it.name, phone = it.phone) }
                db.contactDao().insertContacts(entities)
            }
        }
    }

    fun getContacts(context: Context): List<CareContact> {
        val json = getPrefs(context).getString("${getUserKey()}_contacts", null)
        val defaultContacts = listOf(CareData.sarah, CareData.doctor, CareData.nurse)
        if (json.isNullOrBlank()) {
            return defaultContacts
        }
        val list = mutableListOf<CareContact>()
        runCatching {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(CareContact(obj.getString("name"), obj.getString("phone")))
            }
        }
        return if (list.isNotEmpty()) list else defaultContacts
    }

    // --- Stat Counts ---
    fun saveStatCounts(context: Context, medsCount: Int, activitiesCount: Int) {
        val key = getUserKey()
        getPrefs(context).edit()
            .putInt("${key}_meds_count", medsCount)
            .putInt("${key}_activities_count", activitiesCount)
            .apply()
    }

    fun getMedsCount(context: Context): Int {
        val key = getUserKey()
        val saved = getPrefs(context).getInt("${key}_meds_count", -1)
        return if (saved >= 0) saved else getMeds(context).size
    }

    fun getActivitiesCount(context: Context): Int {
        val key = getUserKey()
        val saved = getPrefs(context).getInt("${key}_activities_count", -1)
        return if (saved >= 0) saved else getActivities(context).size
    }
}
