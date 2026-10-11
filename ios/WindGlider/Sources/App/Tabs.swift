import SwiftUI

/// Onglets (Tab d'AppRoot.kt) : Lite = Accueil, Tuto, Prévol, Pilotage, Mes vols ; complète = tous.
/// iOS : l'accueil est un onglet (Android y revient par le retour système) ; au-delà de cinq, iOS range le reste dans « Plus ».
enum AppTab: Int, CaseIterable, Identifiable {
    case home, prevol, pilotage, carte, mesVols, checklist, feed, tuto
    var id: Int { rawValue }

    static var visible: [AppTab] {
        // complète : les quatre onglets de vol en barre, accueil (ouvert au lancement), Check-lists, Feed et Tuto dans « Plus »
        Edition.full ? [.prevol, .pilotage, .carte, .mesVols, .home, .checklist, .feed, .tuto]
                     : [.home, .tuto, .prevol, .pilotage, .mesVols]
    }

    func label(_ lang: String) -> String {
        let fr = lang == "fr"
        switch self {
        case .home: return fr ? "Accueil" : "Home"
        case .prevol: return fr ? "Prévol" : "Pre-flight"
        case .pilotage: return fr ? "Pilotage" : "Flight"
        case .carte: return fr ? "Carte" : "Map"
        case .mesVols: return fr ? "Mes vols" : "My flights"
        case .checklist: return fr ? "Check-lists" : "Checklists"
        case .feed: return "Feed"
        case .tuto: return "Tuto"
        }
    }

    var icon: String {
        switch self {
        case .home: return "house"
        case .prevol: return "sun.max"
        case .pilotage: return "airplane"
        case .carte: return "map"
        case .mesVols: return "person"
        case .checklist: return "checklist"
        case .feed: return "person.2"
        case .tuto: return "graduationcap"
        }
    }
}
