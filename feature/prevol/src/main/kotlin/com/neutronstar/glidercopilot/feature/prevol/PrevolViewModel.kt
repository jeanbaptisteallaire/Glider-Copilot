package com.neutronstar.glidercopilot.feature.prevol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neutronstar.glidercopilot.domain.Club
import com.neutronstar.glidercopilot.domain.flarm.Registration
import com.neutronstar.glidercopilot.precog.DayWeather
import com.neutronstar.glidercopilot.precog.WeatherRepository
import com.neutronstar.glidercopilot.precog.WeatherResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** Ce dont Prévol a besoin côté club, fourni par l'app (local aujourd'hui, compte utilisateur demain). */
interface ClubSource {
    val clubs: List<Club>
    val selectedClub: Flow<Club?>
    suspend fun select(clubId: String)
}

/** Planeur du jour : immatriculation appairée, trois dernières saisies, détection de décollage. */
interface GliderSource {
    val pairedRegistration: Flow<String?>
    val recentRegistrations: Flow<List<String>>
    val autoTakeoff: Flow<Boolean>
    /** Enregistre l'immatriculation (déjà normalisée) et la cherche dans la base OGN. */
    suspend fun pair(registration: String): PairingStatus
    /** Statut de l'appairage courant, sans nouvelle saisie (copie locale de la base si possible). */
    suspend fun currentStatus(registration: String): PairingStatus
    suspend fun setAutoTakeoff(on: Boolean)
}

sealed interface PairingStatus {
    data object None : PairingStatus
    data object Checking : PairingStatus
    /** Boîtier trouvé et suivi autorisé. [device] ex. « FLARM DD1234 · LS-4 ». */
    data class Paired(val device: String, val source: String) : PairingStatus
    data class NotFound(val source: String) : PairingStatus
    data object NotTracked : PairingStatus
    data object Unverified : PairingStatus
}

data class PairingUi(
    val paired: String? = null,
    val input: String = "",
    val recent: List<String> = emptyList(),
    val status: PairingStatus = PairingStatus.None,
    val autoTakeoff: Boolean = false,
    val message: String? = null,
)

data class PrevolUiState(
    val club: Club? = null,
    val clubs: List<Club> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val day: DayWeather? = null,
    val selectedHour: Int = 0,
    val now: Instant = Instant.now(),
    val pairing: PairingUi = PairingUi(),
)

class PrevolViewModel(
    private val weather: WeatherRepository,
    private val clubSource: ClubSource,
    private val glider: GliderSource,
) : ViewModel() {
    private val _state = MutableStateFlow(PrevolUiState(clubs = clubSource.clubs.filter { it.position != null && !it.isFederation }.sortedBy { it.name }))
    val state: StateFlow<PrevolUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            clubSource.selectedClub.distinctUntilChanged().collect { club ->
                _state.update { it.copy(club = club, day = null, error = null) }
                refresh()
            }
        }
        viewModelScope.launch {
            glider.recentRegistrations.collect { r -> _state.update { it.copy(pairing = it.pairing.copy(recent = r)) } }
        }
        viewModelScope.launch {
            glider.autoTakeoff.collect { on -> _state.update { it.copy(pairing = it.pairing.copy(autoTakeoff = on)) } }
        }
        viewModelScope.launch {
            val reg = glider.pairedRegistration.first()
            _state.update { it.copy(pairing = it.pairing.copy(paired = reg, input = reg ?: "", status = if (reg == null) PairingStatus.None else PairingStatus.Checking)) }
            if (reg != null) {
                val st = glider.currentStatus(reg)
                _state.update { s -> if (s.pairing.paired == reg) s.copy(pairing = s.pairing.copy(status = st)) else s }
            }
        }
    }

    fun refresh() {
        val club = _state.value.club ?: return
        val pos = club.position ?: run {
            _state.update { it.copy(error = "Position du terrain inconnue pour ce club", loading = false) }
            return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, now = Instant.now()) }
            when (val r = weather.loadDay(pos, club.departement)) {
                is WeatherResult.Success -> _state.update { s ->
                    val best = r.day.summary.best?.let { b -> r.day.hours.indexOfFirst { it.analysis.validTime == b.validTime } } ?: -1
                    s.copy(loading = false, day = r.day, selectedHour = if (best >= 0) best else 0)
                }
                is WeatherResult.Error -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun selectHour(index: Int) = _state.update { it.copy(selectedHour = index) }

    fun selectClub(id: String) {
        viewModelScope.launch { clubSource.select(id) }
    }

    fun onRegistrationInput(text: String) =
        _state.update { it.copy(pairing = it.pairing.copy(input = text.uppercase().take(Registration.MAX_LENGTH), message = null)) }

    fun validateRegistration() {
        val reg = Registration.normalize(_state.value.pairing.input)
        if (!Registration.isValid(reg)) {
            _state.update { it.copy(pairing = it.pairing.copy(message = "Immatriculation trop courte")) }
            return
        }
        _state.update { it.copy(pairing = it.pairing.copy(paired = reg, input = reg, status = PairingStatus.Checking, message = null)) }
        viewModelScope.launch {
            val st = glider.pair(reg)
            _state.update { s -> if (s.pairing.paired == reg) s.copy(pairing = s.pairing.copy(status = st)) else s }
        }
    }

    fun cancelRegistration() = _state.update { it.copy(pairing = it.pairing.copy(input = it.pairing.paired ?: "", message = null)) }

    fun pickRecent(reg: String) {
        onRegistrationInput(reg)
        validateRegistration()
    }

    fun setAutoTakeoff(on: Boolean) {
        viewModelScope.launch { glider.setAutoTakeoff(on) }
    }

    class Factory(private val weather: WeatherRepository, private val clubs: ClubSource, private val glider: GliderSource) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PrevolViewModel(weather, clubs, glider) as T
    }
}
