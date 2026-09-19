package com.neutronstar.glidy.flightarchive.data

import android.content.Context
import androidx.room.Room
import com.neutronstar.glidy.flightarchive.FlightArchiveRepository
import com.neutronstar.glidy.flightarchive.FlightShareGateway
import java.io.File

object LocalArchiveModule {
    fun create(context: Context): FlightArchiveRepository {
        val applicationContext = context.applicationContext
        val database = Room.databaseBuilder(
            applicationContext,
            FlightArchiveDatabase::class.java,
            DATABASE_NAME,
        ).build()
        return RoomFlightArchiveRepository(
            dao = database.flightDao(),
            archiveDirectory = File(applicationContext.filesDir, ARCHIVE_DIRECTORY),
        )
    }

    fun createShareGateway(
        context: Context,
        repository: FlightArchiveRepository,
    ): FlightShareGateway {
        val applicationContext = context.applicationContext
        return AndroidFlightShareGateway(
            context = applicationContext,
            repository = repository,
            archiveDirectory = File(applicationContext.filesDir, ARCHIVE_DIRECTORY),
        )
    }

    private const val DATABASE_NAME = "glidy-flight-archive.db"
    private const val ARCHIVE_DIRECTORY = "igc-archive"
}
