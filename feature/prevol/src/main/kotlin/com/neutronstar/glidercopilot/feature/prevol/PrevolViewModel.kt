package com.neutronstar.glidercopilot.feature.prevol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neutronstar.glidercopilot.domain.Club
import com.neutronstar.glidercopilot.precog.DayWeather
import com.neutronstar.glidercopilot.precog.WeatherRepository
import com.neutronstar.glidercopilot.precog.WeatherResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** Ce dont Prévol a besoin côté club, fourni par l'app (local aujourd'hui, compte utilisateur demain). */
interface ClubSource {
    val clubs: List<Club>
    val selectedClub: Flow<Club?>
    suspend fun select(clubId: String)
}

data class PrevolUiState(
    val club: Club? = null,
    val clubs: List<Club> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val day: DayWeather? = null,
    val selectedHour: Int = 0,
    val now: Instant = Instant.now(),
)

class PrevolViewModel(
    private val weather: WeatherRepository,
    private val clubSource: ClubSource,
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

    class Factory(private val weather: WeatherRepository, private val clubs: ClubSource) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PrevolViewModel(weather, clubs) as T
    }
}
