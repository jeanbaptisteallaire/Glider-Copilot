import SwiftUI

/// Illustration du dépôt Android (res/drawable-nodpi), chargée depuis le bundle.
func wgImage(_ name: String) -> Image? {
    Bundle.main.path(forResource: name, ofType: "webp").flatMap(UIImage.init(contentsOfFile:)).map(Image.init(uiImage:))
}

enum Legal {
    // mêmes adresses que GcLegal (core/designsystem/Legal.kt), pages servies par GitHub Pages (site/)
    static let base = "https://jeanbaptisteallaire.github.io/Glider-Copilot"
    static let privacy = URL(string: "\(base)/confidentialite.html")!
    static let terms = URL(string: "\(base)/conditions.html")!
}

/// Avertissement de sécurité du premier lancement (même texte que Disclaimer d'Android, AppRoot.kt ; sans la mention OGN :
/// pas de trafic dans l'édition Lite iOS). Version enregistrée : un nouveau texte se fait accepter à nouveau.
struct DisclaimerView: View {
    static let version = 1
    let onAccept: () -> Void

    var body: some View {
        ZStack(alignment: .bottom) {
            LanguageSwitch().frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing).padding(.trailing, 16).padding(.top, 8).zIndex(2)
            LinearGradient(colors: [WG.sky.opacity(0.55), WG.brandPaper], startPoint: .top, endPoint: .bottom).ignoresSafeArea()
            VStack {
                wgImage("wg_logo")?.resizable().scaledToFit().frame(maxWidth: 220).padding(.top, 60)
                Spacer()
            }
            VStack(alignment: .leading, spacing: 12) {
                Text("WIND GLIDER").font(Font.system(size: 22).weight(.medium)).tracking(3).foregroundStyle(WG.brandInk)
                Text(tr("Aide secondaire au vol à voile. Ne remplace ni le vario, ni le calculateur, ni le FLARM, ni une navigation certifiée. Instruments de bord et veille extérieure priment. Le pilote reste seul responsable."))
                    .font(WG.body).foregroundStyle(WG.brandInk)
                Text(tr("Données météo : PRECOG — precog-api.com (Source : Météo-France). Relief : Copernicus GLO-30. Terrains : openAIP. Estimations thermiques calculées dans l'app : à confronter au ciel."))
                    .font(WG.footnote).foregroundStyle(WG.brandInk.opacity(0.7))
                HStack(spacing: 16) {
                    Link(tr("Conditions"), destination: Legal.terms)
                    Link(tr("Confidentialité"), destination: Legal.privacy)
                }
                .font(WG.footnote).tint(WG.route)
                Button(action: onAccept) {
                    Text(tr("J'ai compris")).font(WG.headline).frame(maxWidth: .infinity, minHeight: 52)
                }
                .background(WG.brandInk, in: RoundedRectangle(cornerRadius: 14))
                .foregroundStyle(.white)
            }
            .padding(22)
            .background(WG.card, in: RoundedRectangle(cornerRadius: 24))
            .padding(20)
        }
    }
}

/// « Avant de voler », une fois par lancement avant Pilotage (même texte que PilotNoticeScreen d'Android, Guide.kt).
struct PilotNoticeView: View {
    let onOk: () -> Void

    var body: some View {
        ZStack(alignment: .bottom) {
            LanguageSwitch().frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing).padding(.trailing, 16).padding(.top, 8).zIndex(2)
            Color(red: 0x3B / 255, green: 0x3F / 255, blue: 0x45 / 255).ignoresSafeArea()
            VStack {
                wgImage("wg_cockpit")?.resizable().scaledToFit()
                Spacer()
            }
            .ignoresSafeArea(edges: .top)
            VStack(alignment: .leading, spacing: 10) {
                Text(tr("Avant de voler")).font(WG.title).foregroundStyle(WG.brandInk)
                line(tr("Fixez solidement votre téléphone dans le cockpit, sans gêner les commandes ni la vue dehors."))
                line(tr("Utilisez Wind Glider avec l'accord de votre instructeur."))
                line(tr("Aide secondaire : instruments de bord et veille extérieure d'abord."))
                Button(action: onOk) { Text(tr("Compris")).font(WG.headline).frame(maxWidth: .infinity, minHeight: 52) }
                    .background(WG.brandInk, in: RoundedRectangle(cornerRadius: 14))
                    .foregroundStyle(.white)
                    .padding(.top, 4)
            }
            .padding(22)
            .background(WG.card, in: RoundedRectangle(cornerRadius: 24))
            .padding(.horizontal, 20).padding(.bottom, 24)
        }
    }

    private func line(_ t: String) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Circle().fill(WG.sky).frame(width: 6, height: 6).padding(.top, 8)
            Text(t).font(WG.body).foregroundStyle(WG.brandInk)
        }
    }
}
