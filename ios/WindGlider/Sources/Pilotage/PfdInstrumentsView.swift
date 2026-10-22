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

/// Bandeau sécurité (haut gauche) : la marge en très gros, l'altitude en tambour à côté, et dessous un ruban
/// horizontal « ouvert » (±250 m) où le minimum est hachuré cyan et la zone sous le minimum rouge.
/// Remplace les lignes SÉCU / ALT : la marge et le hachuré disent la même chose sans texte.
struct SafetyBand: View {
    let marginText: String
    let marginColor: Color
    let tape: AltitudeTapeState
    let altM: Double
    let dark: Bool
    var essential = false

    var body: some View {
        let ink = dark ? Color.white : WG.brandInk
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .center, spacing: 10) {
                Text(marginText).font(WG.pfd(essential ? 64 : 48, .bold)).foregroundStyle(marginColor)
                    .lineLimit(1).minimumScaleFactor(0.6)
                if !essential { Drum(tape: tape, ink: ink) }
            }
            if !essential {
                HorizontalTape(tape: tape, altM: altM, ink: ink, dark: dark).frame(height: 18)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(tr("Marge %@", marginText))
    }
}

/// Tambour compact : centaines fixes + rouleau des deux derniers chiffres ; bloc rouge plein sous le minimum.
private struct Drum: View {
    let tape: AltitudeTapeState
    let ink: Color
    private let rowH: CGFloat = 22

    var body: some View {
        let red = tape.belowMinimum
        let fg: Color = red ? .white : ink
        HStack(spacing: 0) {
            if let d = tape.drum {
                Text(d.high).font(WG.pfd(24)).foregroundStyle(fg)
                ZStack {
                    ForEach(0..<d.rows.count, id: \.self) { i in
                        let off = (CGFloat(i - Int(AltitudeDrum.companion.CENTER)) + CGFloat(d.roll)) * rowH
                        Text(d.rows[i]).font(WG.pfd(i == Int(AltitudeDrum.companion.CENTER) ? 19 : 16))
                            .foregroundStyle(fg.opacity(abs(off) < rowH / 2 ? 1 : 0.4))
                            .offset(y: off)
                    }
                }
                .frame(width: 26, height: rowH).clipped()
            } else {
                Text("– – –").font(WG.pfd(20)).foregroundStyle(WG.bad)
            }
        }
        .padding(.horizontal, 6).frame(height: 36)
        .background(red ? WG.alarmFill : .clear, in: RoundedRectangle(cornerRadius: 8))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(red ? WG.alarmFill : ink.opacity(0.5), lineWidth: 1.5))
        .lineLimit(1).fixedSize()
    }
}

/// Ruban horizontal : altitude actuelle au centre (index), plus haut à droite ; minimum hachuré cyan, rouge dessous.
private struct HorizontalTape: View {
    let tape: AltitudeTapeState
    let altM: Double
    let ink: Color
    let dark: Bool

    var body: some View {
        Canvas { ctx, size in
            let midX = size.width / 2, y = size.height - 4, half = size.width / 2 - 4
            var rail = Path(); rail.move(to: .init(x: 4, y: y)); rail.addLine(to: .init(x: size.width - 4, y: y))
            ctx.stroke(rail, with: .color(ink.opacity(0.6)), lineWidth: 2)
            guard tape.valid else { return }
            for t in tape.ticksM {
                let x = midX + CGFloat(AltitudeTape.shared.position(altM: altM, atM: Double(t.intValue))) * half
                var p = Path(); p.move(to: .init(x: x, y: y)); p.addLine(to: .init(x: x, y: y - (t.intValue % 100 == 0 ? 8 : 5)))
                ctx.stroke(p, with: .color(ink.opacity(0.6)), lineWidth: 1.5)
            }
            if let m = tape.minimum {
                let x = midX + CGFloat(m.position) * half
                var red = Path(); red.move(to: .init(x: 4, y: y)); red.addLine(to: .init(x: max(4, x), y: y))
                ctx.stroke(red, with: .color(WG.bad), lineWidth: 4)
                let cyan = WG.selected(dark: dark)
                let box = CGRect(x: x - 5, y: y - 12, width: 10, height: 10)
                ctx.stroke(Path(box), with: .color(cyan), lineWidth: 1.5)
                var c = ctx; c.clip(to: Path(box))
                for k in stride(from: -10.0, to: 12, by: 4) {
                    var h = Path(); h.move(to: .init(x: box.minX + k, y: box.maxY)); h.addLine(to: .init(x: box.minX + k + 10, y: box.minY))
                    c.stroke(h, with: .color(cyan), lineWidth: 1.5)
                }
            }
            // index de l'altitude actuelle
            var idx = Path(); idx.move(to: .init(x: midX, y: y - 1)); idx.addLine(to: .init(x: midX - 6, y: y - 12)); idx.addLine(to: .init(x: midX + 6, y: y - 12)); idx.closeSubpath()
            ctx.fill(idx, with: .color(ink))
        }
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
                .frame(width: 18, height: barH)
                .overlay(Rectangle().stroke(WG.casing, lineWidth: 1.5))
                // traits courts +4 / +2 / 0 / −2, le zéro plus long
                ForEach([4.0, 2.0, 0.0, -2.0], id: \.self) { v in
                    Rectangle().fill(v == 0 ? Color.white : WG.casing).frame(width: v == 0 ? 18 : 8, height: v == 0 ? 2 : 1.5)
                        .offset(x: 0, y: -CGFloat(VarioPalette.shared.fraction(ms: v)) * barH + 1)
                }
                if spiralMs.isFinite {
                    Triangle().stroke(ink, lineWidth: 2).frame(width: 10, height: 12)
                        .offset(x: -22, y: -CGFloat(VarioPalette.shared.fraction(ms: spiralMs)) * barH + 6)
                }
                if valid {
                    Rectangle().fill(ink).frame(width: 30, height: 5).overlay(Rectangle().stroke(WG.casing, lineWidth: 1.5))
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
