import Foundation

/// Édition construite, comme `BuildConfig.LITE` d'Android : cible WindGlider = Lite (Prévol, Pilotage, Mes vols, Tuto),
/// cible WindGliderFull = complète (Feed, Check-lists, Carte, trafic OGN, partage, sauvegarde en ligne).
enum Edition {
    #if FULL
    static let full = true
    #else
    static let full = false
    #endif
    static var lite: Bool { !full }
}
