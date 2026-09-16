package com.neutronstar.glidercopilot.feature.prevol

import com.neutronstar.glidercopilot.carto.PackInfo
import com.neutronstar.glidercopilot.domain.aero.Airspace
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

/** Carte hors ligne du club, fournie par l'app (téléchargement, validité, espaces aériens autour du terrain). */
interface OfflineMapSource {
    val state: StateFlow<OfflineMapUi>
    fun refreshCatalog()
    fun download()
    fun cancel()
}

data class OfflineMapUi(
    /** Pack de la région du club dans le catalogue publié (null : catalogue non chargé ou club hors couverture). */
    val available: PackInfo? = null,
    /** Pack installé sur le téléphone pour cette région. */
    val installed: PackInfo? = null,
    val catalogLoading: Boolean = false,
    val catalogError: String? = null,
    val outOfCoverage: Boolean = false,
    /** Avancement du téléchargement en cours, 0..1. */
    val progress: Float? = null,
    val downloadError: String? = null,
    val fieldLabel: String? = null,
    val airspacesAround: List<Pair<Airspace, Double>> = emptyList(),
    val aeroFetched: Instant? = null,
    val now: Instant = Instant.now(),
) {
    val updateAvailable: Boolean get() = installed != null && available != null && available.version != installed.version
    val aeroExpired: Boolean get() = installed?.aeroExpired(now) ?: false
}
