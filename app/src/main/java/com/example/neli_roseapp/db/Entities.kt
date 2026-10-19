package com.example.neli_roseapp.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val userId: String,
    val fullName: String,
    val phone: String,
    val email: String
)

@Entity(tableName = "vitals")
data class VitalsEntity(
    @PrimaryKey val userId: String,
    val bp: String,
    val hr: String,
    val temp: String,
    val weight: String,
    val hydrationGlasses: Int
)

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String,
    val name: String,
    val time: String,
    val taken: Boolean
)

@Entity(tableName = "schedule_events")
data class ScheduleEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String,
    val time: String,
    val title: String,
    val note: String,
    val done: Boolean
)

@Entity(tableName = "activities")
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String,
    val time: String,
    val name: String,
    val place: String,
    val joined: Boolean
)

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String,
    val name: String,
    val phone: String
)
