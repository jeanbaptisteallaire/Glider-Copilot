package com.neutronstar.glidy.myflights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neutronstar.glidy.flightarchive.ArchivedFlight
import com.neutronstar.glidy.flightarchive.FlightArchiveRepository
import com.neutronstar.glidy.flightarchive.FlightId
import com.neutronstar.glidy.flightarchive.IgcParseError
import com.neutronstar.glidy.flightarchive.ImportIgcResult
import com.neutronstar.glidy.flightarchive.ReconciliationResult
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
                val reconciliation = repository.reconcile()
                MyFlightsUiState.Ready(
                    flights = repository.listFlights().sortedForDisplay(),
                    notice = reconciliation.issueNotice(),
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
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MyFlightsViewModel::class.java))
        return MyFlightsViewModel(repository) as T
    }
}
