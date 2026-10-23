import Foundation
import UIKit

#if DEBUG
/// Build de développement seulement, avec `-bench` : toutes les 30 s, temps processeur de l'app (% d'un cœur),
/// état thermique et batterie dans Documents/bench.log, relu depuis le Mac (devicectl). Sert à mesurer
/// l'énergie sans Instruments ; jamais dans une version publiée.
enum BenchMeter {
    private static var timer: Timer?
    private static var lastCpu = 0.0
    private static var lastWall = 0.0
    /// Ligne supplémentaire fournie par l'app (cadence baro, source du vario…).
    static var extra: () -> String = { "" }

    static func startIfRequested() {
        guard RootView.bench, timer == nil else { return }
        UIDevice.current.isBatteryMonitoringEnabled = true
        lastCpu = cpuSeconds(); lastWall = CACurrentMediaTime()
        let t = Timer(timeInterval: ProcessInfo.processInfo.arguments.contains("-benchFast") ? 2 : 30, repeats: true) { _ in sample() }
        RunLoop.main.add(t, forMode: .common)
        timer = t
        log("start \(ProcessInfo.processInfo.arguments.joined(separator: " "))")
    }

    private static func sample() {
        let cpu = cpuSeconds(), wall = CACurrentMediaTime()
        let pct = (cpu - lastCpu) / max(wall - lastWall, 0.001) * 100
        lastCpu = cpu; lastWall = wall
        let thermal = ["nominal", "fair", "serious", "critical"][min(ProcessInfo.processInfo.thermalState.rawValue, 3)]
        log(String(format: "cpu %.1f %% · thermique %@ · batterie %.0f %% · basse conso %@", pct, thermal,
                   UIDevice.current.batteryLevel * 100, ProcessInfo.processInfo.isLowPowerModeEnabled ? "oui" : "non") + " · " + extra())
    }

    /// Temps processeur cumulé de tous les fils du processus (s).
    private static func cpuSeconds() -> Double {
        var usage = rusage()
        getrusage(RUSAGE_SELF, &usage)
        return Double(usage.ru_utime.tv_sec + usage.ru_stime.tv_sec) + Double(usage.ru_utime.tv_usec + usage.ru_stime.tv_usec) / 1e6
    }

    private static func log(_ line: String) {
        let url = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("bench.log")
        let stamp = ISO8601DateFormatter().string(from: Date())
        let data = Data("\(stamp) \(line)\n".utf8)
        if let h = try? FileHandle(forWritingTo: url) { h.seekToEndOfFile(); h.write(data); try? h.close() }
        else { try? data.write(to: url) }
    }
}
#endif
