package com.neutronstar.glidercopilot.domain.flight

import com.neutronstar.glidercopilot.domain.LatLon
import java.time.Instant

/**
 * Journal de calibration (V7.3, demande JB) : capture les échantillons bruts qui alimentent [VarioFilter]
 * en vol — baromètre, accélération verticale déjà projetée (pas les 3 axes ni la gravité : c'est l'entrée
 * réelle du filtre), et GPS — pour rejouer et affiner les réglages du filtre après coup, jamais en vol.
 * CSV compact, une lettre + valeurs, pensé pour rester léger même sur un vol de plusieurs heures :
 *   B,<t_ns>,<hPa>
 *   A,<t_ns>,<up_ms2>
 *   G,<t_ns>,<epoch_ms>,<lat>,<lon>,<alt_m|>,<vit_kmh|>,<cap_deg|>,<precision_m|>
 * Rien ici ne dépend d'Android : enregistrement (par [CalibrationRecorder]) et lecture tournent aussi bien
 * sur le téléphone qu'au dépouillement. [SensorSample] est le même type que celui du banc de rejeu
 * ([SensorReplay]) : un journal relu par [parse] peut être rejoué tel quel dans [VarioFilter].
 */
object CalibrationLog {
    const val HEADER = "# GLIDY calibration v1 : B,t_ns,hPa | A,t_ns,up_ms2 | G,t_ns,epoch_ms,lat,lon,alt,vit_kmh,cap,precision_m"

    fun line(s: SensorSample): String = when (s) {
        is SensorSample.Baro -> "B,${s.timeNs},${s.hPa}"
        is SensorSample.Accel -> "A,${s.timeNs},${s.upMs2}"
        is SensorSample.Gps -> {
            val f = s.fix
            "G,${s.timeNs},${f.time.toEpochMilli()},${f.position.lat},${f.position.lon}," +
                "${opt(f.altitudeM)},${opt(f.groundSpeedKmh)},${opt(f.trackDeg)},${opt(f.accuracyM)}"
        }
    }

    private fun opt(v: Double?) = v?.toString() ?: ""

    fun parseLine(raw: String): SensorSample? {
        if (raw.isEmpty() || raw[0] == '#') return null
        val p = raw.split(',')
        if (p.size < 3) return null
        return runCatching {
            when (p[0]) {
                "B" -> SensorSample.Baro(p[1].toLong(), p[2].toDouble())
                "A" -> SensorSample.Accel(p[1].toLong(), p[2].toDouble())
                "G" -> SensorSample.Gps(
                    p[1].toLong(),
                    GpsFix(
                        time = Instant.ofEpochMilli(p[2].toLong()),
                        position = LatLon(p[3].toDouble(), p[4].toDouble()),
                        altitudeM = p.getOrNull(5)?.toDoubleOrNull(),
                        groundSpeedKmh = p.getOrNull(6)?.toDoubleOrNull(),
                        trackDeg = p.getOrNull(7)?.toDoubleOrNull(),
                        accuracyM = p.getOrNull(8)?.toDoubleOrNull(),
                    ),
                )
                else -> null
            }
        }.getOrNull()
    }

    /** Relit un journal entier (une ligne = un échantillon), dans l'ordre où il a été écrit. */
    fun parse(text: String): List<SensorSample> = text.lineSequence().mapNotNull { parseLine(it.trim()) }.toList()
}

/** Écrit le journal au fil de l'eau, un appel par échantillon : [sink] reçoit une ligne (sans le saut de ligne). */
class CalibrationRecorder(sink: (String) -> Unit) {
    private val write = sink
    var samples = 0; private set

    init { write(CalibrationLog.HEADER) }

    fun onBaro(hPa: Double, timeNs: Long) { write(CalibrationLog.line(SensorSample.Baro(timeNs, hPa))); samples++ }
    fun onAccel(upMs2: Double, timeNs: Long) { write(CalibrationLog.line(SensorSample.Accel(timeNs, upMs2))); samples++ }
    fun onGps(fix: GpsFix, timeNs: Long) { write(CalibrationLog.line(SensorSample.Gps(timeNs, fix))); samples++ }
}
