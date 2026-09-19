package com.neutronstar.glidy.flightarchive.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.neutronstar.glidy.flightarchive.FlightArchiveRepository
import com.neutronstar.glidy.flightarchive.FlightId
import com.neutronstar.glidy.flightarchive.FlightShareGateway
import com.neutronstar.glidy.flightarchive.LocalFileState
import com.neutronstar.glidy.flightarchive.ShareFlightResult
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidFlightShareGateway internal constructor(
    private val context: Context,
    private val repository: FlightArchiveRepository,
    private val archiveDirectory: File,
    private val shareCacheDirectory: File = File(context.cacheDir, "igc-share"),
    private val authority: String = "${context.packageName}.glidy.flightfiles",
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val presentIntent: (Intent) -> Unit = context::startActivity,
) : FlightShareGateway {
    override suspend fun share(id: FlightId): ShareFlightResult = withContext(ioDispatcher) {
        val flight = repository.findFlight(id) ?: return@withContext ShareFlightResult.NotFound
        if (flight.localState == LocalFileState.MISSING) {
            return@withContext ShareFlightResult.FileUnavailable
        }

        val archiveRoot = archiveDirectory.canonicalFile
        val file = File(archiveRoot, flight.file.relativePath).canonicalFile
        if (!file.path.startsWith(archiveRoot.path + File.separator) || !file.isFile) {
            return@withContext ShareFlightResult.FileUnavailable
        }

        runCatching {
            val flightShareDirectory = File(shareCacheDirectory, id.value).apply {
                deleteRecursively()
                mkdirs()
            }
            val sharedFile = File(flightShareDirectory, flight.file.fileName)
            file.copyTo(sharedFile, overwrite = true)
            val uri = FileProvider.getUriForFile(context, authority, sharedFile)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = IGC_MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, flight.file.fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "Partager le fichier IGC").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            withContext(Dispatchers.Main) { presentIntent(chooser) }
            ShareFlightResult.Presented
        }.getOrElse { error ->
            ShareFlightResult.Failed(error.message ?: error::class.java.simpleName)
        }
    }

    private companion object {
        const val IGC_MIME_TYPE = "application/vnd.fai.igc"
    }
}
