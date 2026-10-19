package com.example.neli_roseapp.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM user_profiles WHERE userId = :userId LIMIT 1")
    suspend fun getProfile(userId: String): UserProfileEntity?
}

@Dao
interface VitalsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVitals(vitals: VitalsEntity)

    @Query("SELECT * FROM vitals WHERE userId = :userId LIMIT 1")
    suspend fun getVitals(userId: String): VitalsEntity?
}

@Dao
interface MedicationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeds(meds: List<MedicationEntity>)

    @Query("DELETE FROM medications WHERE userId = :userId")
    suspend fun deleteMedsForUser(userId: String)

    @Query("SELECT * FROM medications WHERE userId = :userId")
    suspend fun getMeds(userId: String): List<MedicationEntity>
}

@Dao
interface ScheduleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<ScheduleEventEntity>)

    @Query("DELETE FROM schedule_events WHERE userId = :userId")
    suspend fun deleteEventsForUser(userId: String)

    @Query("SELECT * FROM schedule_events WHERE userId = :userId ORDER BY time ASC")
    suspend fun getEvents(userId: String): List<ScheduleEventEntity>
}

@Dao
interface ActivityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivities(activities: List<ActivityEntity>)

    @Query("DELETE FROM activities WHERE userId = :userId")
    suspend fun deleteActivitiesForUser(userId: String)

    @Query("SELECT * FROM activities WHERE userId = :userId")
    suspend fun getActivities(userId: String): List<ActivityEntity>
}

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<ContactEntity>)

    @Query("DELETE FROM contacts WHERE userId = :userId")
    suspend fun deleteContactsForUser(userId: String)

    @Query("SELECT * FROM contacts WHERE userId = :userId")
    suspend fun getContacts(userId: String): List<ContactEntity>
}
