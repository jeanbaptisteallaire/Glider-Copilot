import Charts
import MapKit
import SwiftUI
import WindGliderShared

/// Onglet Pilotage (édition Lite), d'après l'écran Android : marge de sécurité, distance et finesse, terrain et cap,
/// coupe vers le terrain, carte (trace colorée par le vario, route, vent, zoom spirale), vario et moyennes, REC.
/// Fond blanc (V18.7). Calculs : moteur commun (FlightEngineCore + SafetyEngine).
/// Coupe et vario se masquent (interrupteurs orange) pour agrandir la carte ; masqués, ils restent en pastille sur la carte.
/// Palette de Pilotage : mode clair (charte blanche) ou mode sombre (V3, inspiré de l'avionique : boîtes noires,
/// textes blancs un peu plus gras, vert et violet fluo très clairs, vignettage noir sur les bords).
enum PI {
    static var dark: Bool { UserDefaults.standard.bool(forKey: "pilot.dark") }
    static var ink: Color { dark ? .white : WG.brandInk }
    static var dim: Color { dark ? Color.white.opacity(0.62) : WG.dim }
    static var box: Color { dark ? .black : WG.card }
    static var good: Color { dark ? Color(red: 0.80, green: 1.0, blue: 0.70) : WG.good }      // vert fluo très clair
    static var route: Color { dark ? Color(red: 0.86, green: 0.72, blue: 1.0) : WG.route }    // violet fluo très clair
    static func number(_ size: CGFloat) -> Font { Font.custom("Inter", size: size).weight(dark ? .bold : .semibold).monospacedDigit() }
}

struct PilotView: View {
    @EnvironmentObject var app: AppModel
    @State private var choosingField = false
    @AppStorage("pilot.showProfile") private var showProfile = true
    @AppStorage("pilot.showVario") private var showVario = true
    @State private var topInset: CGFloat = 0
    @State private var bottomInset: CGFloat = 0
    @AppStorage("pilot.dark") private var dark = false

    var body: some View {
        let p = app.pilot
        VStack(spacing: 0) {
            // V3 : la carte se déroule derrière l'en-tête (quatre boîtes blanches à 90 %) et la coupe
            ZStack(alignment: .top) {
                // V3 : carte fluide (60 images/s, planeur prédit) ; -mapV2 au lancement = carte de la V2
                if ProcessInfo.processInfo.arguments.contains("-mapV2") || UserDefaults.standard.bool(forKey: "fluidMapOff") {
                    FlightMapView(p: p, airspaces: app.airspaces)
                } else {
                    FluidMapView(p: p, airspaces: app.showAirspaces ? app.airspaces : [], traffic: app.trafficSnap, predictor: app.predictor, bottomInset: bottomInset, dark: dark)
                        // la carte passe toujours sous le vario et la barre d'onglets : ouvrir ou fermer un panneau
                        // ne redimensionne jamais la carte (planeur immobile à l'écran)
                        .ignoresSafeArea(.container, edges: .bottom)
                }
                // tout en bas au centre : REC au-dessus du chrono du vol, dans la même petite boîte
                Button { app.toggleRecording() } label: {
                    VStack(spacing: 1) {
                        HStack(spacing: 4) {
                            Circle().fill(p.recording ? .white : WG.bad).frame(width: 7, height: 7)
                            Text("REC").font(WG.caption2.weight(.bold))
                        }
                        .foregroundStyle(p.recording ? .white : WG.bad)
                        Text(p.flightTimeText).font(WG.caption.monospacedDigit()).foregroundStyle(p.recording ? .white : PI.ink)
                    }
                    .padding(.horizontal, 10).padding(.vertical, 4)
                    .background(p.recording ? WG.bad : PI.box.opacity(0.92), in: RoundedRectangle(cornerRadius: 10))
                    .shadow(color: .black.opacity(0.15), radius: 2)
                }
                .accessibilityLabel(p.recording ? "Arrêter l'enregistrement" : "Démarrer l'enregistrement")
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom).padding(.bottom, 2)

                // mode sombre : vignettage noir très court sur les bords (les boîtes noires s'y fondent)
                if dark {
                    Rectangle().strokeBorder(Color.black, lineWidth: 26).blur(radius: 14)
                        .padding(-8).ignoresSafeArea().allowsHitTesting(false)
                    // sous les boîtes du haut : fond noir qui se fond dans la carte
                    LinearGradient(stops: [.init(color: .black, location: 0), .init(color: .black.opacity(0.85), location: 0.55),
                                           .init(color: .clear, location: 1)], startPoint: .top, endPoint: .bottom)
                        .frame(height: max(topInset, 150) + 30).frame(maxHeight: .infinity, alignment: .top)
                        .allowsHitTesting(false)
                }
                MapOverlay(p: p, showProfile: $showProfile, showVario: $showVario).padding(.top, topInset).padding(.bottom, bottomInset)
                VStack(spacing: 6) {
                    header(p)
                    // interrupteur « relief » : toujours au même endroit, coin haut droit de la boîte de coupe
                    ZStack(alignment: .topTrailing) {
                    if showProfile {
                        VStack(spacing: 0) {
                            ProfileStrip(p: p).frame(height: 92).padding(.bottom, 10)
                            Text(p.fieldElevText).font(WG.caption).foregroundStyle(PI.dim)
                                .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 16).padding(.bottom, 2)
                        }
                        .background(PI.box.opacity(PI.dark ? 1 : 0.9), in: RoundedRectangle(cornerRadius: 14))
                        .padding(.horizontal, 8)
                    } else {
                        Color.clear.frame(height: 30)
                    }
                    PanelToggle(isOn: $showProfile, label: "relief", dark: true, scale: 1.2).padding(.trailing, 10).padding(.top, 2)
                    }
                }
                .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { topInset = $0 }
                VStack(alignment: .leading, spacing: 0) {
                // vario : même traitement que la coupe ; interrupteur fixe dans le coin bas droit de la boîte
                ZStack(alignment: .bottomTrailing) {
                    if showVario {
                        varioBar(p)
                            .background(PI.box.opacity(PI.dark ? 1 : 0.9), in: RoundedRectangle(cornerRadius: 14))
                            .padding(.horizontal, 8)
                    } else {
                        Color.clear.frame(height: 30)
                    }
                    PanelToggle(isOn: $showVario, label: "vario", dark: true, scale: 1.2).padding(.trailing, 10).padding(.bottom, 2)
                }
                .padding(.bottom, 4)
                }
                .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { bottomInset = $0 }
                .frame(maxHeight: .infinity, alignment: .bottom)
            }
        }
        .background(PI.box)
        .fontWeight(dark ? .medium : nil)
        .statusBarHidden(dark)   // mode sombre : heure et batterie noires illisibles sur le bandeau noir
        .toolbarColorScheme(dark ? .dark : .light, for: .tabBar)
        .toolbarBackground(dark ? .visible : .automatic, for: .tabBar)
        .toolbarBackground(dark ? Color.black : Color.clear, for: .tabBar)
        .animation(.easeInOut(duration: 0.25), value: dark)
        .sheet(isPresented: $choosingField) { FieldPicker().environmentObject(app).presentationDetents([.medium]) }
        .onAppear { UIApplication.shared.isIdleTimerDisabled = true }
        .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
    }

    // MARK: en-tête : marge, distance, finesse

    private func header(_ p: PilotUi) -> some View {
        let marginColor = p.marginKnown ? (p.marginM >= 0 ? PI.good : WG.bad) : PI.dim
        let f10 = app.finesse == 10
        return HStack(alignment: .top, spacing: 6) {
            // gauche : marge (sans unité, évidente), SÉCU, ALT
            VStack(alignment: .leading, spacing: 0) {
                Text("Marge sécu.").font(WG.footnote).foregroundStyle(marginColor)
                Text(p.marginText).font(PI.number(40)).foregroundStyle(marginColor)
                    .lineLimit(1).minimumScaleFactor(0.5)
                Spacer(minLength: 2)
                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    Text("SÉCU").font(WG.caption).foregroundStyle(PI.dim); Text(p.secuText).font(PI.number(16))
                }
                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    Text("ALT").font(WG.caption).foregroundStyle(PI.dim); Text(p.altText).font(PI.number(16))
                }
            }
            .lineLimit(1)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .headerBox()
            // centre : finesse (haut) et cap GPS (bas)
            VStack(spacing: 6) {
                VStack(spacing: 4) {
                    HStack(spacing: 4) {
                        // F20 : « Fin. +300 m » (tour de piste) ; F10 : « Finesse », sans les 300 m (JB, 12/10/2026)
                        Text(f10 ? "Finesse" : "Fin.").font(WG.footnote)
                        if !f10 { Text("+300 m").font(WG.caption2).foregroundStyle(PI.dim) }
                    }
                    .lineLimit(1).fixedSize()
                    // un seul bouton : touche = bascule finesse 10 ⇄ 20
                    Button { app.finesse = f10 ? 20 : 10 } label: {
                        HStack(alignment: .firstTextBaseline, spacing: 0) {
                            Text("F").font(WG.caption2)
                            Text("\(app.finesse)").font(PI.number(20))
                        }
                        .lineLimit(1).fixedSize()
                        .padding(.horizontal, 10).padding(.vertical, 5)
                        .background(WG.sky.opacity(0.15), in: RoundedRectangle(cornerRadius: 8))
                        .foregroundStyle(WG.sky)
                    }
                }
                .frame(maxWidth: .infinity).headerBox()
                VStack(spacing: 0) {
                    Text("CAP").font(WG.footnote)
                    Text(p.hasPosition ? "\(Int(p.trackDeg.rounded()) % 360)°" : "—").font(PI.number(20)).monospacedDigit()
                }
                .lineLimit(1)
                .frame(maxWidth: .infinity).headerBox()
                .accessibilityElement(children: .combine)
            }
            .frame(width: 96)
            // droite : distance, terrain, cap vers le terrain (même taille que le cap GPS)
            Button { choosingField = true } label: {
                VStack(alignment: .trailing, spacing: 0) {
                    Text("Dist. ter.").font(WG.footnote).foregroundStyle(PI.dim)
                    HStack(alignment: .firstTextBaseline, spacing: 2) {
                        Text(p.distanceText).font(PI.number(32)).minimumScaleFactor(0.6)
                        Text("km").font(WG.caption)
                    }
                    Spacer(minLength: 2)
                    HStack(spacing: 4) {
                        Text(p.fieldCode).font(WG.headline).foregroundStyle(PI.ink)
                        Image(systemName: "chevron.down").font(.caption2).foregroundStyle(PI.route)
                    }
                    Text(p.bearingText).font(PI.number(20)).foregroundStyle(PI.route)
                }
                .lineLimit(1)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .trailing)
                .headerBox()
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Choisir le terrain de référence")
        }
        .fixedSize(horizontal: false, vertical: true)
        .padding(.horizontal, 8).padding(.top, 4)
        .foregroundStyle(PI.ink)
    }

    // MARK: vario

    private func varioBar(_ p: PilotUi) -> some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading) {
                Text(p.varioLabel).font(WG.footnote).foregroundStyle(PI.dim)
                Text(p.varioText).font(PI.number(40)).foregroundStyle(WG.vario(p.varioMs)).lineLimit(1).minimumScaleFactor(0.6)
            }
            .frame(width: 120, alignment: .leading)
            VStack(spacing: 6) {
                VarioScale(value: p.varioMs).frame(height: 10)
                HStack {
                    avg("Spirale", p.spiralText); avg("Pompe", p.thermalText); avg("Jour", p.dayText)
                }
            }
            Button { app.soundOn.toggle() } label: {
                Image(systemName: app.soundOn ? "speaker.wave.2" : "speaker.slash").font(.body)
                    .frame(width: 40, height: 40).overlay(RoundedRectangle(cornerRadius: 10).stroke(WG.line))
            }
            .accessibilityLabel(app.soundOn ? "Couper le son du vario" : "Activer le son du vario")
            .frame(maxHeight: .infinity, alignment: .top)
        }
        .fixedSize(horizontal: false, vertical: true)
        .padding(.horizontal, 14).padding(.top, 10).padding(.bottom, 10)
        .foregroundStyle(PI.ink)
    }

    private func avg(_ l: String, _ v: String) -> some View {
        VStack(alignment: .leading) { Text(l).font(WG.caption).foregroundStyle(PI.dim); Text(v).font(PI.number(17)) }
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Petit interrupteur orange d'un panneau (coupe, vario).
private extension View {
    /// Boîte de l'en-tête de Pilotage (V3) : fond blanc à 90 %, coins légèrement arrondis, carte visible autour.
    func headerBox() -> some View {
        padding(.horizontal, 10).padding(.vertical, 6)
            .background(PI.box.opacity(PI.dark ? 1 : 0.9), in: RoundedRectangle(cornerRadius: 12))
    }
}

struct PanelToggle: View {
    @AppStorage("pilot.dark") private var darkMode = false   // redessine au changement de mode
    @Binding var isOn: Bool
    let label: String
    var dark = false
    var scale: CGFloat = 1

    var body: some View {
        Toggle(isOn: $isOn) {
            Text(label).font(Font.custom("Inter", size: 11 * scale)).foregroundStyle(dark ? .white : PI.dim)
        }
            .toggleStyle(MiniSwitch(scale: scale))
            .padding(.horizontal, dark ? 4 : 0)
            .background(dark ? Color.black.opacity(0.85) : .clear, in: Capsule())
            .accessibilityLabel(isOn ? "Masquer \(label)" : "Afficher \(label)")
    }
}

/// Interrupteur réduit (≈ 26 × 15 pt), orange de la charte quand il est actif.
struct MiniSwitch: ToggleStyle {
    var scale: CGFloat = 1
    func makeBody(configuration: Configuration) -> some View {
        Button { configuration.isOn.toggle() } label: {
            HStack(spacing: 4) {
                configuration.label
                Capsule().fill(configuration.isOn ? WG.climb : Color.gray.opacity(0.35))
                    .frame(width: 26 * scale, height: 15 * scale)
                    .overlay(alignment: configuration.isOn ? .trailing : .leading) {
                        Circle().fill(.white).frame(width: 11 * scale, height: 11 * scale).padding(2 * scale)
                    }
            }
            .padding(4).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .animation(.easeInOut(duration: 0.15), value: configuration.isOn)
    }
}

/// Boutons, pastilles et légendes posés sur la carte (disposition de l'écran Android).
struct MapOverlay: View {
    @AppStorage("pilot.dark") private var darkMode = false   // redessine au changement de mode
    @EnvironmentObject var app: AppModel
    @EnvironmentObject var map: MapControl
    @State private var airspaceHint = false
    let p: PilotUi
    @Binding var showProfile: Bool
    @Binding var showVario: Bool

    var body: some View {
        ZStack {
            // haut gauche : recentrage (AUTO/LIBRE), orientation (AUTO/N↑/RTE), REC compact
            VStack(alignment: .leading, spacing: 6) {
                // légende trafic OGN (maquette v8 : .traffic-key), édition complète, réseau connecté
                if let t = app.trafficSnap, t.connected {
                    (Text("OGN ").fontWeight(.bold).foregroundColor(PI.ink) + Text(t.pill))
                        .font(Font.custom("Inter", size: 9)).monospacedDigit().foregroundStyle(PI.dim)
                        .padding(.horizontal, 8).padding(.vertical, 5)
                        .background(PI.box.opacity(PI.dark ? 1 : 0.9), in: RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(WG.line))
                        .accessibilityLabel("Trafic OGN : \(t.pill)")
                        .offset(y: showProfile ? 0 : -34)
                }
                // panneaux masqués : pastilles indépendantes sur la carte
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)

            // haut droite : DÉMO, vent, échelle vario
            VStack(alignment: .trailing, spacing: 8) {
                Button { app.demo.toggle() } label: {
                    Text("DÉMO").font(WG.caption2.weight(.bold)).padding(.horizontal, 9).padding(.vertical, 5)
                        .background(app.demo ? WG.climb : PI.box, in: Capsule())
                        .foregroundStyle(app.demo ? .white : PI.ink).shadow(color: .black.opacity(0.15), radius: 2)
                }
                VStack(spacing: 3) {
                    if p.windKnown {
                        Image(systemName: "arrow.down").font(.subheadline.weight(.semibold)).rotationEffect(.degrees(p.windFromDeg)).foregroundStyle(PI.route)
                        Text(p.windText.replacingOccurrences(of: "/", with: "\n")).font(WG.caption2).foregroundStyle(PI.ink).multilineTextAlignment(.center)
                    } else {
                        Text("vent\n—").font(WG.caption2).foregroundStyle(PI.dim).multilineTextAlignment(.center)
                    }
                }
                .frame(width: 44).padding(.vertical, 8)
                .background(PI.box, in: RoundedRectangle(cornerRadius: 20)).shadow(color: .black.opacity(0.1), radius: 1)
                .accessibilityLabel(p.windKnown ? "Vent \(p.windText)" : "Vent pas encore estimé : spiralez")
                VStack(spacing: 3) {
                    Text("+4").font(WG.caption2).foregroundStyle(PI.dim)
                    LinearGradient(colors: [WG.vario(4), WG.vario(1.5), WG.vario(0), WG.vario(-3)], startPoint: .top, endPoint: .bottom)
                        .frame(width: 4, height: 50).clipShape(Capsule())
                    Text("−3").font(WG.caption2).foregroundStyle(PI.dim)
                }
                .frame(width: 44)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)

            // bas gauche : états GPS · BARO · DATA
            VStack(alignment: .leading, spacing: 4) {
                // position de la carte : recentrer (cible), orientation (boussole rouge en mode nord)
                VStack(spacing: 8) {
                    Button { map.recenter() } label: {
                        Image(systemName: "scope").font(.system(size: 18, weight: .medium))
                            .foregroundStyle(map.follow ? WG.sky : PI.ink)
                            .frame(width: 40, height: 40).background(PI.box.opacity(PI.dark ? 1 : 0.9), in: Circle())
                            .shadow(color: .black.opacity(0.15), radius: 2)
                    }
                    .accessibilityLabel(map.follow ? "Carte centrée sur le planeur" : "Recentrer la carte")
                    Button { map.cycleOrientation() } label: {
                        Group {
                            if map.orientation == .north {
                                ZStack {
                                    Image(systemName: "location.north.fill").font(.system(size: 18)).foregroundStyle(WG.bad)
                                    Text("N").font(Font.custom("Inter", size: 8).weight(.bold)).foregroundStyle(.white).offset(y: 2)
                                }
                            } else {
                                Text(map.orientation.label).font(WG.caption2.weight(.semibold)).foregroundStyle(PI.ink)
                            }
                        }
                        .frame(width: 40, height: 40).background(PI.box.opacity(PI.dark ? 1 : 0.9), in: Circle())
                        .shadow(color: .black.opacity(0.15), radius: 2)
                    }
                    .accessibilityLabel("Orientation de la carte : \(map.orientation == .north ? "nord en haut" : map.orientation.label)")
                }
                .padding(.bottom, 6)
                badge("GPS", app.status.gps)
                badge("BARO", app.status.baro)
                badge("DATA", app.status.data)
                // mode clair / sombre, sous les pastilles ; remonte avec elles quand le vario est ouvert
                DayNightToggle(dark: $darkMode).padding(.top, 6)
            }
            .accessibilityElement(children: .combine)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading).padding(.bottom, 26)   // juste au-dessus de l'échelle

            // bas droite : zones aéronautiques (au-dessus), zoom, pastille vario repliée
            VStack(alignment: .trailing, spacing: 8) {
            Button {
                if app.airspaces.isEmpty { airspaceHint = true } else { app.showAirspaces.toggle() }
            } label: {
                Image(systemName: "square.3.layers.3d").font(.system(size: 17, weight: .regular))
                    .foregroundStyle(app.showAirspaces && !app.airspaces.isEmpty ? WG.sky : PI.ink)
                    .frame(width: 40, height: 40).background(PI.box, in: Circle()).shadow(color: .black.opacity(0.1), radius: 1)
            }
            .accessibilityLabel(app.showAirspaces ? "Masquer les zones aéronautiques" : "Afficher les zones aéronautiques")
            .alert("Zones aéronautiques", isPresented: $airspaceHint) { Button("OK", role: .cancel) {} } message: {
                Text("Téléchargez la carte hors ligne de votre région dans Prévol pour afficher les zones aéronautiques (openAIP).")
            }
            VStack(spacing: 0) {
                Button { map.zoomIn() } label: { Image(systemName: "plus").frame(width: 40, height: 40) }.accessibilityLabel("Zoom avant")
                Divider().frame(width: 24)
                Button { map.zoomOut() } label: { Image(systemName: "minus").frame(width: 40, height: 40) }.accessibilityLabel("Zoom arrière")
            }
            .font(.title3.weight(.light)).foregroundStyle(PI.ink)
            .background(PI.box, in: RoundedRectangle(cornerRadius: 20)).shadow(color: .black.opacity(0.1), radius: 1)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)   // au plus bas, au-dessus de « vario »
        }
        .padding(10)
    }

    private func round(_ label: String, a11y: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(WG.caption2.weight(.semibold)).foregroundStyle(PI.ink)
                .frame(width: 40, height: 40).background(PI.box, in: Circle()).shadow(color: .black.opacity(0.15), radius: 2)
        }
        .accessibilityLabel(a11y)
    }

    private func badge(_ label: String, _ ok: Bool) -> some View {
        HStack(spacing: 4) { Circle().fill(ok ? PI.good : WG.bad).frame(width: 6, height: 6); Text(label).font(WG.caption2).foregroundStyle(PI.ink) }
            .padding(.horizontal, 7).padding(.vertical, 3).background(PI.box, in: Capsule())
            .accessibilityLabel("\(label) \(ok ? "actif" : "inactif")")
    }
}

/// Barre vario −3 … +4 m/s avec curseur.
struct VarioScale: View {
    let value: Double
    var body: some View {
        GeometryReader { g in
            let x = (min(max(value, -3), 4) + 3) / 7 * g.size.width
            ZStack(alignment: .leading) {
                LinearGradient(colors: [WG.vario(-3), WG.vario(0), WG.vario(1.5), WG.vario(4)], startPoint: .leading, endPoint: .trailing)
                    .clipShape(Capsule())
                Rectangle().fill(PI.ink).frame(width: 4, height: g.size.height + 8).offset(x: x - 2)
            }
        }
    }
}

/// Coupe vers le terrain, comme Android : relief (vert), pente de plané depuis l'altitude actuelle jusqu'à l'arrivée au
/// terrain (trait plein, inclinée selon la finesse et le vent), altitude nécessaire (pointillés rouges) jusqu'à
/// terrain + hauteur d'arrivée (« +300 »).
struct ProfileStrip: View {
    @EnvironmentObject var app: AppModel
    @AppStorage("pilot.dark") private var darkMode = false   // redessine au changement de mode
    let p: PilotUi

    var body: some View {
        let pts = p.profile
        let arrival = app.finesse >= 20 ? 300.0 : 0.0   // tour de piste en finesse 20 seulement
        let total = max(pts.last?.sKm ?? 0, p.routeKm)
        let top = max(500, (max(p.currentAltM, p.requiredAltM) / 500).rounded(.up) * 500)
        Chart {
            ForEach(Array(pts.enumerated()), id: \.offset) { _, d in
                AreaMark(x: .value("km", d.sKm), y: .value("relief", d.terrainM)).foregroundStyle(darkMode ? Color(red: 0.55, green: 1.0, blue: 0.45).opacity(0.75) : PI.good.opacity(0.35))
            }
            if total > 0 {
                LineMark(x: .value("km", 0.0), y: .value("alt", p.currentAltM), series: .value("s", "plané"))
                    .foregroundStyle(darkMode ? Color(red: 0.2, green: 0.85, blue: 1.0) : PI.ink).lineStyle(StrokeStyle(lineWidth: 2))
                LineMark(x: .value("km", total), y: .value("alt", p.arrivalAltM), series: .value("s", "plané"))
                    .foregroundStyle(darkMode ? Color(red: 0.2, green: 0.85, blue: 1.0) : PI.ink).lineStyle(StrokeStyle(lineWidth: 2))
                LineMark(x: .value("km", 0.0), y: .value("sécu", p.requiredAltM), series: .value("s", "sécu"))
                    .lineStyle(StrokeStyle(lineWidth: 1.5, dash: [5, 4])).foregroundStyle(darkMode ? Color(red: 1.0, green: 0.6, blue: 0.1) : WG.bad)
                LineMark(x: .value("km", total), y: .value("sécu", p.fieldElevM + arrival), series: .value("s", "sécu"))
                    .lineStyle(StrokeStyle(lineWidth: 1.5, dash: [5, 4])).foregroundStyle(darkMode ? Color(red: 1.0, green: 0.6, blue: 0.1) : WG.bad)
                PointMark(x: .value("km", total), y: .value("sécu", p.fieldElevM + arrival))
                    .symbolSize(20).foregroundStyle(PI.route)
                    .annotation(position: .top, alignment: .trailing) { if arrival > 0 { Text("+300").font(WG.caption2).foregroundStyle(darkMode ? Color(red: 1.0, green: 0.6, blue: 0.1) : WG.bad) } }
            }
        }
        .chartXScale(domain: 0...max(total, 0.1))
        .chartYScale(domain: 0...top)
        // axes à gauche comme Android ; en bas seulement « 0 » et l'étiquette de fin (distance · terrain)
        .chartXAxis { AxisMarks(values: .automatic(desiredCount: 3)) { AxisGridLine() } }
        .chartYAxis { AxisMarks(position: .leading, values: .automatic(desiredCount: 3)) { AxisGridLine().foregroundStyle(darkMode ? Color.white.opacity(0.25) : Color.gray.opacity(0.3)); AxisValueLabel().foregroundStyle(darkMode ? Color.white : WG.dim).font(darkMode ? WG.caption2.weight(.semibold) : WG.caption2) } }
        .overlay(alignment: .topTrailing) {
            if !p.arrivalText.isEmpty { Text(p.arrivalText).font(WG.caption2.weight(.semibold)).foregroundStyle(PI.good).padding(.trailing, 80) }
        }
        .overlay(alignment: .bottomTrailing) {
            if !p.profileEndText.isEmpty { Text(p.profileEndText).font(WG.caption2).foregroundStyle(PI.dim).offset(y: 12) }
        }
        .overlay { if total == 0 { Text("Coupe disponible dès la première position").font(WG.caption).foregroundStyle(PI.dim) } }
        .padding(.horizontal, 12).padding(.top, 14)
    }
}

/// État de la carte partagé avec les boutons : recentrage, orientation, zoom (comme le contrôleur de carte Android).
@MainActor
final class MapControl: ObservableObject {
    enum Orientation { case auto, north, track
        var label: String { switch self { case .auto: "AUTO"; case .north: "N↑"; case .track: "RTE" } }
    }
    @Published var follow = true
    @Published var orientation: Orientation = .auto
    @Published var distance: Double = 9000
    /// Changement de caméra en cours venant de l'app (pas d'un geste du pilote).
    var programmatic = false

    func recenter() { follow = true }
    func cycleOrientation() {
        orientation = switch orientation { case .auto: .north; case .north: .track; case .track: .auto }
        follow = true
    }
    func zoomIn() { distance = max(600, distance / 2) }
    func zoomOut() { distance = min(200_000, distance * 2) }
}

/// Carte : trace colorée par la montée, planeur, terrain et route, espaces aériens ; zoom automatique sur la spirale
/// (ThermalZoom commun) ; un geste du pilote passe en LIBRE jusqu'au recentrage.
struct FlightMapView: View {
    let p: PilotUi
    var airspaces: [AirspaceShape] = []
    @EnvironmentObject var map: MapControl
    @EnvironmentObject var app: AppModel
    @State private var camera: MapCameraPosition = .automatic
    @Namespace private var scope

    var body: some View {
        let me = CLLocationCoordinate2D(latitude: p.lat, longitude: p.lon)
        let field = CLLocationCoordinate2D(latitude: p.fieldLat, longitude: p.fieldLon)
        Map(position: $camera, scope: scope) {
            ForEach(airspaces, id: \.id) { a in
                MapPolygon(coordinates: coords(a))
                    .foregroundStyle(WG.airspace(a.family).opacity(0.06))
                    .stroke(WG.airspace(a.family), style: StrokeStyle(lineWidth: 1.5, dash: a.family == 1 ? [6, 4] : []))
            }
            ForEach(Array(segments.enumerated()), id: \.offset) { _, s in
                MapPolyline(coordinates: s.coords).stroke(WG.vario(s.climb), lineWidth: 4)
            }
            if p.hasPosition && (p.fieldLat != 0 || p.fieldLon != 0) {
                MapPolyline(coordinates: [me, field]).stroke(WG.route, style: StrokeStyle(lineWidth: 2, dash: [4, 5]))
            }
            if p.fieldLat != 0 || p.fieldLon != 0 {
                Annotation(p.fieldCode, coordinate: field) { Image(systemName: "flag.fill").foregroundStyle(WG.route) }
            }
            // trafic OGN (édition complète) : pompes du réseau puis aéronefs, comme FlightMap d'Android
            if let t = app.trafficSnap {
                ForEach(t.pumps, id: \.self) { pump in
                    Annotation("", coordinate: CLLocationCoordinate2D(latitude: pump.lat, longitude: pump.lon)) {
                        PumpMark(climb: pump.climbMs)
                    }
                }
                ForEach(t.planes, id: \.id) { a in
                    Annotation("", coordinate: CLLocationCoordinate2D(latitude: a.lat, longitude: a.lon)) {
                        PlaneMark(plane: a, rotation: (a.trackDeg?.doubleValue ?? 0) - heading)
                    }
                }
            }
            if p.hasPosition {
                Annotation("", coordinate: me) {
                    Image(systemName: "airplane").font(.title2).rotationEffect(.degrees(p.trackDeg - 90 - heading)).foregroundStyle(WG.brandInk)
                }
            }
        }
        .mapStyle(.standard(elevation: .flat, pointsOfInterest: .excludingAll))
        // échelle de distance en bas à gauche, au-dessus des pastilles (disposition Android)
        .overlay(alignment: .bottomLeading) {
            MapScaleView(anchorEdge: .leading, scope: scope).mapControlVisibility(.visible).padding(.leading, 10).padding(.bottom, 128)
        }
        .mapControls { }   // pas de boussole Apple : orientation gérée par AUTO / N↑ / RTE
        .mapScope(scope)
        // seul un geste du pilote (glisser, pincer) passe la carte en LIBRE ; un redimensionnement ne compte pas
        .simultaneousGesture(DragGesture(minimumDistance: 12).onChanged { _ in map.follow = false })
        .simultaneousGesture(MagnifyGesture().onChanged { _ in map.follow = false })
        .onChange(of: cameraKey) { _, _ in updateCamera() }
        .onAppear { updateCamera() }
    }

    /// AUTO : nord en spirale, route au nez sinon ; N↑ : nord fixe ; RTE : route au nez.
    private var heading: Double {
        switch map.orientation {
        case .north: return 0
        case .track: return p.trackDeg
        case .auto: return (p.zoomActive || p.circling) ? 0 : p.trackDeg
        }
    }

    private func coords(_ a: AirspaceShape) -> [CLLocationCoordinate2D] {
        let c = a.coords.map { $0.doubleValue }
        return stride(from: 0, to: c.count - 1, by: 2).map { CLLocationCoordinate2D(latitude: c[$0], longitude: c[$0 + 1]) }
    }

    private struct Segment { let coords: [CLLocationCoordinate2D]; let climb: Double }

    /// Trace découpée en tronçons de même couleur (une polyligne par tronçon).
    private var segments: [Segment] {
        var out: [Segment] = []
        var cur: [CLLocationCoordinate2D] = []
        var bucket = Int.min
        for d in p.trace {
            let b = Int((d.climbMs * 2).rounded())
            let c = CLLocationCoordinate2D(latitude: d.lat, longitude: d.lon)
            if b != bucket, let last = cur.last {
                out.append(Segment(coords: cur, climb: Double(bucket) / 2)); cur = [last]
            }
            bucket = b; cur.append(c)
        }
        if cur.count > 1 { out.append(Segment(coords: cur, climb: Double(bucket) / 2)) }
        return out
    }

    private var cameraKey: String {
        let pos = p.zoomActive ? "z\(Int(p.zoomLat * 2000)),\(Int(p.zoomLon * 2000))" : "p\(Int(p.lat * 2000)),\(Int(p.lon * 2000)),\(p.hasPosition)"
        return "\(pos)|\(map.follow)|\(map.orientation.label)|\(Int(map.distance))|\(Int(heading / 5))"
    }

    private func updateCamera() {
        guard map.follow else { return }
        map.programmatic = true
        withAnimation(.easeInOut(duration: 0.9)) {
            if p.zoomActive {
                // cercle de spirale ≈ 2/3 du plus petit côté de la carte (comme Android) ; les boutons +/− ajustent d'un cran
                camera = .camera(MapCamera(centerCoordinate: .init(latitude: p.zoomLat, longitude: p.zoomLon), distance: p.zoomRadiusM * 7 * map.distance / 9000, heading: heading))
            } else if p.hasPosition {
                camera = .camera(MapCamera(centerCoordinate: .init(latitude: p.lat, longitude: p.lon), distance: map.distance, heading: heading))
            } else if p.fieldLat != 0 {
                camera = .camera(MapCamera(centerCoordinate: .init(latitude: p.fieldLat, longitude: p.fieldLon), distance: max(map.distance, 15000), heading: 0))
            }
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) { map.programmatic = false }
    }
}

/// Terrain de référence : automatique (meilleure marge, retour au club) ou choisi à la main.
struct FieldPicker: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Button("Automatique (meilleure marge)") { app.engine.selectField(id: nil); dismiss() }
                ForEach(app.engine.fieldChoices(), id: \.id) { f in
                    Button { app.engine.selectField(id: f.id); dismiss() } label: {
                        HStack {
                            VStack(alignment: .leading) {
                                Text(f.code).font(WG.headline).foregroundStyle(WG.brandInk)
                                Text(f.name).font(WG.caption).foregroundStyle(WG.dim)
                            }
                            Spacer()
                            VStack(alignment: .trailing) {
                                Text(f.distanceText).font(WG.subhead.monospacedDigit())
                                if !f.marginText.isEmpty { Text(f.marginText).font(WG.caption.monospacedDigit()).foregroundStyle(f.reachable ? WG.good : WG.bad) }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Terrain")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

/// Aéronef OGN : flèche orientée (couleur du vario), étiquette DDB ou repli court, écart d'altitude.
private struct PlaneMark: View {
    let plane: TrafficPlane
    let rotation: Double

    var body: some View {
        VStack(spacing: 1) {
            Image(systemName: "location.north.fill").font(.system(size: 13))
                .rotationEffect(.degrees(rotation))
                .foregroundStyle(plane.circling ? WG.climb : WG.brandInk)
            let name = plane.fullLabel.isEmpty ? (plane.shortLabel.isEmpty ? plane.typeLabel : plane.shortLabel) : plane.fullLabel
            Text(name + (plane.relativeAltText.isEmpty ? "" : " " + plane.relativeAltText))
                .font(WG.caption2.weight(.semibold)).monospacedDigit()
                .padding(.horizontal, 4).padding(.vertical, 1)
                .background(WG.card.opacity(0.85), in: RoundedRectangle(cornerRadius: 4))
                .foregroundStyle(WG.brandInk)
        }
        .opacity(plane.ageS > 60 ? 0.5 : 1)
        .accessibilityLabel("Aéronef \(plane.fullLabel.isEmpty ? plane.typeLabel : plane.fullLabel)")
    }
}

/// Pompe repérée par le réseau : cercle coloré selon la montée moyenne.
private struct PumpMark: View {
    let climb: Double
    var body: some View {
        ZStack {
            Circle().fill(WG.vario(climb).opacity(0.25)).frame(width: 30, height: 30)
            Circle().stroke(WG.vario(climb), lineWidth: 2).frame(width: 30, height: 30)
            Text(formatClimb(climb)).font(WG.caption2.weight(.bold)).foregroundStyle(WG.brandInk)
        }
        .accessibilityLabel("Pompe \(formatClimb(climb)) mètres par seconde")
    }
    private func formatClimb(_ v: Double) -> String {
        let f = NumberFormatter(); f.locale = Locale(identifier: "fr_FR"); f.minimumFractionDigits = 1; f.maximumFractionDigits = 1
        return (v >= 0 ? "+" : "") + (f.string(from: NSNumber(value: v)) ?? "")
    }
}


/// Interrupteur mode clair / sombre (soleil, lune) sur fond noir, comme « relief » et « vario ».
struct DayNightToggle: View {
    @Binding var dark: Bool

    var body: some View {
        Button { withAnimation(.easeInOut(duration: 0.25)) { dark.toggle() } } label: {
            HStack(spacing: 0) {
                icon("sun.max.fill", on: !dark)
                icon("moon.fill", on: dark)
            }
            .padding(3).background(Color.black.opacity(0.85), in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(dark ? "Passer en mode clair" : "Passer en mode sombre")
    }

    private func icon(_ name: String, on: Bool) -> some View {
        Image(systemName: name).font(.system(size: 13, weight: .semibold))
            .foregroundStyle(on ? Color.black : Color.white.opacity(0.7))
            .frame(width: 30, height: 24)
            .background(on ? Color.white : Color.clear, in: Capsule())
    }
}
