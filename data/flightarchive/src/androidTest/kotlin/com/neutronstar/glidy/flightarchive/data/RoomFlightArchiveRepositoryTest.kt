package com.neutronstar.glidy.flightarchive.data

import android.content.Context
import android.content.Intent
import android.provider.OpenableColumns
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neutronstar.glidy.flightarchive.ImportIgcResult
import com.neutronstar.glidy.flightarchive.LocalFileState
import com.neutronstar.glidy.flightarchive.RemoveFlightResult
import com.neutronstar.glidy.flightarchive.ShareFlightResult
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomFlightArchiveRepositoryTest {
    private lateinit var database: FlightArchiveDatabase
    private lateinit var archiveDirectory: File
    private lateinit var repository: RoomFlightArchiveRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FlightArchiveDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        archiveDirectory = File(context.filesDir, "igc-archive/test-${System.nanoTime()}").apply { mkdirs() }
        repository = RoomFlightArchiveRepository(
            dao = database.flightDao(),
            archiveDirectory = archiveDirectory,
            now = { 1_800_000_000_000L },
        )
    }

    @After
    fun tearDown() {
        database.close()
        archiveDirectory.deleteRecursively()
        ApplicationProvider.getApplicationContext<Context>().cacheDir.resolve("igc-share").deleteRecursively()
    }

    @Test
    fun importCopiesIndexesAndDeduplicates() = runBlocking {
        val first = repository.importIgc("mon-vol.igc", testIgc.byteInputStream())
        val imported = first as ImportIgcResult.Imported

        assertEquals("mon-vol.igc", imported.flight.file.fileName)
        assertEquals(3, imported.flight.summary?.validPointCount)
        assertEquals(1, archiveDirectory.listFiles { file -> file.extension == "igc" }?.size)

        val duplicate = repository.importIgc("copie.igc", testIgc.byteInputStream())
        assertTrue(duplicate is ImportIgcResult.Duplicate)
        assertEquals(1, repository.listFlights().size)
    }

    @Test
    fun databaseCanBeRebuiltFromOriginalFiles() = runBlocking {
        val imported = repository.importIgc("a-reconstruire.igc", testIgc.byteInputStream())
            as ImportIgcResult.Imported
        database.flightDao().deleteAll()
        assertTrue(repository.listFlights().isEmpty())

        val reconciliation = repository.reconcile()

        assertEquals(1, reconciliation.discovered)
        val rebuilt = repository.listFlights().single()
        assertEquals(imported.flight.id, rebuilt.id)
        assertEquals(imported.flight.file.sha256, rebuilt.file.sha256)
        assertEquals(3, rebuilt.summary?.validPointCount)
    }

    @Test
    fun twoIdenticalScansDoNotCreateDuplicates() = runBlocking {
        repository.importIgc("stable.igc", testIgc.byteInputStream())

        val first = repository.reconcile()
        val second = repository.reconcile()

        assertEquals(0, first.discovered)
        assertEquals(0, second.discovered)
        assertEquals(0, second.updated)
        assertEquals(1, repository.listFlights().size)
    }

    @Test
    fun missingOriginalIsReportedWithoutCrash() = runBlocking {
        val imported = repository.importIgc("disparu.igc", testIgc.byteInputStream())
            as ImportIgcResult.Imported
        File(archiveDirectory, imported.flight.file.relativePath).delete()

        val reconciliation = repository.reconcile()

        assertEquals(1, reconciliation.missing)
        assertEquals(LocalFileState.MISSING, repository.listFlights().single().localState)
    }

    @Test
    fun invalidFileIsRejectedAndLeavesNoArchive() = runBlocking {
        val result = repository.importIgc("invalide.igc", ByteArrayInputStream("pas un IGC".toByteArray()))

        assertTrue(result is ImportIgcResult.Invalid)
        assertTrue(repository.listFlights().isEmpty())
        assertFalse(archiveDirectory.listFiles().orEmpty().any { it.extension == "igc" })
    }

    @Test
    fun removalDeletesIndexAndPrivateCopy() = runBlocking {
        val imported = repository.importIgc("a-supprimer.igc", testIgc.byteInputStream())
            as ImportIgcResult.Imported

        assertEquals(RemoveFlightResult.Removed, repository.removeLocalFlight(imported.flight.id))
        assertTrue(repository.listFlights().isEmpty())
        assertFalse(File(archiveDirectory, imported.flight.file.relativePath).exists())
    }

    @Test
    fun shareGatewayPresentsReadOnlyIgcIntent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val imported = repository.importIgc("a-partager.igc", testIgc.byteInputStream())
            as ImportIgcResult.Imported
        var presented: Intent? = null
        val gateway = AndroidFlightShareGateway(
            context = context,
            repository = repository,
            archiveDirectory = archiveDirectory,
            presentIntent = { presented = it },
        )

        assertEquals(ShareFlightResult.Presented, gateway.share(imported.flight.id))
        val chooser = requireNotNull(presented)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val sendIntent = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, sendIntent.action)
        assertEquals("application/vnd.fai.igc", sendIntent.type)
        assertTrue(sendIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        @Suppress("DEPRECATION")
        val streamUri = sendIntent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
        assertTrue(streamUri.toString()
            .startsWith("content://${context.packageName}.glidy.flightfiles/"))
        val sharedName = context.contentResolver.query(
            requireNotNull(streamUri),
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )!!.use { cursor ->
            cursor.moveToFirst()
            cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        }
        assertEquals("a-partager.igc", sharedName)
    }

    @Test
    fun missingFileCannotBeShared() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val imported = repository.importIgc("partage-manquant.igc", testIgc.byteInputStream())
            as ImportIgcResult.Imported
        File(archiveDirectory, imported.flight.file.relativePath).delete()
        repository.reconcile()
        var presented = false
        val gateway = AndroidFlightShareGateway(
            context = context,
            repository = repository,
            archiveDirectory = archiveDirectory,
            presentIntent = { presented = true },
        )

        assertEquals(ShareFlightResult.FileUnavailable, gateway.share(imported.flight.id))
        assertFalse(presented)
    }

    private companion object {
        val testIgc = """
            AXXXGLYGLIDY
            HFDTEDATE:190926,01
            HFPLTPILOTINCHARGE:PILOTE TEST
            HFGTYGLIDERTYPE:ASW 28
            HFGIDGLIDERID:TEST
            B0912004348085N00346845EA0016200190
            B0912014348094N00346838EA0016500195
            B0912024348104N00346830EA0016800201
        """.trimIndent()
    }
}
