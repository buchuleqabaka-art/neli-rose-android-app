package com.example.neli_roseapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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

// Opens the phone dialer with the number filled in
fun Context.dial(number: String) {
    val clean = number.filter { it.isDigit() || it == '+' }
    runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", clean, null))) }
        .onFailure { Toast.makeText(this, "No phone app is available", Toast.LENGTH_LONG).show() }
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
    fun saveVitals(context: Context, bp: String, hr: String, temp: String, weight: String): Boolean {
        val key = getUserKey()
        val currentGlasses = getHydration(context)
        val saved = getPrefs(context).edit()
            .putString("${key}_bp", bp)
            .putString("${key}_hr", hr)
            .putString("${key}_temp", temp)
            .putString("${key}_weight", weight)
            .commit()
        if (!saved) return false

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
            }.onFailure { android.util.Log.e("DataManager", "Could not mirror vitals to database", it) }
        }
        return true
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
        return getPrefs(context).getInt("${getUserKey()}_glasses", 0)
    }

    // Symptoms stay on this device and are never sent to the care team.
    fun getSymptoms(context: Context): List<Pair<String, Long>> {
        val json = getPrefs(context).getString("${getUserKey()}_symptoms", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                item.getString("name") to item.getLong("time")
            }
        }.getOrDefault(emptyList())
    }

    fun addSymptom(context: Context, name: String, time: Long): Boolean {
        val items = (listOf(name to time) + getSymptoms(context)).take(100)
        val array = JSONArray()
        items.forEach { (symptom, recordedAt) ->
            array.put(JSONObject().put("name", symptom).put("time", recordedAt))
        }
        return getPrefs(context).edit().putString("${getUserKey()}_symptoms", array.toString()).commit()
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
        val json = getPrefs(context).getString("${getUserKey()}_meds", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                Med(obj.getString("name"), obj.getString("time"), obj.getBoolean("taken"))
            }
        }.getOrDefault(emptyList())
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
        if (json.isNullOrBlank()) return emptyList()
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
        return list
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
        if (json.isNullOrBlank()) return emptyList()
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
        return list
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
        // Previously shipped example contacts must not be offered as real care contacts.
        val samplePhones = setOf("+2782111233", "+27115557788", "+27115550000")
        if (json.isNullOrBlank()) {
            return emptyList()
        }
        val list = mutableListOf<CareContact>()
        runCatching {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(CareContact(obj.getString("name"), obj.getString("phone")))
            }
        }
        return list.filterNot { contact -> contact.phone.filter { it.isDigit() || it == '+' } in samplePhones }
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
