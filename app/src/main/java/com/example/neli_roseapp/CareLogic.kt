package com.example.neli_roseapp

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Rules that do not need Android: dates, times, vitals checks and the next reminder.
// Keeping them here means they can be covered by plain unit tests.

/** Dates are stored as ISO text (yyyy-MM-dd), so they sort and compare as plain strings. */
object CareDates {
    /** Used for records carried over from the old app, which never stored the day they belong to. */
    const val LEGACY_DATE = "0000-00-00"

    fun today(): String = LocalDate.now().toString()

    fun parse(date: String): LocalDate? = runCatching { LocalDate.parse(date) }.getOrNull()

    fun plusDays(date: String, days: Long): String =
        (parse(date) ?: LocalDate.now()).plusDays(days).toString()

    fun of(year: Int, month: Int, day: Int): String = LocalDate.of(year, month, day).toString()

    /** For example "Wednesday, 11 November 2026". */
    fun display(date: String): String {
        val parsed = parse(date) ?: return date
        return parsed.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault()))
    }
}

object CareTimes {
    private val anyTime = Regex("(\\d{1,2}):(\\d{2})")
    private val exactTime = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

    /**
     * Minutes since midnight for the first HH:mm found in the text, or null.
     * Entries from the old app hold text such as "Morning 08:00".
     */
    fun minutesOfDay(text: String): Int? {
        val match = anyTime.find(text) ?: return null
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        if (hour > 23 || minute > 59) return null
        return hour * 60 + minute
    }

    fun format(hour: Int, minute: Int): String = String.format(Locale.US, "%02d:%02d", hour, minute)

    fun isValid(text: String): Boolean = exactTime.matches(text)
}

/** One set of readings. A value is null when the person left that field empty. */
data class VitalsValues(
    val systolic: Int?,
    val diastolic: Int?,
    val heartRate: Int?,
    val temperatureC: Double?,
    val weightKg: Double?
) {
    val isEmpty: Boolean
        get() = systolic == null && diastolic == null && heartRate == null &&
            temperatureC == null && weightKg == null
}

/** Result of checking the vitals form: either the values, or a message for each field that is wrong. */
data class VitalsCheck(
    val values: VitalsValues?,
    val bloodPressureError: String? = null,
    val heartRateError: String? = null,
    val temperatureError: String? = null,
    val weightError: String? = null,
    val formError: String? = null
) {
    val isValid: Boolean get() = values != null
}

object VitalsValidator {
    const val UNIT_BLOOD_PRESSURE = "mmHg"
    const val UNIT_HEART_RATE = "bpm"
    const val UNIT_TEMPERATURE = "°C"
    const val UNIT_WEIGHT = "kg"

    // Limits of what a person can plausibly record. They catch typing mistakes; they are not medical advice.
    const val SYSTOLIC_MIN = 70
    const val SYSTOLIC_MAX = 250
    const val DIASTOLIC_MIN = 40
    const val DIASTOLIC_MAX = 150
    const val HEART_RATE_MIN = 30
    const val HEART_RATE_MAX = 220
    const val TEMPERATURE_MIN = 30.0
    const val TEMPERATURE_MAX = 45.0
    const val WEIGHT_MIN = 20.0
    const val WEIGHT_MAX = 300.0

    private val bloodPressure = Regex("^(\\d{2,3})\\s*/\\s*(\\d{2,3})$")
    private val wholeNumber = Regex("^\\d{1,3}$")
    private val decimalNumber = Regex("^\\d{1,3}([.,]\\d{1,2})?$")

    fun check(
        bloodPressureText: String,
        heartRateText: String,
        temperatureText: String,
        weightText: String
    ): VitalsCheck {
        val bp = bloodPressureText.trim()
        val hr = heartRateText.trim()
        val temp = temperatureText.trim()
        val weight = weightText.trim()

        if (bp.isEmpty() && hr.isEmpty() && temp.isEmpty() && weight.isEmpty()) {
            return VitalsCheck(values = null, formError = "Enter at least one reading")
        }

        var systolic: Int? = null
        var diastolic: Int? = null
        var bpError: String? = null
        if (bp.isNotEmpty()) {
            val match = bloodPressure.find(bp)
            if (match == null) {
                bpError = "Enter two numbers, for example 120/80"
            } else {
                val top = match.groupValues[1].toInt()
                val bottom = match.groupValues[2].toInt()
                when {
                    top !in SYSTOLIC_MIN..SYSTOLIC_MAX ->
                        bpError = "The first number must be between $SYSTOLIC_MIN and $SYSTOLIC_MAX"
                    bottom !in DIASTOLIC_MIN..DIASTOLIC_MAX ->
                        bpError = "The second number must be between $DIASTOLIC_MIN and $DIASTOLIC_MAX"
                    bottom >= top ->
                        bpError = "The first number must be higher than the second"
                    else -> {
                        systolic = top
                        diastolic = bottom
                    }
                }
            }
        }

        var heartRate: Int? = null
        var hrError: String? = null
        if (hr.isNotEmpty()) {
            val value = if (wholeNumber.matches(hr)) hr.toInt() else null
            if (value == null || value !in HEART_RATE_MIN..HEART_RATE_MAX) {
                hrError = "Enter a whole number between $HEART_RATE_MIN and $HEART_RATE_MAX"
            } else {
                heartRate = value
            }
        }

        var temperature: Double? = null
        var tempError: String? = null
        if (temp.isNotEmpty()) {
            val value = toDecimal(temp)
            if (value == null || value < TEMPERATURE_MIN || value > TEMPERATURE_MAX) {
                tempError = "Enter a number between ${trim(TEMPERATURE_MIN)} and ${trim(TEMPERATURE_MAX)}"
            } else {
                temperature = value
            }
        }

        var weightKg: Double? = null
        var weightError: String? = null
        if (weight.isNotEmpty()) {
            val value = toDecimal(weight)
            if (value == null || value < WEIGHT_MIN || value > WEIGHT_MAX) {
                weightError = "Enter a number between ${trim(WEIGHT_MIN)} and ${trim(WEIGHT_MAX)}"
            } else {
                weightKg = value
            }
        }

        if (bpError != null || hrError != null || tempError != null || weightError != null) {
            return VitalsCheck(
                values = null,
                bloodPressureError = bpError,
                heartRateError = hrError,
                temperatureError = tempError,
                weightError = weightError
            )
        }
        return VitalsCheck(VitalsValues(systolic, diastolic, heartRate, temperature, weightKg))
    }

    /**
     * Reads what the old app stored as free text, such as "120/80" or "72 bpm".
     * Anything that is not a plausible reading is left out.
     */
    fun parseLegacy(bloodPressureText: String, heartRateText: String, temperatureText: String, weightText: String): VitalsValues {
        var systolic: Int? = null
        var diastolic: Int? = null
        Regex("(\\d{2,3})\\s*/\\s*(\\d{2,3})").find(bloodPressureText)?.let { match ->
            val top = match.groupValues[1].toInt()
            val bottom = match.groupValues[2].toInt()
            if (top in SYSTOLIC_MIN..SYSTOLIC_MAX && bottom in DIASTOLIC_MIN..DIASTOLIC_MAX && bottom < top) {
                systolic = top
                diastolic = bottom
            }
        }
        val heartRate = Regex("\\d{2,3}").find(heartRateText)?.value?.toInt()
            ?.takeIf { it in HEART_RATE_MIN..HEART_RATE_MAX }
        val temperature = firstDecimal(temperatureText)?.takeIf { it in TEMPERATURE_MIN..TEMPERATURE_MAX }
        val weight = firstDecimal(weightText)?.takeIf { it in WEIGHT_MIN..WEIGHT_MAX }
        return VitalsValues(systolic, diastolic, heartRate, temperature, weight)
    }

    /** For example "120/80 mmHg, 72 bpm, 36.6 °C, 70 kg". Empty values are left out. */
    fun describe(systolic: Int?, diastolic: Int?, heartRate: Int?, temperatureC: Double?, weightKg: Double?): String {
        val parts = mutableListOf<String>()
        if (systolic != null && diastolic != null) parts.add("$systolic/$diastolic $UNIT_BLOOD_PRESSURE")
        if (heartRate != null) parts.add("$heartRate $UNIT_HEART_RATE")
        if (temperatureC != null) parts.add("${trim(temperatureC)} $UNIT_TEMPERATURE")
        if (weightKg != null) parts.add("${trim(weightKg)} $UNIT_WEIGHT")
        return parts.joinToString(", ")
    }

    /** 70.0 becomes "70", 36.6 stays "36.6". */
    fun trim(value: Double): String {
        val rounded = Math.round(value * 100.0) / 100.0
        return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else rounded.toString()
    }

    private fun toDecimal(text: String): Double? =
        if (decimalNumber.matches(text)) text.replace(',', '.').toDoubleOrNull() else null

    private fun firstDecimal(text: String): Double? =
        Regex("\\d{1,3}([.,]\\d{1,2})?").find(text)?.value?.replace(',', '.')?.toDoubleOrNull()
}

/** Something the person still has to do today. */
data class PendingItem(val title: String, val timeText: String, val kind: String)

/** What the "Next Reminder" card shows. */
data class NextReminder(val title: String, val detail: String, val badge: String)

object ReminderCalculator {
    const val KIND_MEDICATION = "Medication"
    const val KIND_SCHEDULE = "Schedule"

    /**
     * Picks what to show from today's unfinished items.
     * Something already past its time comes first, because it still needs doing.
     * Otherwise the next item to come is shown with the time left until it.
     */
    fun next(pending: List<PendingItem>, nowMinutes: Int, hasAnyItems: Boolean): NextReminder {
        val timed = pending.mapNotNull { item ->
            CareTimes.minutesOfDay(item.timeText)?.let { minutes -> minutes to item }
        }.sortedBy { it.first }

        val overdue = timed.filter { it.first < nowMinutes }
        if (overdue.isNotEmpty()) {
            val (minutes, item) = overdue.first()
            val others = overdue.size - 1
            val more = if (others > 0) " ($others more waiting)" else ""
            return NextReminder(
                title = item.title,
                detail = "${item.kind}, was due at ${clock(minutes)}$more",
                badge = "Due"
            )
        }

        val upcoming = timed.firstOrNull()
        if (upcoming != null) {
            val (minutes, item) = upcoming
            return NextReminder(
                title = item.title,
                detail = "${item.kind} at ${clock(minutes)}",
                badge = countdown(minutes - nowMinutes)
            )
        }

        return if (hasAnyItems) {
            NextReminder("Nothing else is due today", "Everything with a time has been done", "Done")
        } else {
            NextReminder("No reminders yet", "Add your medicines or schedule events to see them here", "-")
        }
    }

    /** "Now", "in 45 min", "in 2 h" or "in 2 h 15 min". */
    fun countdown(minutes: Int): String = when {
        minutes <= 0 -> "Now"
        minutes < 60 -> "in $minutes min"
        minutes % 60 == 0 -> "in ${minutes / 60} h"
        else -> "in ${minutes / 60} h ${minutes % 60} min"
    }

    private fun clock(minutes: Int): String = CareTimes.format(minutes / 60, minutes % 60)
}

enum class RemovalPlan { DELETE_COMPLETELY, STOP_FROM_TODAY }

object CareRules {
    const val HYDRATION_GOAL = 8

    /**
     * A repeating item that started before today keeps its earlier days: it only stops from today on.
     * Anything else is deleted completely.
     */
    fun removalPlan(repeats: Boolean, startDate: String, today: String): RemovalPlan =
        if (repeats && startDate < today) RemovalPlan.STOP_FROM_TODAY else RemovalPlan.DELETE_COMPLETELY

    /** Keeps digits and a leading plus sign, which is what the dialler and SMS apps expect. */
    fun cleanPhone(number: String): String = number.filter { it.isDigit() || it == '+' }

    fun isPlausiblePhone(number: String): Boolean = number.count { it.isDigit() } >= 7
}
