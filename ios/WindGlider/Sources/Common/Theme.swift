import SwiftUI

/// Jetons de la charte (miroir de core/designsystem/Theme.kt). Ne jamais coder une couleur ou une police dans un écran.
enum WG {
    private static func rgb(_ hex: UInt32) -> Color {
        Color(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }

    // marque (V20)
    static let brandInk = rgb(0x1D3550)
    static let coverSea = rgb(0x073E60)     // fond de la couverture animée (COVER_SEA)
    static let brandPaper = rgb(0xF6F3EE)
    static let sky = rgb(0x0A84FF)          // sélection, liens
    static let card = Color.white
    static let line = Color.black.opacity(0.08)
    static let dim = Color.secondary

    // sens (charte Pilotage, thème clair)
    static let good = rgb(0x1E8E3E)         // marge positive
    static let bad = rgb(0xD93025)          // marge négative, alerte
    static let warn = rgb(0xC25E00)         // message à lire (Gc.colors.warn, thème clair)
    static let climb = rgb(0xFF9F43)        // orange montée (v8)
    static let sink = rgb(0x2E7D32)
    static let route = rgb(0x7B3FE4)        // route vers le terrain, cap
    static let sun = rgb(0xF29900)          // thermique
    static let altitude = rgb(0x5856D6)     // plafond, altitudes
    static let wind = rgb(0x0A84FF)

    /// Couleur de montée des graphiques sur pages blanches (Android chartClimbColor) : teinte soleil plus soutenue en montée forte.
    static func chartClimb(_ ms: Double) -> Color { sun.opacity(min(max(0.3 + ms / 2.5 * 0.7, 0.3), 1.0)) }

    /// Familles d'espaces (charte Android, carte claire) : contrôlés bleu, réglementés rouge-magenta, information vert.
    static func airspace(_ family: Int32) -> Color {
        switch family { case 0: return rgb(0x1F5FBF); case 1: return rgb(0xC2185B); case 2: return rgb(0x2E9D57); default: return rgb(0x8A8A8A) }
    }

    /// Échelle vario (−3 → +4 m/s), comme `vario()` de la charte : vert en descente, orange → rouge en montée.
    static func vario(_ ms: Double) -> Color {
        if ms < 0 { return rgb(0x2E9D57).opacity(min(1, 0.4 + -ms / 3 * 0.6)) }
        if ms < 1 { return rgb(0xF5B041) }
        if ms < 2 { return rgb(0xF0862A) }
        return rgb(0xD9381E)
    }

    // typographie : Inter, échelle d'Apple (Gc.type)
    static let largeTitle = Font.custom("Inter", size: 34, relativeTo: .largeTitle).weight(.bold)
    static let title = Font.custom("Inter", size: 22, relativeTo: .title2).weight(.semibold)
    static let headline = Font.custom("Inter", size: 17, relativeTo: .headline).weight(.semibold)
    static let body = Font.custom("Inter", size: 17, relativeTo: .body)
    static let subhead = Font.custom("Inter", size: 15, relativeTo: .subheadline)
    static let footnote = Font.custom("Inter", size: 13, relativeTo: .footnote)
    static let caption = Font.custom("Inter", size: 12, relativeTo: .caption)
    static let caption2 = Font.custom("Inter", size: 11, relativeTo: .caption2)
    static func number(_ size: CGFloat) -> Font { Font.custom("Inter", size: size).weight(.semibold).monospacedDigit() }
}

/// Carte blanche arrondie des pages façon Apple Santé.
struct WGCard<Content: View>: View {
    var title: String? = nil
    var systemImage: String? = nil
    var tint: Color = WG.brandInk
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let title {
                Label(title, systemImage: systemImage ?? "circle").font(WG.headline).foregroundStyle(tint)
            }
            content
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(WG.card, in: RoundedRectangle(cornerRadius: 16))
    }
}
