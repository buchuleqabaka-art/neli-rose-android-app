package com.example.neli_roseapp

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

private const val TAG = "NeliRose"

/**
 * Runs screen work that reads or saves records.
 * If it fails, the person is told and the cause goes to Logcat, instead of the app closing.
 */
fun Fragment.launchUi(block: suspend CoroutineScope.() -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Screen action failed", e)
            context?.let { Toast.makeText(it, "Something went wrong. Please try again.", Toast.LENGTH_LONG).show() }
        }
    }
}

fun AppCompatActivity.launchUi(block: suspend CoroutineScope.() -> Unit) {
    lifecycleScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Screen action failed", e)
            Toast.makeText(this@launchUi, "Something went wrong. Please try again.", Toast.LENGTH_LONG).show()
        }
    }
}

/** Asks before something is removed. */
fun Context.confirm(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit) {
    AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(confirmLabel) { _, _ -> onConfirm() }
        .setNegativeButton("Cancel", null)
        .show()
}

/** Opens a 24-hour time picker and returns the choice as HH:mm. */
fun Context.pickTime(current: String, onPicked: (String) -> Unit) {
    val minutes = CareTimes.minutesOfDay(current) ?: (9 * 60)
    TimePickerDialog(
        this,
        { _, hour, minute -> onPicked(CareTimes.format(hour, minute)) },
        minutes / 60,
        minutes % 60,
        true
    ).show()
}

/** Opens a date picker and returns the choice as yyyy-MM-dd. */
fun Context.pickDate(current: String, onPicked: (String) -> Unit) {
    val date = CareDates.parse(current) ?: LocalDate.now()
    DatePickerDialog(
        this,
        { _, year, month, day -> onPicked(CareDates.of(year, month + 1, day)) },
        date.year,
        date.monthValue - 1,
        date.dayOfMonth
    ).show()
}

/** For example "11 Nov 2026, 14:05". */
fun formatDateTime(millis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))

/** For example "14:05". */
fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
