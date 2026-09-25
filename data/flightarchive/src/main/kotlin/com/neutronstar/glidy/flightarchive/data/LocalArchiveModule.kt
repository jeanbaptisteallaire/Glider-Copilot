package com.neutronstar.glidy.flightarchive.data

import android.content.Context
import androidx.room.Room
import com.neutronstar.glidy.flightarchive.FlightArchiveRepository
import com.neutronstar.glidy.flightarchive.FlightShareGateway
import com.neutronstar.glidy.flightarchive.CompletedFlightGateway
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

    /**
     * [authority] : autorité du FileProvider de l'application hôte (GLIDY n'en déclare qu'un seul, S10) ;
     * ses chemins doivent couvrir `files/igc-archive/` et `cache/igc-share/`.
     */
    fun createShareGateway(
        context: Context,
        repository: FlightArchiveRepository,
        authority: String = "${context.applicationContext.packageName}.glidy.flightfiles",
    ): FlightShareGateway {
        val applicationContext = context.applicationContext
        return AndroidFlightShareGateway(
            context = applicationContext,
            repository = repository,
            archiveDirectory = File(applicationContext.filesDir, ARCHIVE_DIRECTORY),
            authority = authority,
        )
    }

    fun createCompletedFlightGateway(
        context: Context,
        repository: FlightArchiveRepository,
    ): CompletedFlightGateway {
        val applicationContext = context.applicationContext
        return AtomicCompletedFlightGateway(
            repository = repository,
            inboxDirectory = File(applicationContext.filesDir, COMPLETED_INBOX_DIRECTORY),
        )
    }

    private const val DATABASE_NAME = "glidy-flight-archive.db"
    private const val ARCHIVE_DIRECTORY = "igc-archive"
    private const val COMPLETED_INBOX_DIRECTORY = "igc-completed-inbox"
}
