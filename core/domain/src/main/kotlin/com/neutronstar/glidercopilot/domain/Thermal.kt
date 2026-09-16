package com.neutronstar.glidercopilot.domain

import java.time.Instant
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min

/** Un niveau du profil vertical ARPEGE, déjà converti en unités SI. */
data class ProfileLevel(
    val heightAglM: Double,
    val temperatureK: Double,
    val dewpointK: Double?,
    val pressurePa: Double,
    val windSpeedMs: Double?,
    val windFromDeg: Double?,
    val geopotentialM2s2: Double?,
    val cloudFraction: Double?,
)

/** Conditions de surface à une échéance, en unités SI. [shortwaveNetWm2] est un flux moyen sur l'heure. */
data class SurfaceConditions(
    val validTime: Instant,
    val temperature2mK: Double,
    val dewpoint2mK: Double?,
    val pressureSurfacePa: Double?,
    val shortwaveNetWm2: Double?,
    val totalCloudPct: Double?,
    val lowCloudPct: Double?,
    val capeJkg: Double?,
    val wind10mMs: Double?,
    val wind10mFromDeg: Double?,
)

enum class LiftType { NONE, BLUE, CUMULUS }

data class ThermalAnalysis(
    val validTime: Instant,
    val groundAltitudeM: Double,
    /** Sommet des thermiques secs (m sol). Nul si pas d'ascendance ; plafonné au sommet du profil. */
    val dryTopAglM: Double,
    val dryTopCapped: Boolean,
    /** Base des cumulus estimée (m sol), formule d'Espy. */
    val cloudBaseAglM: Double?,
    /** Plafond exploitable = min(sommet sec, base Cu) en m sol. */
    val ceilingAglM: Double,
    val liftType: LiftType,
    /** Vitesse convective de Deardorff (m/s). */
    val wStarMs: Double,
    /** Taux de montée estimé pour un planeur (m/s). */
    val climbMs: Double,
    val capeJkg: Double?,
    val totalCloudPct: Double?,
) {
    val ceilingMslM: Double get() = groundAltitudeM + ceilingAglM
    val cloudBaseMslM: Double? get() = cloudBaseAglM?.let { groundAltitudeM + it }
}

/**
 * Modèle thermique simplifié (hypothèses à valider par un pilote instructeur) :
 * - parcelle adiabatique sèche partant de la température à 2 m + [Params.triggerExcessK] ;
 * - sommet = premier niveau où la température potentielle de l'environnement dépasse celle de la parcelle ;
 * - base des cumulus = 125 m × (T − Td) ;
 * - flux de chaleur sensible = [Params.sensibleHeatFraction] × rayonnement solaire net ;
 * - montée planeur = [Params.climbEfficiency] × w* − [Params.gliderSinkMs].
 */
object ThermalModel {
    data class Params(
        val triggerExcessK: Double = 1.0,
        val sensibleHeatFraction: Double = 0.35,
        val climbEfficiency: Double = 0.85,
        val gliderSinkMs: Double = 0.75,
        val espyMetresPerKelvin: Double = 125.0,
        val minUsableCeilingAglM: Double = 600.0,
    )

    fun groundAltitude(levels: List<ProfileLevel>): Double? {
        val values = levels.mapNotNull { l -> l.geopotentialM2s2?.let { it / Atmosphere.G - l.heightAglM } }
        return if (values.isEmpty()) null else values.average()
    }

    fun analyse(
        surface: SurfaceConditions,
        profile: List<ProfileLevel>,
        params: Params = Params(),
    ): ThermalAnalysis {
        require(profile.isNotEmpty()) { "profil vide" }
        val levels = profile.sortedBy { it.heightAglM }
        val ground = groundAltitude(levels) ?: 0.0
        val pSurf = surface.pressureSurfacePa ?: extrapolateSurfacePressure(levels)
        val thetaParcel = Atmosphere.potentialTemperature(surface.temperature2mK + params.triggerExcessK, pSurf)

        var top = 0.0
        var capped = true
        var prevH = 0.0
        var prevExcess = thetaParcel - Atmosphere.potentialTemperature(surface.temperature2mK, pSurf)
        for (l in levels) {
            val excess = thetaParcel - Atmosphere.potentialTemperature(l.temperatureK, l.pressurePa)
            if (excess <= 0) {
                top = if (prevExcess > 0) prevH + (l.heightAglM - prevH) * prevExcess / (prevExcess - excess) else prevH
                capped = false
                break
            }
            prevH = l.heightAglM
            prevExcess = excess
            top = l.heightAglM
        }

        val cloudBase = surface.dewpoint2mK?.let { td ->
            max(0.0, (surface.temperature2mK - td) * params.espyMetresPerKelvin)
        }
        val ceiling = if (cloudBase != null && cloudBase < top) cloudBase else top
        val liftType = when {
            ceiling < params.minUsableCeilingAglM -> LiftType.NONE
            cloudBase != null && cloudBase < top -> LiftType.CUMULUS
            else -> LiftType.BLUE
        }

        val sw = max(0.0, surface.shortwaveNetWm2 ?: 0.0)
        val rho = Atmosphere.airDensity(pSurf, surface.temperature2mK)
        val kinematicFlux = params.sensibleHeatFraction * sw / (rho * Atmosphere.CP)
        val wStar = if (ceiling <= 0) 0.0 else cbrt(Atmosphere.G / surface.temperature2mK * kinematicFlux * ceiling)
        val climb = if (liftType == LiftType.NONE) 0.0 else max(0.0, params.climbEfficiency * wStar - params.gliderSinkMs)

        return ThermalAnalysis(
            validTime = surface.validTime,
            groundAltitudeM = ground,
            dryTopAglM = top,
            dryTopCapped = capped,
            cloudBaseAglM = cloudBase,
            ceilingAglM = ceiling,
            liftType = liftType,
            wStarMs = wStar,
            climbMs = climb,
            capeJkg = surface.capeJkg,
            totalCloudPct = surface.totalCloudPct,
        )
    }

    /** Pression au sol par extrapolation hypsométrique depuis le niveau le plus bas. */
    private fun extrapolateSurfacePressure(levels: List<ProfileLevel>): Double {
        val l = levels.first()
        return l.pressurePa * kotlin.math.exp(Atmosphere.G * l.heightAglM / (Atmosphere.R_DRY * l.temperatureK))
    }
}

enum class DayQuality(val label: String) { NONE("Pas de thermique"), WEAK("Faible"), AVERAGE("Moyenne"), GOOD("Bonne"), EXCELLENT("Excellente") }

data class DaySummary(
    val hours: List<ThermalAnalysis>,
    val triggerTime: Instant?,
    val endTime: Instant?,
    val best: ThermalAnalysis?,
    val quality: DayQuality,
    val overdevelopmentRisk: Boolean,
)

object DayPlanner {
    const val USABLE_CLIMB_MS = 0.5

    fun summarize(hours: List<ThermalAnalysis>): DaySummary {
        val sorted = hours.sortedBy { it.validTime }
        val usable = sorted.filter { it.liftType != LiftType.NONE && it.climbMs >= USABLE_CLIMB_MS }
        val best = usable.maxByOrNull { it.ceilingMslM }
        val maxClimb = usable.maxOfOrNull { it.climbMs } ?: 0.0
        val quality = when {
            usable.isEmpty() -> DayQuality.NONE
            maxClimb < 0.9 -> DayQuality.WEAK
            maxClimb < 1.6 -> DayQuality.AVERAGE
            maxClimb < 2.5 -> DayQuality.GOOD
            else -> DayQuality.EXCELLENT
        }
        val overdev = sorted.any { (it.capeJkg ?: 0.0) > 800.0 && it.liftType == LiftType.CUMULUS }
        return DaySummary(sorted, usable.firstOrNull()?.validTime, usable.lastOrNull()?.validTime, best, quality, overdev)
    }
}
