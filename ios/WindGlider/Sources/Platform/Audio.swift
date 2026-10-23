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
    /// Son voulu par le pilote : après un appel, un casque Bluetooth branché ou débranché, le son revient seul
    /// (revue résilience M1 : il restait muet jusqu'au redémarrage de l'app).
    private var wanted = false
    private let lock = NSLock()
    private var observers: [NSObjectProtocol] = []

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
        let nc = NotificationCenter.default
        observers.append(nc.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] n in
            guard let self, let raw = n.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
                  let type = AVAudioSession.InterruptionType(rawValue: raw) else { return }
            if type == .began { self.running = false } else if self.wanted { self.restart() }
        })
        // changement de sortie (casque, haut-parleur) : AVAudioEngine s'arrête de lui-même
        observers.append(nc.addObserver(forName: .AVAudioEngineConfigurationChange, object: engine, queue: .main) { [weak self] _ in
            guard let self else { return }
            self.running = false
            if self.wanted { self.restart() }
        })
        observers.append(nc.addObserver(forName: AVAudioSession.mediaServicesWereResetNotification, object: nil, queue: .main) { [weak self] _ in
            guard let self else { return }
            self.running = false
            if self.wanted { self.restart() }
        })
    }

    deinit { observers.forEach(NotificationCenter.default.removeObserver) }

    private func restart() { running = false; start() }

    func set(_ v: Double) { lock.lock(); vario = v; lock.unlock() }

    func start() {
        wanted = true
        guard !running else { return }
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default, options: [.mixWithOthers, .duckOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
        running = (try? engine.start()) != nil
    }

    func stop() {
        wanted = false
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
        // revue résilience M2 : session audio active même son du vario coupé (mode silencieux, écran verrouillé)
        // même catégorie et même mode que le vario : pas de changement de configuration (et de trou dans le son) à chaque annonce
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default, options: [.mixWithOthers, .duckOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
        // alerte urgente : passe devant les annonces en attente
        if force, synth.isSpeaking { synth.stopSpeaking(at: .immediate) }
        let u = AVSpeechUtterance(string: text)
        u.voice = AVSpeechSynthesisVoice(language: "fr-FR")
        synth.speak(u)
    }
}
