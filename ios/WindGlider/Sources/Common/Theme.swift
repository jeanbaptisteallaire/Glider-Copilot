import SwiftUI

/// Jetons de la charte (miroir de core/designsystem/Theme.kt). Ne jamais coder une couleur ou une police dans un écran.
enum WG {
    private static func rgb(_ hex: UInt32) -> Color {
        Color(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }

    // Charte Godspeed (LIGHT PFD), JB 22/10/2026 : toute l'app en sombre lavande, jamais noir pur ; SF Pro.
    static let brandInk = rgb(0xE8EDF2)     // texte principal (text1 Godspeed)
    /// Encre des pages illustrées claires (accueil, couverture) : l'ancien bleu nuit de la marque.
    static let paperInk = rgb(0x1D3550)
    static let coverSea = rgb(0x073E60)     // fond de la couverture animée (COVER_SEA)
    static let brandPaper = rgb(0x24233A)   // fond de page (repli uni de `page`)
    static let sky = rgb(0x12A4F0)          // cyan Godspeed : ce qui se touche ou se sélectionne
    static let card = rgb(0x34334A)         // panneau lavande
    static let line = Color.white.opacity(0.10)
    static let dim = rgb(0xA3ABB7)          // texte secondaire (text2 Godspeed)
    /// Fond de page : dégradé lavande du ciel Thales, du haut vers le bas.
    static let page = LinearGradient(colors: [rgb(0x3D3E53), rgb(0x24233A), rgb(0x1C1B29)], startPoint: .top, endPoint: .bottom)
    /// Fond de carte de page : dégradé lavande léger.
    static let cardFill = LinearGradient(colors: [rgb(0x3B3A52), rgb(0x2F2E43)], startPoint: .top, endPoint: .bottom)

    // sens (fixe, jamais décoratif — Godspeed sur fond sombre)
    static let good = rgb(0x38D67F)         // normal / marge positive
    static let bad = rgb(0xFF4A3D)          // alarme
    static let warn = rgb(0xFFB52E)         // prudence (ambre)
    static let climb = rgb(0xFF9F43)        // orange montée (v8)
    static let sink = rgb(0x38D67F)
    static let route = rgb(0xE35BE6)        // magenta Godspeed : navigation, route vers le terrain
    static let sun = rgb(0xF29900)          // thermique
    static let altitude = rgb(0x9B9FD0)     // plafond, altitudes (lavande claire)
    static let wind = rgb(0x12A4F0)

    /// Couleur de montée des graphiques sur pages blanches (Android chartClimbColor) : teinte soleil plus soutenue en montée forte.
    static func chartClimb(_ ms: Double) -> Color { sun.opacity(min(max(0.3 + ms / 2.5 * 0.7, 0.3), 1.0)) }

    /// Familles d'espaces (Godspeed, règle R21 : jamais la couleur seule, le trait porte aussi l'information) :
/// contrôlés bleu trait plein, réglementés rouge trait plein, information vert tirets, autres gris tirets.
    static func airspaceDash(_ family: Int32) -> [CGFloat] {
        switch family { case 0, 1: return []; default: return [10, 6] }
    }
    static func airspaceFill(_ family: Int32) -> CGFloat {
        switch family { case 1: return 0.10; case 0: return 0.06; default: return 0.04 }
    }
    static func airspace(_ family: Int32) -> Color {
        // couleurs des zones de Godspeed (zoneInk), lisibles sur satellite comme sur plan
        switch family { case 0: return rgb(0x6FA0E8); case 1: return rgb(0xE0607A); case 2: return rgb(0x5CCB86); default: return rgb(0xAEB6C0) }
    }

    /// Échelle vario (−3 → +4 m/s), comme `vario()` de la charte : vert en descente, orange → rouge en montée.
    static func vario(_ ms: Double) -> Color {
        if ms < 0 { return rgb(0x2E9D57).opacity(min(1, 0.4 + -ms / 3 * 0.6)) }
        if ms < 1 { return rgb(0xF5B041) }
        if ms < 2 { return rgb(0xF0862A) }
        return rgb(0xD9381E)
    }

    // typographie : SF Pro (police de Godspeed), échelle d'Apple
    static let largeTitle = Font.system(size: 34).weight(.bold)
    static let title = Font.system(size: 22).weight(.semibold)
    static let headline = Font.system(size: 17).weight(.semibold)
    static let body = Font.system(size: 17)
    static let subhead = Font.system(size: 15)
    static let footnote = Font.system(size: 13)
    static let caption = Font.system(size: 12)
    static let callout = Font.system(size: 16)
    static let caption2 = Font.system(size: 11)
    static func number(_ size: CGFloat) -> Font { Font.system(size: size, weight: .semibold).monospacedDigit() }
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
        .background(WG.cardFill, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(WG.line))
    }
}
