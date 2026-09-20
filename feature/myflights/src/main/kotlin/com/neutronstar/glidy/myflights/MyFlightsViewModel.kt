package com.neutronstar.glidy.myflights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neutronstar.glidy.flightarchive.ArchivedFlight
import com.neutronstar.glidy.flightarchive.CompletedFlightGateway
import com.neutronstar.glidy.flightarchive.FlightArchiveRepository
import com.neutronstar.glidy.flightarchive.FlightId
import com.neutronstar.glidy.flightarchive.FlightShareGateway
import com.neutronstar.glidy.flightarchive.IgcParseError
import com.neutronstar.glidy.flightarchive.ImportIgcResult
import com.neutronstar.glidy.flightarchive.ReconciliationResult
import com.neutronstar.glidy.flightarchive.RemoveFlightResult
import com.neutronstar.glidy.flightarchive.PendingFlightRecovery
import com.neutronstar.glidy.flightarchive.ShareFlightResult
import java.io.InputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MyFlightsUiState {
    data object Loading : MyFlightsUiState

    data class Ready(
        val flights: List<ArchivedFlight>,
        val selectedFlightId: FlightId? = null,
        val pendingDeleteFlightId: FlightId? = null,
        val notice: String? = null,
    ) : MyFlightsUiState

    data class Error(val message: String) : MyFlightsUiState
}

internal fun ReconciliationResult.issueNotice(): String? = when {
    missing > 0 && invalid > 0 -> "$missing fichier(s) manquant(s) et $invalid fichier(s) invalide(s)."
    missing > 0 -> "$missing fichier(s) IGC manquant(s)."
    invalid > 0 -> "$invalid fichier(s) IGC invalide(s)."
    else -> null
}

internal fun PendingFlightRecovery.issueNotice(): String? = when {
    imported > 0 && rejected > 0 -> "$imported vol(s) Pilotage récupéré(s), $rejected fichier(s) rejeté(s)."
    imported > 0 -> "$imported vol(s) terminé(s) récupéré(s) depuis Pilotage."
    rejected > 0 -> "$rejected fichier(s) Pilotage rejeté(s)."
    else -> null
}

internal fun ImportIgcResult.userMessage(): String = when (this) {
    is ImportIgcResult.Imported -> "Vol importé et archivé sur cet appareil."
    is ImportIgcResult.Duplicate -> "Ce vol est déjà présent dans le carnet."
    is ImportIgcResult.Invalid -> when (error) {
        IgcParseError.EmptyFile -> "Le fichier IGC est vide."
        IgcParseError.MissingDateHeader -> "Le fichier IGC ne contient aucune date."
        is IgcParseError.InvalidDateHeader -> "La date du fichier IGC est invalide."
        IgcParseError.NoValidFix -> "Le fichier IGC ne contient aucun point GPS valide."
    }
    ImportIgcResult.TooLarge -> "Le fichier dépasse la limite de 64 Mo."
    is ImportIgcResult.Failed -> "Import impossible : $reason"
}

class MyFlightsViewModel(
    private val repository: FlightArchiveRepository,
    private val shareGateway: FlightShareGateway,
    private val completedFlightGateway: CompletedFlightGateway? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow<MyFlightsUiState>(MyFlightsUiState.Loading)
    val state: StateFlow<MyFlightsUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        mutableState.value = MyFlightsUiState.Loading
        viewModelScope.launch {
            mutableState.value = try {
                val recovered = completedFlightGateway?.recoverPending()
                val reconciliation = repository.reconcile()
                MyFlightsUiState.Ready(
                    flights = repository.listFlights().sortedForDisplay(),
                    notice = listOfNotNull(recovered?.issueNotice(), reconciliation.issueNotice())
                        .joinToString(" ")
                        .takeIf { it.isNotBlank() },
                )
            } catch (_: Exception) {
                MyFlightsUiState.Error("Le carnet local ne peut pas être chargé.")
            }
        }
    }

    fun importIgc(fileName: String, source: InputStream?) {
        if (source == null) {
            showNotice("Le fichier ne peut pas être ouvert.")
            return
        }
        viewModelScope.launch {
            val result = runCatching { source.use { repository.importIgc(fileName, it) } }
                .getOrElse { ImportIgcResult.Failed(it.message ?: "erreur locale") }
            val flights = runCatching { repository.listFlights().sortedForDisplay() }
                .getOrElse {
                    mutableState.value = MyFlightsUiState.Error("Le carnet local ne peut pas être actualisé.")
                    return@launch
                }
            mutableState.value = MyFlightsUiState.Ready(
                flights = flights,
                notice = result.userMessage(),
            )
        }
    }

    fun selectFlight(id: FlightId) {
        val current = mutableState.value as? MyFlightsUiState.Ready ?: return
        if (current.flights.any { it.id == id }) {
            mutableState.value = current.copy(selectedFlightId = id)
        }
    }

    fun closeDetail() {
        val current = mutableState.value as? MyFlightsUiState.Ready ?: return
        mutableState.value = current.copy(selectedFlightId = null)
    }

    fun shareFlight(id: FlightId) {
        val current = mutableState.value as? MyFlightsUiState.Ready ?: return
        if (current.flights.none { it.id == id }) return
        viewModelScope.launch {
            val result = runCatching { shareGateway.share(id) }
                .getOrElse { ShareFlightResult.Failed(it.message ?: "erreur locale") }
            val latest = mutableState.value as? MyFlightsUiState.Ready ?: return@launch
            mutableState.value = latest.copy(notice = result.userMessage())
        }
    }

    fun requestDelete(id: FlightId) {
        val current = mutableState.value as? MyFlightsUiState.Ready ?: return
        if (current.flights.any { it.id == id }) {
            mutableState.value = current.copy(pendingDeleteFlightId = id)
        }
    }

    fun cancelDelete() {
        val current = mutableState.value as? MyFlightsUiState.Ready ?: return
        mutableState.value = current.copy(pendingDeleteFlightId = null)
    }

    fun confirmDelete() {
        val current = mutableState.value as? MyFlightsUiState.Ready ?: return
        val id = current.pendingDeleteFlightId ?: return
        viewModelScope.launch {
            val result = runCatching { repository.removeLocalFlight(id) }
                .getOrElse { RemoveFlightResult.Failed(it.message ?: "erreur locale") }
            val latest = mutableState.value as? MyFlightsUiState.Ready ?: return@launch
            mutableState.value = when (result) {
                RemoveFlightResult.Removed -> latest.copy(
                    flights = latest.flights.filterNot { it.id == id },
                    selectedFlightId = null,
                    pendingDeleteFlightId = null,
                    notice = "Le vol et sa copie locale ont été supprimés.",
                )
                RemoveFlightResult.NotFound -> latest.copy(
                    flights = latest.flights.filterNot { it.id == id },
                    selectedFlightId = null,
                    pendingDeleteFlightId = null,
                    notice = "Ce vol n'était plus présent dans le carnet.",
                )
                is RemoveFlightResult.Failed -> latest.copy(
                    pendingDeleteFlightId = null,
                    notice = "Suppression impossible : ${result.reason}",
                )
            }
        }
    }

    private fun showNotice(message: String) {
        val current = mutableState.value as? MyFlightsUiState.Ready ?: return
        mutableState.value = current.copy(notice = message)
    }

    private fun List<ArchivedFlight>.sortedForDisplay(): List<ArchivedFlight> =
        sortedWith(
            compareByDescending<ArchivedFlight> { it.summary?.startedAt?.toEpochMilli() ?: Long.MIN_VALUE }
                .thenBy { it.file.fileName.lowercase() },
        )
}

class MyFlightsViewModelFactory(
    private val repository: FlightArchiveRepository,
    private val shareGateway: FlightShareGateway,
    private val completedFlightGateway: CompletedFlightGateway? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MyFlightsViewModel::class.java))
        return MyFlightsViewModel(repository, shareGateway, completedFlightGateway) as T
    }
}

internal fun ShareFlightResult.userMessage(): String = when (this) {
    ShareFlightResult.Presented -> "Le menu de partage est ouvert."
    ShareFlightResult.NotFound -> "Ce vol n'est plus présent dans le carnet."
    ShareFlightResult.FileUnavailable -> "Le fichier IGC n'est plus disponible sur cet appareil."
    is ShareFlightResult.Failed -> "Partage impossible : $reason"
}
