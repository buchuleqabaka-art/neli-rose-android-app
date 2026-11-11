package com.example.neli_roseapp.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM user_profiles WHERE userId = :userId LIMIT 1")
    suspend fun getProfile(userId: String): UserProfileEntity?
}

@Dao
interface VitalsDao {
    @Insert
    suspend fun insertReading(reading: VitalReadingEntity): Long

    @Query("SELECT * FROM vital_readings WHERE userId = :userId ORDER BY recordedAt DESC, id DESC LIMIT 1")
    suspend fun latestReading(userId: String): VitalReadingEntity?

    @Query("SELECT * FROM vital_readings WHERE userId = :userId AND date = :date ORDER BY recordedAt DESC, id DESC LIMIT 1")
    suspend fun latestReadingOn(userId: String, date: String): VitalReadingEntity?

    @Query("SELECT COUNT(*) FROM vital_readings WHERE userId = :userId")
    suspend fun countReadings(userId: String): Int
}

@Dao
interface HydrationDao {
    @Query("SELECT glasses FROM hydration_days WHERE userId = :userId AND date = :date")
    suspend fun glassesOn(userId: String, date: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(day: HydrationDayEntity)

    @Query("SELECT COUNT(*) FROM hydration_days WHERE userId = :userId")
    suspend fun countDays(userId: String): Int
}

@Dao
interface MedicationDao {
    @Insert
    suspend fun insert(medication: MedicationEntity): Long

    @Update
    suspend fun update(medication: MedicationEntity)

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Medicines that apply on the given day. */
    @Query("SELECT * FROM medications WHERE userId = :userId AND startDate <= :date AND (endDate IS NULL OR endDate >= :date) ORDER BY id")
    suspend fun activeOn(userId: String, date: String): List<MedicationEntity>

    @Query("SELECT COUNT(*) FROM medications WHERE userId = :userId")
    suspend fun countAll(userId: String): Int

    @Query("SELECT medicationId FROM medication_logs WHERE userId = :userId AND date = :date")
    suspend fun takenIdsOn(userId: String, date: String): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: MedicationLogEntity)

    @Query("DELETE FROM medication_logs WHERE medicationId = :medicationId AND date = :date")
    suspend fun deleteLog(medicationId: Long, date: String)

    @Query("DELETE FROM medication_logs WHERE medicationId = :medicationId AND date >= :fromDate")
    suspend fun deleteLogsFrom(medicationId: Long, fromDate: String)

    @Query("DELETE FROM medication_logs WHERE medicationId = :medicationId")
    suspend fun deleteAllLogs(medicationId: Long)
}

@Dao
interface ScheduleDao {
    @Insert
    suspend fun insert(event: ScheduleEventEntity): Long

    @Update
    suspend fun update(event: ScheduleEventEntity)

    @Query("DELETE FROM schedule_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** One-off events on the given day, plus repeating events that are running on it. */
    @Query(
        "SELECT * FROM schedule_events WHERE userId = :userId AND (" +
            "(repeatDaily = 0 AND startDate = :date) OR " +
            "(repeatDaily = 1 AND startDate <= :date AND (endDate IS NULL OR endDate >= :date))" +
            ") ORDER BY time, id"
    )
    suspend fun eventsOn(userId: String, date: String): List<ScheduleEventEntity>

    @Query("SELECT COUNT(*) FROM schedule_events WHERE userId = :userId")
    suspend fun countAll(userId: String): Int

    @Query("SELECT eventId FROM schedule_completions WHERE userId = :userId AND date = :date")
    suspend fun completedIdsOn(userId: String, date: String): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletion(completion: ScheduleCompletionEntity)

    @Query("DELETE FROM schedule_completions WHERE eventId = :eventId AND date = :date")
    suspend fun deleteCompletion(eventId: Long, date: String)

    @Query("DELETE FROM schedule_completions WHERE eventId = :eventId AND date >= :fromDate")
    suspend fun deleteCompletionsFrom(eventId: Long, fromDate: String)

    @Query("DELETE FROM schedule_completions WHERE eventId = :eventId")
    suspend fun deleteAllCompletions(eventId: Long)
}

@Dao
interface SymptomDao {
    @Insert
    suspend fun insert(log: SymptomLogEntity): Long

    @Query("SELECT * FROM symptom_logs WHERE userId = :userId ORDER BY loggedAt DESC, id DESC LIMIT :limit")
    suspend fun recent(userId: String, limit: Int): List<SymptomLogEntity>
}

@Dao
interface ActivityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivities(activities: List<ActivityEntity>)

    @Query("DELETE FROM activities WHERE userId = :userId")
    suspend fun deleteActivitiesForUser(userId: String)

    @Query("SELECT * FROM activities WHERE userId = :userId ORDER BY id")
    suspend fun getActivities(userId: String): List<ActivityEntity>
}

@Dao
interface ContactDao {
    @Insert
    suspend fun insert(contact: ContactEntity): Long

    @Update
    suspend fun update(contact: ContactEntity)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM contacts WHERE userId = :userId ORDER BY id")
    suspend fun forUser(userId: String): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE userId = :userId AND isCaregiver = 1 LIMIT 1")
    suspend fun caregiver(userId: String): ContactEntity?

    /** Exactly one contact per person is the caregiver. */
    @Query("UPDATE contacts SET isCaregiver = CASE WHEN id = :contactId THEN 1 ELSE 0 END WHERE userId = :userId")
    suspend fun setCaregiver(userId: String, contactId: Long)

    @Query("SELECT COUNT(*) FROM contacts WHERE userId = :userId")
    suspend fun countAll(userId: String): Int
}
