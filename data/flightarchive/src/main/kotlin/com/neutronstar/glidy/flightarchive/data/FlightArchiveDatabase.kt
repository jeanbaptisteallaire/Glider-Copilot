package com.neutronstar.glidy.flightarchive.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

@Entity(
    tableName = "flights",
    indices = [
        Index(value = ["sha256"], unique = true),
        Index(value = ["relativePath"], unique = true),
        Index(value = ["startedAtEpochMillis"]),
    ],
)
data class FlightEntity(
    @PrimaryKey val id: String,
    val relativePath: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val startedAtEpochMillis: Long?,
    val endedAtEpochMillis: Long?,
    val pointCount: Int,
    val validPointCount: Int,
    val distanceMeters: Long?,
    val minimumAltitudeMeters: Int?,
    val maximumAltitudeMeters: Int?,
    val positiveGainMeters: Int?,
    val boundsSouth: Double?,
    val boundsWest: Double?,
    val boundsNorth: Double?,
    val boundsEast: Double?,
    val pilot: String?,
    val gliderType: String?,
    val gliderId: String?,
    val localState: String,
    val syncState: String,
    val remoteId: String?,
    val previewTrack: String,
    val altitudeProfile: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Dao
interface FlightDao {
    @Query("SELECT * FROM flights ORDER BY startedAtEpochMillis DESC, createdAtEpochMillis DESC")
    suspend fun listAll(): List<FlightEntity>

    @Query("SELECT * FROM flights WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): FlightEntity?

    @Query("SELECT * FROM flights WHERE sha256 = :sha256 LIMIT 1")
    suspend fun findBySha256(sha256: String): FlightEntity?

    @Upsert
    suspend fun upsert(entity: FlightEntity)

    @Query("DELETE FROM flights WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM flights")
    suspend fun deleteAll()
}

@Database(entities = [FlightEntity::class], version = 1, exportSchema = true)
abstract class FlightArchiveDatabase : RoomDatabase() {
    abstract fun flightDao(): FlightDao
}
