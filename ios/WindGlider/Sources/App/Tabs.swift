import SwiftUI

/// Onglets de la version Android publiée (V20.4, édition Lite) : Tuto, Prévol, Pilotage, Mes vols — et rien d'autre.
/// Les autres onglets d'Android (Feed, Carte, Check-lists) ne sont pas dans la version publiée : pas sur iOS non plus.
enum AppTab: Int, CaseIterable, Identifiable {
    case tuto, prevol, pilotage, mesVols, checklist
    var id: Int { rawValue }

    static var visible: [AppTab] {
        [.tuto, .prevol, .pilotage, .mesVols]
    }

    func label(_ lang: String) -> String {
        let fr = lang == "fr"
        switch self {
        case .tuto: return "Tuto"
        case .prevol: return fr ? "Prévol" : "Pre-flight"
        case .pilotage: return fr ? "Pilotage" : "Flight"
        case .mesVols: return fr ? "Mes vols" : "My flights"
        case .checklist: return fr ? "Check-lists" : "Checklists"
        }
    }

    var icon: String {
        switch self {
        case .tuto: return "graduationcap"
        case .prevol: return "sun.max"
        case .pilotage: return "airplane"
        case .mesVols: return "person"
        case .checklist: return "checklist"
        }
    }
}
