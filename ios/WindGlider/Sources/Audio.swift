import AVFoundation

/// Son du vario, mêmes règles qu'Android (VarioTone.kt, maquette v8) : bips de plus en plus rapides et aigus en montée
/// (au-delà de +0,25 m/s), tonalité grave continue en forte descente (sous −1,7 m/s), silence entre les deux.
final class VarioTone {
    private let engine = AVAudioEngine()
    private var source: AVAudioSourceNode!
    private let rate = 22_050.0
    private var vario = 0.0
    private var phase = 0.0
    private var beepPhase = 0.0
    private var gain = 0.0
    private var running = false
    private let lock = NSLock()

    init() {
        source = AVAudioSourceNode { [unowned self] _, _, frameCount, abl -> OSStatus in
            lock.lock(); let v = vario; lock.unlock()
            let buffers = UnsafeMutableAudioBufferListPointer(abl)
            let chunk = Int(frameCount)
            var on = false
            var freq = 400.0
            var vol = 0.16
            if v > 0.25 {
                let period = min(max(0.55 - v * 0.09, 0.14), 0.55)
                beepPhase = (beepPhase + Double(chunk) / rate / period).truncatingRemainder(dividingBy: 1.0)
                on = beepPhase < 0.5
                freq = 540 + v * 120
            } else if v < -1.7 {
                on = true
                freq = 220 + v * 10
                vol *= 0.45
            }
            let target = on ? vol : 0.0
            for i in 0..<chunk {
                gain += (target - gain) * 0.01
                phase += 2 * .pi * freq / rate
                if phase > 2 * .pi { phase -= 2 * .pi }
                let s = Float(sin(phase) * gain)
                for b in buffers { b.mData?.assumingMemoryBound(to: Float.self)[i] = s }
            }
            return noErr
        }
        let format = AVAudioFormat(standardFormatWithSampleRate: rate, channels: 1)!
        engine.attach(source)
        engine.connect(source, to: engine.mainMixerNode, format: format)
    }

    func set(_ v: Double) { lock.lock(); vario = v; lock.unlock() }

    func start() {
        guard !running else { return }
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default, options: [.mixWithOthers, .duckOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
        running = (try? engine.start()) != nil
    }

    func stop() {
        guard running else { return }
        engine.stop(); running = false
    }
}

/// Annonces vocales en français (équivalent du TextToSpeech d'Android).
final class Voice {
    private let synth = AVSpeechSynthesizer()
    var enabled = true

    func speak(_ text: String, force: Bool = false) {
        guard enabled || force else { return }
        let u = AVSpeechUtterance(string: text)
        u.voice = AVSpeechSynthesisVoice(language: "fr-FR")
        synth.speak(u)
    }
}
