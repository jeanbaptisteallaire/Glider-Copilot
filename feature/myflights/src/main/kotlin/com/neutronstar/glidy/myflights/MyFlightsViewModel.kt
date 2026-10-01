package com.neutronstar.glidy.myflights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neutronstar.glidy.flightarchive.ArchivedFlight
import com.neutronstar.glidy.flightarchive.CompletedFlightGateway
import com.neutronstar.glidy.flightarchive.FlightArchiveRepository
import com.neutronstar.glidy.flightarchive.FlightId
import com.neutronstar.glidy.flightarchive.FlightShareGateway
import com.neutronstar.glidy.flightarchive.FlightVisibility
import com.neutronstar.glidy.social.PilotProfile
import com.neutronstar.glidy.social.ProfileStore
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
    imported > 0 -> "$imported vol(s) récupéré(s) depuis Pilotage."
    rejected > 0 -> "$rejected fichier(s) Pilotage rejeté(s)."
    else -> null
}

internal fun ImportIgcResult.userMessage(): String = when (this) {
    is ImportIgcResult.Imported -> "Vol importé sur ce téléphone."
    is ImportIgcResult.Duplicate -> "Vol déjà dans le carnet."
    is ImportIgcResult.Invalid -> when (error) {
        IgcParseError.EmptyFile -> "Le fichier IGC est vide."
        IgcParseError.MissingDateHeader -> "Fichier IGC sans date."
        is IgcParseError.InvalidDateHeader -> "Date du fichier IGC invalide."
        IgcParseError.NoValidFix -> "Aucun point GPS valide dans ce fichier IGC."
    }
    ImportIgcResult.TooLarge -> "Fichier trop lourd (64 Mo max)."
    is ImportIgcResult.Failed -> "Import impossible : $reason"
}

class MyFlightsViewModel(
    private val repository: FlightArchiveRepository,
    private val shareGateway: FlightShareGateway,
    private val completedFlightGateway: CompletedFlightGateway? = null,
    /** S16 — profil pilote ; null = profil en mémoire seulement (tests, app autonome). */
    private val profileStore: ProfileStore? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow<MyFlightsUiState>(MyFlightsUiState.Loading)
    val state: StateFlow<MyFlightsUiState> = mutableState.asStateFlow()

    private val mutableProfile = MutableStateFlow(PilotProfile())
    val profile: StateFlow<PilotProfile> = mutableProfile.asStateFlow()

    init {
        refresh()
        viewModelScope.launch { profileStore?.let { store -> runCatching { store.load() }.getOrNull()?.let { mutableProfile.value = it } } }
    }

    /** S16 — enregistre le profil (validé par l'écran). */
    fun saveProfile(profile: PilotProfile) {
        mutableProfile.value = profile
        viewModelScope.launch {
            val ok = runCatching { profileStore?.save(profile) }.isSuccess
            showNotice(if (ok) "Profil enregistré sur ce téléphone." else "Le profil n'a pas pu être enregistré.")
        }
    }

    /** S16 — efface le profil de ce téléphone. Les vols ne sont pas touchés. */
    fun deleteProfile() {
        mutableProfile.value = PilotProfile()
        viewModelScope.launch {
            runCatching { profileStore?.clear() }
            showNotice("Profil supprimé de ce téléphone. Vos vols sont conservés.")
        }
    }

    /**
     * S16 — icône de partage d'une tuile : publie ou dépublie le vol sur le fil GLIDY. Le vol d'exemple ne se
     * publie pas. Sans compte en ligne, le choix est gardé et appliqué dès que la sauvegarde sera active (S18).
     */
    fun toggleShare(id: FlightId) {
        val current = mutableState.value as? MyFlightsUiState.Ready ?: return
        val flight = current.flights.firstOrNull { it.id == id } ?: return
        if (flight.file.fileName.startsWith("exemple-", ignoreCase = true)) {
            showNotice("Le vol d'exemple ne peut pas être partagé.")
            return
        }
        val target = if (flight.isPublic) FlightVisibility.PRIVATE else FlightVisibility.PUBLIC
        viewModelScope.launch {
            val ok = runCatching { repository.setVisibility(id, target) }.getOrDefault(false)
            val latest = mutableState.value as? MyFlightsUiState.Ready ?: return@launch
            if (!ok) {
                mutableState.value = latest.copy(notice = "Le partage n'a pas pu être modifié.")
                return@launch
            }
            val now = java.time.Instant.now()
            mutableState.value = latest.copy(
                flights = latest.flights.map {
                    if (it.id == id) it.copy(visibility = target, publishedAt = if (target == FlightVisibility.PUBLIC) now else null) else it
                },
                notice = if (target == FlightVisibility.PUBLIC) "Vol partagé sur le fil GLIDY."
                else "Vol retiré du fil : il redevient privé.",
            )
        }
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

    /**
     * Relecture silencieuse (GLIDY S10) : garde le vol ouvert et n'affiche pas d'écran de chargement —
     * utilisée au retour sur l'onglet ou du rejeu 3D, quand un vol a pu être archivé entre-temps.
     */
    fun reload() {
        val current = mutableState.value as? MyFlightsUiState.Ready ?: return refresh()
        viewModelScope.launch {
            val recovered = runCatching { completedFlightGateway?.recoverPending() }.getOrNull()
            val flights = runCatching { repository.listFlights().sortedForDisplay() }.getOrNull() ?: return@launch
            val latest = mutableState.value as? MyFlightsUiState.Ready ?: current
            mutableState.value = latest.copy(
                flights = flights,
                selectedFlightId = latest.selectedFlightId?.takeIf { id -> flights.any { it.id == id } },
                notice = recovered?.issueNotice() ?: latest.notice,
            )
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
                    notice = "Vol supprimé de ce téléphone.",
                )
                RemoveFlightResult.NotFound -> latest.copy(
                    flights = latest.flights.filterNot { it.id == id },
                    selectedFlightId = null,
                    pendingDeleteFlightId = null,
                    notice = "Vol déjà absent du carnet.",
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
    private val profileStore: ProfileStore? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MyFlightsViewModel::class.java))
        return MyFlightsViewModel(repository, shareGateway, completedFlightGateway, profileStore) as T
    }
}

internal fun ShareFlightResult.userMessage(): String = when (this) {
    ShareFlightResult.Presented -> "Le menu de partage est ouvert."
    ShareFlightResult.NotFound -> "Ce vol n'est plus présent dans le carnet."
    ShareFlightResult.FileUnavailable -> "Fichier IGC introuvable sur ce téléphone."
    is ShareFlightResult.Failed -> "Partage impossible : $reason"
}
