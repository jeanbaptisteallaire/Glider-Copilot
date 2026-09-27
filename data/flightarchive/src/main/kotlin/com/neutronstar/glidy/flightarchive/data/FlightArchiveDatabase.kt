package com.neutronstar.glidy.flightarchive.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    /** S16 (schéma v2) : PRIVATE / PUBLIC — privé par défaut, y compris pour les vols existants. */
    @ColumnInfo(defaultValue = "PRIVATE") val visibility: String = "PRIVATE",
    val publishedAtEpochMillis: Long? = null,
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

    @Query("UPDATE flights SET syncState = :syncState, remoteId = :remoteId, updatedAtEpochMillis = :now WHERE id = :id")
    suspend fun updateSync(id: String, syncState: String, remoteId: String?, now: Long): Int

    @Query("UPDATE flights SET visibility = :visibility, publishedAtEpochMillis = :publishedAt, updatedAtEpochMillis = :now WHERE id = :id")
    suspend fun updateVisibility(id: String, visibility: String, publishedAt: Long?, now: Long): Int

    @Query("DELETE FROM flights WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM flights")
    suspend fun deleteAll()
}

@Database(entities = [FlightEntity::class], version = 2, exportSchema = true)
abstract class FlightArchiveDatabase : RoomDatabase() {
    abstract fun flightDao(): FlightDao
}

/** v1 → v2 (S16) : visibilité des vols. Non destructive : les vols existants restent privés. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE flights ADD COLUMN visibility TEXT NOT NULL DEFAULT 'PRIVATE'")
        db.execSQL("ALTER TABLE flights ADD COLUMN publishedAtEpochMillis INTEGER")
    }
}
