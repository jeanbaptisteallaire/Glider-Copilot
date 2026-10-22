import SwiftUI
import WindGliderShared

/// Langage visuel de LIGHT PFD dans Pilotage (JB, 22/10/2026) : ruban d'altitude « ouvert » à tambour au service de la
/// marge de sécurité, barre vario verticale −3 … +4. Calculs et textes : `AltitudeTape` / `VarioPalette` (Kotlin commun).
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
    static func selected(dark: Bool) -> Color { dark ? rgb2(0x1CB4FF) : rgb2(0x00739E) }
    static let alarmFill = rgb2(0xD4141C)
    private static func rgb2(_ hex: UInt32) -> Color {
        Color(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }
}

/// Ruban d'altitude façon Thales : filet vertical, traits tous les 50 m, minimum hachuré cyan, tambour à deux derniers
/// chiffres qui défilent, bloc rouge plein sous le minimum, écart affiché dessous. Tirets si l'altitude manque.
struct AltitudeTapeView: View {
    let state: AltitudeTapeState
    let altM: Double
    let dark: Bool
    private let height: CGFloat = 220
    private let width: CGFloat = 104

    var body: some View {
        let ink = dark ? Color.white : WG.brandInk
        ZStack(alignment: .leading) {
            Canvas { ctx, size in
                let midY = size.height / 2
                let x = size.width - 10
                // filet
                var rail = Path(); rail.move(to: .init(x: x, y: 6)); rail.addLine(to: .init(x: x, y: size.height - 6))
                ctx.stroke(rail, with: .color(ink.opacity(0.75)), lineWidth: 2)
                guard state.valid else { return }
                for t in state.ticksM {
                    let y = midY - CGFloat(AltitudeTape.shared.position(altM: altM, atM: Double(t.intValue))) * (size.height / 2 - 6)
                    var p = Path(); p.move(to: .init(x: x, y: y)); p.addLine(to: .init(x: x - (t.intValue % 100 == 0 ? 12 : 7), y: y))
                    ctx.stroke(p, with: .color(ink.opacity(0.75)), lineWidth: 2)
                }
                if let m = state.minimum {
                    let y = midY - CGFloat(m.position) * (size.height / 2 - 6)
                    let cyan = WG.selected(dark: dark)
                    // sous le minimum : trait rouge le long du filet
                    var red = Path(); red.move(to: .init(x: x, y: max(y, 6))); red.addLine(to: .init(x: x, y: size.height - 6))
                    ctx.stroke(red, with: .color(WG.bad), lineWidth: 4)
                    // repère hachuré « //// » du minimum
                    let box = CGRect(x: x + 1, y: y - 5, width: 9, height: m.clamped ? 6 : 10)
                    ctx.stroke(Path(box), with: .color(cyan), lineWidth: 1.5)
                    for k in stride(from: -10.0, to: 12, by: 4) {
                        var h = Path(); h.move(to: .init(x: box.minX + k, y: box.maxY)); h.addLine(to: .init(x: box.minX + k + 10, y: box.minY))
                        ctx.clip(to: Path(box)); ctx.stroke(h, with: .color(cyan), lineWidth: 1.5)
                    }
                }
            }
            .frame(width: width, height: height)
            VStack(spacing: 2) {
                drum(ink: ink)
                if !state.gapText.isEmpty {
                    Text(state.gapText).font(PI.number(20)).foregroundStyle(state.belowMinimum ? WG.bad : WG.selected(dark: dark))
                        .padding(.horizontal, 4).background(PI.box.opacity(0.85), in: RoundedRectangle(cornerRadius: 6))
                        .lineLimit(1).fixedSize()
                }
            }
            .offset(y: state.gapText.isEmpty ? 0 : 12)
        }
        .frame(width: width, height: height)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(state.valid ? tr("Altitude %@ mètres, marge %@", "\(Int(altM.rounded()))", state.gapText) : tr("Altitude indisponible"))
    }

    /// Boîte de lecture : centaines fixes + rouleau des deux derniers chiffres, voisins estompés.
    private func drum(ink: Color) -> some View {
        let red = state.belowMinimum
        let fg: Color = red ? .white : ink
        let rowH: CGFloat = 30
        return HStack(spacing: 0) {
            if let d = state.drum {
                Text(d.high).font(PI.number(34)).foregroundStyle(fg).lineLimit(1).fixedSize()
                ZStack {
                    ForEach(0..<d.rows.count, id: \.self) { i in
                        let off = (CGFloat(i - Int(AltitudeDrum.companion.CENTER)) + CGFloat(d.roll)) * rowH
                        Text(d.rows[i]).font(PI.number(i == Int(AltitudeDrum.companion.CENTER) ? 26 : 20))
                            .foregroundStyle(fg.opacity(abs(off) < rowH / 2 ? 1 : 0.45))
                            .offset(y: off)
                    }
                }
                .frame(width: 36, height: rowH * 1.25).clipped()
            } else {
                Text("– – –").font(PI.number(24)).foregroundStyle(WG.bad)
            }
        }
        .padding(.horizontal, 6).frame(height: 54)
        .background(red ? WG.alarmFill : PI.box.opacity(PI.dark ? 1 : 0.92), in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(red ? WG.alarmFill : ink.opacity(0.6), lineWidth: 2))
    }
}

/// Vario en barre verticale : gros chiffre au-dessus, échelle −3 … +4 au dégradé de la trace, curseur liseré sombre.
struct VarioColumn: View {
    let ms: Double
    let text: String
    let valid: Bool
    let dark: Bool
    private let barH: CGFloat = 150

    var body: some View {
        let ink = dark ? Color.white : WG.brandInk
        VStack(alignment: .leading, spacing: 4) {
            Text(VarioPalette.shared.valueText(ms: valid ? ms : .nan)).font(PI.number(34))
                .foregroundStyle(valid ? WG.varioText(ms, dark: dark) : PI.dim)
                .lineLimit(1).fixedSize()
                .padding(.horizontal, 6).background(PI.box.opacity(PI.dark ? 1 : 0.92), in: RoundedRectangle(cornerRadius: 10))
            HStack(alignment: .top, spacing: 4) {
                // graduations chiffrées
                VStack(spacing: 0) {
                    ForEach(VarioPalette.shared.ticks.map { $0.intValue }, id: \.self) { v in
                        Text(v % 2 == 0 ? VarioPalette.shared.tickLabel(v: Int32(v)) : " ").font(PI.number(13)).foregroundStyle(ink)
                            .frame(height: barH / 7, alignment: .center)
                    }
                }
                .frame(height: barH + barH / 7).offset(y: -barH / 14)
                ZStack(alignment: .bottom) {
                    LinearGradient(stops: VarioPalette.shared.flashStops.map {
                        .init(color: WG.argb(($0.second as! KotlinLong).int64Value), location: VarioPalette.shared.fraction(ms: ($0.first as! KotlinDouble).doubleValue))
                    }, startPoint: .bottom, endPoint: .top)
                    .frame(width: 18, height: barH)
                    .overlay(Rectangle().stroke(WG.casing, lineWidth: 1.5))
                    // zéro : trait blanc continu
                    Rectangle().fill(.white).frame(width: 18, height: 2).offset(y: -CGFloat(VarioPalette.shared.fraction(ms: 0)) * barH + 1)
                    if valid {
                        // curseur : triangle + trait, liseré sombre ; en butée au-delà de l'échelle
                        Rectangle().fill(ink).frame(width: 26, height: 5).overlay(Rectangle().stroke(WG.casing, lineWidth: 1.5))
                            .offset(y: -CGFloat(VarioPalette.shared.fraction(ms: ms)) * barH + 2)
                            .animation(.easeOut(duration: 0.8), value: ms)   // curseur lissé ≈ 1 s ; le chiffre et le son restent bruts
                    }
                }
                .frame(width: 26, height: barH)
            }
            .padding(6).background(PI.box.opacity(PI.dark ? 0.85 : 0.8), in: RoundedRectangle(cornerRadius: 12))
            Text("m/s").font(WG.caption).foregroundStyle(PI.dim)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(valid ? tr("Vario %@ mètres par seconde", text) : tr("Vario indisponible"))
    }
}
