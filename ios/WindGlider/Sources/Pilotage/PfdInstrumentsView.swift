import SwiftUI
import WindGliderShared

/// Langage visuel de LIGHT PFD (Godspeed) dans Pilotage (JB, 22/10/2026), version épurée après revue pilote / FH :
/// chaque information une seule fois, rien au centre de la carte. Calculs et textes : `AltitudeTape` / `VarioPalette`.
extension WG {
    /// Couleur ARGB venue du Kotlin commun.
    static func argb(_ v: Int64) -> Color {
        Color(red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255, blue: Double(v & 0xFF) / 255)
    }
    static func varioFlash(_ ms: Double) -> Color { argb(VarioPalette.shared.flash(ms: ms)) }
    /// Texte vario : flash en mode sombre, assombri (≥ 4,5:1 sur blanc) en mode clair.
    static func varioText(_ ms: Double, dark: Bool) -> Color { argb(dark ? VarioPalette.shared.flash(ms: ms) : VarioPalette.shared.ink(ms: ms)) }
    static let casing = argb(VarioPalette.shared.CASING)
    /// Cyan « réglé par le pilote » (minimum) : assombri en clair pour rester lisible sur blanc.
    static func selected(dark: Bool) -> Color { dark ? argb(0x1CB4FF) : argb(0x00739E) }
    static let alarmFill = argb(0xD4141C)
    /// Boîte de marge en alerte (JB, 23/10/2026) : aplats pleins, texte noir dessus (contraste ≥ 4,5:1).
    static let marginAlarmBox = argb(0xF2463A)
    static let marginCautionBox = argb(0xFF9F1C)
    /// Chiffres de vol, police de Godspeed : SF Pro, chiffres tabulaires.
    static func pfd(_ size: CGFloat, _ weight: Font.Weight = .semibold) -> Font { .system(size: size, weight: weight).monospacedDigit() }
    /// Charte Godspeed (ciel lavande de l'horizon Thales) pour les panneaux en mode sombre : dégradé violet, jamais noir pur.
    static let lavenderTop = argb(0x4F4F6B)
    static let lavenderBottom = argb(0x2B2A3A)
}

/// Fond de panneau de Pilotage : dégradé lavande Godspeed en sombre, blanc à 90 % en clair.
struct PanelBackground: ShapeStyle {
    let dark: Bool
    func resolve(in environment: EnvironmentValues) -> some ShapeStyle {
        dark ? AnyShapeStyle(LinearGradient(colors: [WG.lavenderTop.opacity(0.94), WG.lavenderBottom.opacity(0.94)], startPoint: .top, endPoint: .bottom))
             : AnyShapeStyle(Color.white.opacity(0.9))
    }
}

/// Bandeau sécurité (haut gauche) : « Marge sécu. (m) » en petit au-dessus de la marge en très gros, et l'altitude en
/// tambour à côté (« Altitude (m) »). Le ruban horizontal est retiré (JB, 22/10) : la marge le dit déjà.
struct SafetyBand: View {
    let marginText: String
    let marginColor: Color
    let tape: AltitudeTapeState
    let dark: Bool
    var essential = false
    /// Posé sous le chiffre de la marge, aligné à gauche, dans la hauteur du tambour (la boîte ne grandit pas).
    var accessory: AnyView? = nil
    /// Marge faible ou négative (JB, 23/10/2026) : toute la boîte devient orange ou rouge plein, tous les textes en noir.
    var alert = false
    /// Référence de l'altitude (« calé terrain », « alt. pression »…) : une altitude non calée se voit (revue résilience C2).
    var altRef = ""

    var body: some View {
        let ink: Color = alert ? .black : (dark ? Color.white : WG.brandInk)
        let label: Color = alert ? .black : PI.dim
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 0) {
                // libellés −10 % ; chiffres remontés de 16 pt vers leur libellé (la boîte garde sa taille)
                Text(tr("Marge sécu. (m)")).font(.system(size: 11.7, weight: .medium)).foregroundStyle(label)
                Text(marginText).font(WG.pfd(essential ? 64 : 48, .bold)).foregroundStyle(alert ? .black : marginColor)
                    .lineLimit(1).minimumScaleFactor(0.6)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(tr("Marge %@ mètres", marginText))
                    // centré sur la hauteur du tambour : marge et lecture d'altitude sur le même axe
                    .frame(height: essential ? nil : 78)
                    .overlay(alignment: .bottomLeading) { if !essential, let accessory { accessory.offset(y: 11) } }
                    .offset(y: essential ? 0 : -16)
            }
            if !essential {
                VStack(alignment: .leading, spacing: 2) {
                    Text(altRef.isEmpty ? tr("Alti.") : tr("Alti.") + " · " + altRef).font(.system(size: 11.7, weight: .medium)).foregroundStyle(label)
                        .lineLimit(1).minimumScaleFactor(0.8)
                    Drum(tape: tape, ink: ink, inAlertBox: alert).offset(y: -16)
                }
            }
        }
    }
}

/// Tambour d'altitude : centaines fixes + rouleau des deux derniers chiffres, qui va jusqu'au bord de la boîte et
/// s'estompe en s'éloignant de la lecture ; bloc rouge plein sous le minimum.
private struct Drum: View {
    let tape: AltitudeTapeState
    let ink: Color
    /// Dans une boîte déjà rouge ou orange : pas de second bloc rouge, chiffres noirs.
    var inAlertBox = false
    private let rowH: CGFloat = 24
    private let boxH: CGFloat = 78

    var body: some View {
        let red = tape.belowMinimum && !inAlertBox
        let fg: Color = red ? .white : ink
        HStack(spacing: 2) {
            if let d = tape.drum {
                // hachures blanches devant la lecture et filet vertical entre les deux séries de chiffres (référence Thales)
                Hatch().stroke(fg.opacity(0.85), lineWidth: 2.5).frame(width: 22, height: 16).padding(.trailing, 4)
                Text(d.high).font(WG.pfd(30, .bold)).foregroundStyle(fg)
                Rectangle().fill(fg.opacity(0.5)).frame(width: 1, height: boxH - 8).padding(.horizontal, 3)
                ZStack {
                    ForEach(0..<d.rows.count, id: \.self) { i in
                        let off = (CGFloat(i - Int(AltitudeDrum.companion.CENTER)) + CGFloat(d.roll)) * rowH
                        let fade = max(0, 1 - abs(off) / (boxH / 2 + rowH / 2))
                        Text(d.rows[i]).font(WG.pfd(abs(off) < rowH / 2 ? 26 : 21, abs(off) < rowH / 2 ? .bold : .semibold))
                            .foregroundStyle(fg.opacity(abs(off) < rowH / 2 ? 1 : 0.25 + 0.5 * fade))
                            .fixedSize().offset(y: off)
                    }
                }
                .frame(width: 44, height: boxH).clipped()
                .mask(LinearGradient(stops: [.init(color: .clear, location: 0), .init(color: .black, location: 0.3),
                                             .init(color: .black, location: 0.7), .init(color: .clear, location: 1)],
                                     startPoint: .top, endPoint: .bottom))
            } else {
                Text("– – –").font(WG.pfd(22)).foregroundStyle(inAlertBox ? .black : WG.bad)
            }
        }
        .padding(.horizontal, 8).frame(height: boxH)
        .background(red ? WG.alarmFill : .clear, in: RoundedRectangle(cornerRadius: 10))   // pas de boîte, sauf le bloc rouge d'alarme
        .lineLimit(1).fixedSize()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(tape.valid ? tr("Altitude") : tr("Altitude indisponible"))
    }
}

/// Hachures « //// » devant la lecture d'altitude.
private struct Hatch: Shape {
    func path(in r: CGRect) -> Path {
        var p = Path()
        for k in stride(from: r.minX - r.height, to: r.maxX, by: 6) {
            p.move(to: .init(x: max(k, r.minX), y: r.maxY - max(0, r.minX - k)))
            p.addLine(to: .init(x: min(k + r.height, r.maxX), y: r.minY + max(0, k + r.height - r.maxX)))
        }
        return p
    }
}

/// Vario au bord droit : chiffre en tête, barre étroite −3 … +4 au dégradé de la trace, traits courts sans texte,
/// index creux de la moyenne spirale, bouton son en pied. Aucun autre texte.
struct VarioRail: View {
    let ms: Double
    let valid: Bool
    let spiralMs: Double
    let dark: Bool
    let soundOn: Bool
    let onSound: () -> Void
    private let barH: CGFloat = 230

    var body: some View {
        let ink = dark ? Color.white : WG.brandInk
        VStack(alignment: .trailing, spacing: 6) {
            Text(VarioPalette.shared.valueText(ms: valid ? ms : .nan)).font(WG.pfd(28, .bold))
                .foregroundStyle(valid ? WG.varioText(ms, dark: dark) : PI.dim)
                .lineLimit(1).fixedSize()
                .padding(.horizontal, 6).padding(.vertical, 2)
                .background(PanelBackground(dark: dark), in: RoundedRectangle(cornerRadius: 8))
            ZStack(alignment: .bottomTrailing) {
                LinearGradient(stops: VarioPalette.shared.flashStops.map {
                    .init(color: WG.argb(($0.second as! KotlinLong).int64Value), location: VarioPalette.shared.fraction(ms: ($0.first as! KotlinDouble).doubleValue))
                }, startPoint: .bottom, endPoint: .top)
                // barre fine (JB : −40 %), terminée par des demi-cercles en haut et en bas
                .frame(width: 11, height: barH)
                .clipShape(Capsule())
                .overlay(Capsule().stroke(WG.casing, lineWidth: 1.5))
                // traits courts +4 / +2 / 0 / −2, le zéro plus long
                ForEach([4.0, 2.0, 0.0, -2.0], id: \.self) { v in
                    Rectangle().fill(v == 0 ? Color.white : WG.casing).frame(width: v == 0 ? 11 : 5, height: v == 0 ? 2 : 1.5)
                        .offset(x: 0, y: -CGFloat(VarioPalette.shared.fraction(ms: v)) * barH + 1)
                }
                if spiralMs.isFinite {
                    Triangle().stroke(ink, lineWidth: 2).frame(width: 10, height: 12)
                        .offset(x: -15, y: -CGFloat(VarioPalette.shared.fraction(ms: spiralMs)) * barH + 6)
                }
                if valid {
                    Capsule().fill(ink).frame(width: 23, height: 5).overlay(Capsule().stroke(WG.casing, lineWidth: 1.5))
                        .offset(x: 6, y: -CGFloat(VarioPalette.shared.fraction(ms: ms)) * barH + 2.5)
                        .animation(.easeOut(duration: 0.8), value: ms)   // curseur lissé ≈ 1 s ; chiffre et son bruts
                }
            }
            .frame(width: 40, height: barH, alignment: .bottomTrailing)
            Button { Haptic.tap(); onSound() } label: {
                Image(systemName: soundOn ? "speaker.wave.2.fill" : "speaker.slash").font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(soundOn ? ink : PI.dim)
                    .frame(width: 40, height: 40).background(PanelBackground(dark: dark), in: Circle())
            }
            .accessibilityLabel(soundOn ? tr("Couper le son du vario") : tr("Activer le son du vario"))
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(valid ? tr("Vario %@ mètres par seconde", VarioPalette.shared.valueText(ms: ms)) : tr("Vario indisponible"))
    }
}

/// Index creux pointant vers la barre (moyenne spirale).
private struct Triangle: Shape {
    func path(in r: CGRect) -> Path {
        var p = Path(); p.move(to: .init(x: r.maxX, y: r.midY)); p.addLine(to: .init(x: r.minX, y: r.minY)); p.addLine(to: .init(x: r.minX, y: r.maxY)); p.closeSubpath(); return p
    }
}
