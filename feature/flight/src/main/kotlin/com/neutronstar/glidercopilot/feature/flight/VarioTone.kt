package com.neutronstar.glidercopilot.feature.flight

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

/**
 * Son du vario, mêmes règles que la maquette v8 : bips de plus en plus rapides et aigus en montée
 * (au-delà de +0,25 m/s), tonalité grave continue en forte descente (sous −1,7 m/s), silence entre les deux.
 */
class VarioTone {
    @Volatile var vario: Double = 0.0
    @Volatile private var running = false
    private var worker: Thread? = null

    fun start() {
        if (running) return
        running = true
        worker = thread(name = "vario-tone", isDaemon = true) { loop() }
    }

    fun stop() {
        running = false
        worker?.join(300)
        worker = null
    }

    private fun loop() {
        val rate = 22_050
        val chunk = rate / 50 // 20 ms
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(chunk * 2 * 4)
                .build()
        }.getOrNull() ?: run { running = false; return }
        val buf = ShortArray(chunk)
        var phase = 0.0
        var beepPhase = 0.0
        var gain = 0.0
        try {
            track.play()
            while (running) {
                val v = vario
                var on = false
                var freq = 400.0
                var vol = 0.16
                if (v > 0.25) {
                    val period = (0.55 - v * 0.09).coerceIn(0.14, 0.55)
                    beepPhase = (beepPhase + 0.02 / period) % 1.0
                    on = beepPhase < 0.5
                    freq = 540 + v * 120
                } else if (v < -1.7) {
                    on = true
                    freq = 220 + v * 10
                    vol *= 0.45
                }
                val target = if (on) vol else 0.0
                for (i in 0 until chunk) {
                    gain += (target - gain) * 0.01
                    phase += 2 * PI * freq / rate
                    if (phase > 2 * PI) phase -= 2 * PI
                    buf[i] = (sin(phase) * gain * Short.MAX_VALUE).toInt().toShort()
                }
                track.write(buf, 0, chunk)
            }
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }
}
