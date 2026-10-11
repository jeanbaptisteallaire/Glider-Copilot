import SwiftUI
import WindGliderShared

/// Menu d'accueil Wind Glider (HomeMenuScreen.kt, V20) : papier chaud, logo, case « Tutoriel », carte Prévol avec la vraie
/// journée du terrain, cartes illustrées Pilotage et Mes vols.
struct HomeMenuView: View {
    @EnvironmentObject var app: AppModel
    @AppStorage("guideLanguage") private var lang = Locale.current.language.languageCode?.identifier == "fr" ? "fr" : "en"
    let open: (AppTab) -> Void

    var body: some View {
        let fr = lang == "fr"
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 12) {
                    wgImage("wg_logo")?.resizable().scaledToFill().frame(width: 44, height: 44).clipShape(Circle())
                    Text("WIND GLIDER").font(Font.custom("Inter", size: 20).weight(.medium)).tracking(3).foregroundStyle(WG.brandInk)
                }
                .padding(.leading, 4).padding(.top, 14)
                Button { open(.tuto) } label: {
                    HStack(spacing: 10) {
                        Image(systemName: "graduationcap").foregroundStyle(WG.brandInk)
                        Text(fr ? "Tutoriel" : "Tutorial").font(Font.custom("Inter", size: 17).weight(.semibold)).foregroundStyle(WG.brandInk)
                        Spacer()
                        Image(systemName: "chevron.right").font(.footnote).foregroundStyle(WG.dim)
                    }
                    .padding(.horizontal, 18).padding(.vertical, 11)
                    .background(WG.card, in: RoundedRectangle(cornerRadius: 14))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Ouvrir le tutoriel")
                Button { open(.prevol) } label: {
                    VStack(alignment: .leading, spacing: 12) {
                        title(fr ? "Prévol & météo" : "Pre-flight & weather", WG.brandInk)
                        if case .loaded(let day) = app.weather {
                            CeilingChart(hours: day.hours, selected: -1, onSelect: { _ in open(.prevol) })
                                .frame(height: 150).allowsHitTesting(false)
                        } else {
                            Text(app.club.map { "\($0.airfieldIcao ?? $0.name) · " } ?? "").font(WG.subhead).foregroundStyle(WG.dim)
                                + Text(fr ? "météo du jour" : "today's weather").font(WG.subhead).foregroundStyle(WG.dim)
                        }
                    }
                    .padding(.horizontal, 20).padding(.vertical, 18)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(WG.card, in: RoundedRectangle(cornerRadius: 22))
                    .shadow(color: WG.brandInk.opacity(0.1), radius: 8, y: 2)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Ouvrir Prévol")
                photoCard(fr ? "Pilotage" : "Flight",
                          fr ? "Les données utiles, à leur place dans le cockpit." : "The data you need, right where it belongs in the cockpit.",
                          image: "wg_cockpit", focusY: 0.56, a11y: "Ouvrir Pilotage") { open(.pilotage) }
                photoCard(fr ? "Mes vols" : "My flights",
                          fr ? "Retrouvez et revivez vos trajectoires." : "Find and relive your flights.",
                          image: "wg_flights_header", focusY: 0.5, a11y: "Ouvrir Mes vols") { open(.mesVols) }
            }
            .padding(.horizontal, 16).padding(.bottom, 16)
        }
        .background(WG.brandPaper)
        // la carte Prévol montre la vraie journée du terrain (PrevolDayPreview d'Android)
        .task { if case .idle = app.weather { await app.loadWeather() } }
    }

    private func title(_ t: String, _ c: Color) -> some View {
        Text(t).font(Font.custom("Inter", size: 26).weight(.bold)).tracking(-0.3).foregroundStyle(c)
    }

    /// Carte illustrée : titre et sous-titre blancs sur le ciel, flèche en bas à droite.
    private func photoCard(_ t: String, _ sub: String, image: String, focusY: CGFloat, a11y: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            GeometryReader { g in
                ZStack(alignment: .topLeading) {
                    if let ui = Bundle.main.path(forResource: image, ofType: "webp").flatMap(UIImage.init(contentsOfFile:)) {
                        // recadrage façon BiasAlignment : [focusY] place la fenêtre dans la hauteur en trop de l'image
                        let h = g.size.width * ui.size.height / max(ui.size.width, 1)
                        let excess = max(0, h - g.size.height)
                        Image(uiImage: ui).resizable().frame(width: g.size.width, height: max(h, g.size.height))
                            .offset(y: -excess * focusY)
                            .frame(width: g.size.width, height: g.size.height, alignment: .top).clipped()
                    }
                    LinearGradient(colors: [Color(red: 0.06, green: 0.16, blue: 0.27).opacity(0.4), .clear], startPoint: .top, endPoint: .bottom).frame(height: 110)
                    VStack(alignment: .leading, spacing: 4) {
                        title(t, .white).shadow(color: .black.opacity(0.4), radius: 5)
                        Text(sub).font(Font.custom("Inter", size: 15)).foregroundStyle(.white).shadow(color: .black.opacity(0.4), radius: 4)
                            .frame(width: g.size.width * 0.62, alignment: .leading)
                    }
                    .padding(.horizontal, 20).padding(.vertical, 18)
                    Image(systemName: "chevron.right").font(.system(size: 14, weight: .semibold)).foregroundStyle(WG.brandInk)
                        .frame(width: 34, height: 34).background(Color.white.opacity(0.9), in: Circle())
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing).padding(14)
                }
            }
            .aspectRatio(1.6, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: 22))
            .shadow(color: WG.brandInk.opacity(0.1), radius: 8, y: 2)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(a11y)
    }
}
