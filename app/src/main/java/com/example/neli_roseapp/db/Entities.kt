package com.example.neli_roseapp.db

import androidx.room.Entity
import androidx.room.PrimaryKey

// Room is the one place where a person's records are kept.
// Dates are ISO text (yyyy-MM-dd) and times are HH:mm, see CareDates and CareTimes.

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val userId: String,
    val fullName: String,
    val phone: String,
    val email: String
)

/** One saved set of vitals. Every save adds a row, so earlier readings are kept. */
@Entity(tableName = "vital_readings")
data class VitalReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val date: String,
    val recordedAt: Long,
    val systolic: Int?,
    val diastolic: Int?,
    val heartRate: Int?,
    val temperatureC: Double?,
    val weightKg: Double?
)

/** Glasses of water for one person on one day. */
@Entity(tableName = "hydration_days", primaryKeys = ["userId", "date"])
data class HydrationDayEntity(
    val userId: String,
    val date: String,
    val glasses: Int
)

/**
 * A medicine the person takes every day from startDate.
 * endDate is the last day it applied, or null while it is still in use.
 */
@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val name: String,
    val time: String,
    val startDate: String,
    val endDate: String?
)

/** A row exists when the medicine was taken on that day. */
@Entity(tableName = "medication_logs", primaryKeys = ["medicationId", "date"])
data class MedicationLogEntity(
    val medicationId: Long,
    val userId: String,
    val date: String,
    val takenAt: Long
)

/**
 * A schedule event. A one-off event happens on startDate only.
 * A repeating event happens every day from startDate until endDate (null = still running).
 */
@Entity(tableName = "schedule_events")
data class ScheduleEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val title: String,
    val note: String,
    val time: String,
    val startDate: String,
    val repeatDaily: Boolean,
    val endDate: String?
)

/** A row exists when the event was ticked off on that day. */
@Entity(tableName = "schedule_completions", primaryKeys = ["eventId", "date"])
data class ScheduleCompletionEntity(
    val eventId: Long,
    val userId: String,
    val date: String,
    val completedAt: Long
)

@Entity(tableName = "symptom_logs")
data class SymptomLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val symptom: String,
    val notes: String,
    val date: String,
    val loggedAt: Long
)

@Entity(tableName = "activities")
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val time: String,
    val name: String,
    val place: String,
    val joined: Boolean
)

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val name: String,
    val phone: String,
    val isCaregiver: Boolean
)
