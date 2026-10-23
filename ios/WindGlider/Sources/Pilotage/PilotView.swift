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
    static var dark: Bool { true }   // charte Godspeed sur toute l'app ; « pilot.dark » ne choisit que le fond satellite
    static var ink: Color { dark ? .white : WG.brandInk }
    static var dim: Color { dark ? Color.white.opacity(0.62) : WG.dim }
    static var box: Color { dark ? WG.lavenderBottom : WG.card }   // Godspeed : lavande, jamais noir pur
    static var good: Color { dark ? Color(red: 0.80, green: 1.0, blue: 0.70) : WG.good }      // vert fluo très clair
    static var route: Color { dark ? Color(red: 0.86, green: 0.72, blue: 1.0) : WG.route }    // violet fluo très clair
    /// marge proche du seuil (PilotDeclutter.CAUTION_M) : orange de la charte, plus sombre en clair pour le contraste
    static var caution: Color { dark ? WG.climb : WG.warn }
    // V5 (JB, 19/10/2026) : chiffres plus gras, même taille
    static func number(_ size: CGFloat) -> Font { Font.system(size: size).weight(dark ? .heavy : .bold).monospacedDigit() }
    static func marginColor(_ level: MarginLevel) -> Color {
        switch level {
        case .ok: return good
        case .caution: return caution
        case .alarm: return WG.bad
        default: return dim
        }
    }
}

/// Retour haptique des interrupteurs de Pilotage (Essentiel, relief, vario, finesse, son, jour/nuit, couches).
enum Haptic {
    static func tap() { UIImpactFeedbackGenerator(style: .medium).impactOccurred() }
}

/// Écran de vol épuré (JB + revue pilote / FH, 22/10/2026) : chaque information une seule fois, centre de la carte libre.
/// Haut : marge + altitude (tambour, ruban horizontal) à gauche, terrain à droite. Bord droit : vario vertical.
/// Bas : réglages « ⋯ », enregistrement, zoom, Essentiel. Essentiel : la marge seule (et la distance au terrain).
struct PilotView: View {
    static var benchNoMap: Bool {
        #if DEBUG
        ProcessInfo.processInfo.arguments.contains("-benchNoMap")
        #else
        false
        #endif
    }
    @EnvironmentObject var app: AppModel
    /// état du vol (4 Hz) : seule cette vue s'y abonne
    @EnvironmentObject var flight: FlightModel
    @EnvironmentObject var map: MapControl
    @State private var choosingField = false
    @AppStorage("pilot.showProfile") private var showProfile = true
    @AppStorage("pilot.showVario") private var showVario = true
    @State private var profileAnimating = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @AppStorage("pilot.dark") private var dark = false
    /// V5 : mode Essentiel (la marge seule) ; mémorisé
    @AppStorage("pilot.essential") private var essential = false
    @State private var airspaceHint = false
    @AppStorage(Lang.key) private var lang = Lang.current
    /// Option du menu « ⋯ » : fréquence radio du terrain de référence (JB, 22/10/2026).
    @AppStorage("pilot.showRadio") private var showRadio = false
    /// Option « Carte économe » (recentrage par pas, moins de batterie) ; désactivée par défaut.
    @AppStorage("map.stepMode") private var stepMap = false

    var body: some View {
        let p = flight.pilot
        let ds = PilotDeclutter.shared.display(
            essential: essential, profileWanted: showProfile, varioWanted: showVario,
            marginKnown: p.marginKnown, marginM: p.marginM,
            gpsOk: app.status.gps && p.gpsOk, baroOk: app.status.baro && (p.baroOk || p.demo || !p.running), dataOk: app.status.data,   // GPS : position de moins de 5 s, pas seulement l'autorisation
            ognConnected: flight.trafficSnap?.connected ?? false)
        ZStack(alignment: .top) {
            if PilotView.benchNoMap {
                Color.black.ignoresSafeArea()   // mesures d'énergie seulement (build de développement)
            } else if ProcessInfo.processInfo.arguments.contains("-mapV2") || UserDefaults.standard.bool(forKey: "fluidMapOff") {
                FlightMapView(p: p, airspaces: app.airspaces)
            } else {
                FluidMapView(p: p, airspaces: app.showAirspaces ? app.airspaces : [], traffic: flight.trafficSnap, predictor: app.predictor, bottomInset: 56, dark: dark, economy: app.economy, stepMap: stepMap)   // mentions Plans au-dessus des boutons du bas
                    .ignoresSafeArea(.container, edges: .bottom)
            }
            // une seule pile : bandeau → coupe (si ouverte) → outils des bords ; c'est la mise en page qui fait
            // descendre et remonter les icônes avec la coupe, image par image (designer UI, 23/10/2026)
            VStack(spacing: 6) {
                header(p, ds)
                statusLine(ds)
                if ds.showProfile {
                    VStack(spacing: 0) {
                        ProfileStrip(p: p).frame(height: 92).padding(.bottom, 10)
                        Text(trDyn(p.fieldElevText)).font(.system(size: 12)).foregroundStyle(PI.dim)
                            .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 16).padding(.bottom, 2)
                    }
                    .background(PanelBackground(dark: true), in: RoundedRectangle(cornerRadius: 14))
                    .contentShape(Rectangle())
                    .onTapGesture { setProfile(false) }   // un toucher la range dans le bouton montagne
                    .accessibilityAddTraits(.isButton)
                    .accessibilityHint(tr("Toucher pour ranger la coupe"))
                    .padding(.horizontal, 8)
                    // déroulée depuis le bandeau comme un store, fondu sur la même durée, sans échelle ni rebond
                    .transition(.move(edge: .top).combined(with: .opacity))
                }
                if !essential { edges(p, ds) }
            }
            .clipped()
            bottomBar(p, ds)
        }
        .background(PI.box)
        .animation(.easeInOut(duration: 0.25), value: essential)
        // marge qui passe sous le seuil (orange ou rouge) : la coupe se rouvre seule ; le pilote peut la refermer
        .onChange(of: ds.marginLevel) { old, new in
            let low: (MarginLevel) -> Bool = { $0 == .caution || $0 == .alarm }
            if low(new) && !low(old) { setProfile(true) }
        }
        .toolbarColorScheme(.dark, for: .tabBar)
        .toolbarBackground(.visible, for: .tabBar)
        .toolbarBackground(WG.lavenderBottom, for: .tabBar)
        .animation(.easeInOut(duration: 0.25), value: dark)
        .sheet(isPresented: $choosingField) { FieldPicker().environmentObject(app).presentationDetents([.medium]) }
        .alert(tr("Zones aéronautiques"), isPresented: $airspaceHint) { Button("OK", role: .cancel) {} } message: {
            Text(tr("Téléchargez la carte hors ligne de votre région dans Prévol pour afficher les zones aéronautiques (openAIP)."))
        }
        .onAppear { UIApplication.shared.isIdleTimerDisabled = true; app.applyLaunchDemo(); app.sensors.motionWanted = true }
        // hors de Pilotage, accéléromètre coupé sauf pendant un vol (le vario sonore en a besoin écran éteint)
        .onDisappear { UIApplication.shared.isIdleTimerDisabled = false; app.sensors.motionWanted = app.isRecording || app.engine.inFlight }
    }

    // MARK: haut : sécurité à gauche, terrain à droite

    private func header(_ p: PilotUi, _ ds: PilotDisplay) -> some View {
        let tape = AltitudeTape.shared.state(altM: p.altText == "—" ? nil : KotlinDouble(value: p.currentAltM),
                                             minimumM: KotlinDouble(value: p.requiredAltM), marginKnown: p.marginKnown)
        let low = ds.marginLevel == .caution || ds.marginLevel == .alarm
        return HStack(alignment: .top, spacing: 6) {
            VStack(alignment: .leading, spacing: 2) {
                SafetyBand(marginText: p.marginText, marginColor: PI.marginColor(ds.marginLevel), tape: tape, dark: true, essential: essential,
                           accessory: AnyView(finesseButton(alert: low)), alert: low, altRef: trDyn(p.altRefText))
                if essential {
                    // la seule autre information de survie : où est le terrain
                    Text("\(p.fieldCode) · \(p.distanceText) km").font(WG.pfd(20)).foregroundStyle(low ? .black : PI.ink)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            // marge faible (orange) ou négative (rouge) : toute la boîte change, aplat plein, textes noirs
            .panel(dark, fill: low ? (ds.marginLevel == .alarm ? WG.marginAlarmBox : WG.marginCautionBox) : nil)
            if !essential {
                // terrain : code, distance, route (violet = navigation), finesse touchable
                VStack(alignment: .trailing, spacing: 2) {
                    Button { choosingField = true } label: {
                        VStack(alignment: .trailing, spacing: 0) {
                            HStack(spacing: 4) {
                                Text(p.fieldCode).font(.system(size: 17, weight: .semibold))
                                Image(systemName: "chevron.down").font(.system(size: 11, weight: .bold)).foregroundStyle(PI.route)
                            }
                            HStack(alignment: .firstTextBaseline, spacing: 2) {
                                Text(p.distanceText).font(WG.pfd(32, .bold)).minimumScaleFactor(0.6)
                                Text("km").font(.system(size: 13)).foregroundStyle(PI.dim)
                            }
                            Text(p.bearingText).font(WG.pfd(22)).foregroundStyle(PI.route)
                        }
                        .foregroundStyle(PI.ink)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(tr("Choisir le terrain de référence"))
                }
                .lineLimit(1)
                .frame(maxHeight: .infinity, alignment: .top)   // les deux boîtes du haut ont la même hauteur
                .panel(dark)
            }
        }
        .fixedSize(horizontal: false, vertical: true)
        .padding(.horizontal, 8).padding(.top, 4)
    }

    /// Ouvre ou range la coupe : une seule animation douce sans rebond (≈ 1/3 s, repli un peu plus sec) ;
    /// un double toucher pendant le mouvement est ignoré ; « Réduire les animations » : simple fondu court.
    private func setProfile(_ open: Bool) {
        guard open != showProfile, !profileAnimating else { return }
        Haptic.tap()
        profileAnimating = true
        let anim: Animation = reduceMotion ? .easeInOut(duration: 0.15) : .smooth(duration: open ? 0.32 : 0.26)
        withAnimation(anim) { showProfile = open } completion: { profileAnimating = false }
    }

    /// Finesse sous la marge : un toucher passe 10 → 20 → 30 (réglage détaillé en Prévol).
    private func finesseButton(alert: Bool) -> some View {
        let c: Color = alert ? .black : WG.selected(dark: true)
        return Button { Haptic.tap(); app.finesse = app.finesse >= 30 ? 10 : app.finesse + 10 } label: {
            Text("F\(app.finesse)").font(WG.pfd(16.5)).foregroundStyle(c)   // +10 %
                .padding(.horizontal, 9).padding(.vertical, 3.5)
                .overlay(Capsule().stroke(c.opacity(0.6)))
        }
        .accessibilityLabel(tr("Finesse %@", "\(app.finesse)"))
    }

    /// Sous le bandeau, seulement si nécessaire : capteur en défaut, démo (« dark cockpit »).
    @ViewBuilder private func statusLine(_ ds: PilotDisplay) -> some View {
        let noZones = app.showAirspaces && app.airspaces.isEmpty
        let noRelief = flight.pilot.marginKnown && !flight.pilot.reliefChecked
        if ds.gpsAlert || ds.baroAlert || ds.dataAlert || noZones || noRelief || app.economy {
            HStack(spacing: 6) {
                if ds.gpsAlert { badge("GPS") }
                if ds.baroAlert { badge("BARO") }
                if ds.dataAlert { badge("DATA") }
                if app.economy {
                    // iPhone chaud ou mode Économie : carte moins fluide et OGN en pause, le pilote sait pourquoi
                    Label(tr("Économie"), systemImage: "thermometer.high")
                        .font(.system(size: 13, weight: .semibold)).foregroundStyle(WG.warn)
                        .padding(.horizontal, 8).padding(.vertical, 4).background(PanelBackground(dark: true), in: Capsule())
                }
                if noRelief {
                    // marge calculée sans relief (hors carte ou carte absente) : à ne pas lire comme une marge vérifiée
                    Label(tr("Relief non vérifié"), systemImage: "mountain.2")
                        .font(.system(size: 13, weight: .semibold)).foregroundStyle(WG.warn)
                        .padding(.horizontal, 8).padding(.vertical, 4).background(PanelBackground(dark: true), in: Capsule())
                }
                if noZones {
                    // carte hors ligne absente : le dire plutôt que montrer une carte sans zones (ambre = prudence)
                    Button { airspaceHint = true } label: {
                        Label(tr("Zones : carte à télécharger"), systemImage: "square.3.layers.3d.slash")
                            .font(.system(size: 13, weight: .semibold)).foregroundStyle(WG.warn)
                            .padding(.horizontal, 8).padding(.vertical, 4).background(PanelBackground(dark: true), in: Capsule())
                    }
                }
                Spacer()
            }
            .padding(.horizontal, 10)
        }
    }

    private func badge(_ label: String) -> some View {
        HStack(spacing: 4) { Circle().fill(WG.bad).frame(width: 7, height: 7); Text(label).font(.system(size: 13, weight: .semibold)).foregroundStyle(PI.ink) }
            .padding(.horizontal, 8).padding(.vertical, 4).background(PanelBackground(dark: true), in: Capsule())
            .accessibilityLabel(tr("%@ inactif", label))
    }

    // MARK: bords : vent et recentrage à gauche, vario à droite ; le centre reste à la carte

    private func edges(_ p: PilotUi, _ ds: PilotDisplay) -> some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 8) {
                if !ds.showProfile {
                    // en tête de colonne ; coupe dépliée : vent, orientation et recentrage remontent sous elle.
                    // coupe terrain repliée : même taille et même forme que le vent, montagne blanche
                    Button { setProfile(true) } label: {
                        Image(systemName: "mountain.2.fill").font(.system(size: 18, weight: .semibold)).foregroundStyle(.white)
                            .frame(width: 44, height: 50).background(PanelBackground(dark: true), in: RoundedRectangle(cornerRadius: 14))
                    }
                    .accessibilityLabel(tr("Afficher la coupe du terrain"))
                }
                // vent : flèche + vitesse ; flèche en pointillés si c'est une prévision
                VStack(spacing: 0) {
                    if p.windKnown {
                        Image(systemName: p.windForecast ? "arrow.down.circle.dotted" : "arrow.down").font(.system(size: 18, weight: .semibold))
                            .rotationEffect(.degrees(p.windFromDeg)).foregroundStyle(PI.route)
                        Text("\(Int(p.windSpeedKmh.rounded()))").font(WG.pfd(15)).foregroundStyle(PI.ink)
                    } else {
                        Image(systemName: "wind").font(.system(size: 15)).foregroundStyle(PI.dim)
                    }
                }
                .frame(width: 44, height: 50).background(PanelBackground(dark: true), in: RoundedRectangle(cornerRadius: 14))
                .accessibilityLabel(p.windKnown ? tr("Vent %@", trDyn(p.windText)) : tr("Vent pas encore estimé : spiralez"))
                // orientation de la vue : AUTO (nord en spirale, route sinon) → N↑ → RTE ; toujours visible
                Button { Haptic.tap(); map.cycleOrientation() } label: {
                    Group {
                        if map.orientation == .north {
                            ZStack {
                                Image(systemName: "location.north.fill").font(.system(size: 18)).foregroundStyle(WG.bad)
                                Text("N").font(.system(size: 8, weight: .bold)).foregroundStyle(.white).offset(y: 2)
                            }
                        } else {
                            Text(map.orientation.label).font(.system(size: 12, weight: .bold)).foregroundStyle(PI.ink)
                        }
                    }
                    .frame(width: 44, height: 44).background(PanelBackground(dark: true), in: Circle())
                }
                .accessibilityLabel(tr("Orientation de la carte : %@", map.orientation == .north ? tr("nord en haut") : map.orientation.label))
                if !map.follow {
                    roundButton("scope", tint: WG.sky, a11y: tr("Recentrer la carte")) { map.recenter() }
                }
                if showRadio {
                    // antenne + code + fréquence en cyan (Godspeed) ; « — » si openAIP ne la donne pas
                    VStack(spacing: 2) {
                        Image(systemName: "antenna.radiowaves.left.and.right").font(.system(size: 14, weight: .semibold)).foregroundStyle(PI.dim)
                        Text(p.fieldCode).font(.system(size: 12, weight: .semibold)).foregroundStyle(PI.ink)
                        Text(p.fieldFrequencyText).font(WG.pfd(13)).foregroundStyle(WG.selected(dark: true))
                    }
                    .lineLimit(1).fixedSize()
                    .padding(.horizontal, 6).padding(.vertical, 6)
                    .background(PanelBackground(dark: true), in: RoundedRectangle(cornerRadius: 14))
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(tr("Fréquence %@ : %@", p.fieldCode, p.fieldFrequencyText))
                }
            }
            Spacer(minLength: 0)
            if ds.showVario {
                VarioRail(ms: p.varioMs, valid: p.varioText != "—", spiralMs: p.spiralMs, dark: true, soundOn: app.soundOn) { app.soundOn.toggle() }
            }
        }
        .padding(.horizontal, 8)
        .frame(maxHeight: .infinity, alignment: .top)
    }

    // MARK: bas : réglages, enregistrement, zoom, Essentiel

    private func bottomBar(_ p: PilotUi, _ ds: PilotDisplay) -> some View {
        ZStack(alignment: .bottom) {
            HStack(alignment: .bottom) {
                // gauche : DÉMO au-dessus du logo Plans (il ne décale plus les outils du haut), puis réglages
                VStack(alignment: .leading, spacing: 30) {
                    if app.demo {
                        Text(tr("DÉMO")).font(.system(size: 13, weight: .bold)).padding(.horizontal, 9).padding(.vertical, 4)
                            .background(WG.climb, in: Capsule()).foregroundStyle(.white)
                    }
                    if !essential { settingsMenu() }
                }
                Spacer()
                // basique ⇄ complet : petite pastille, toujours au même endroit
                Button { Haptic.tap(); essential.toggle() } label: {
                    Text(essential ? tr("COMPLET") : tr("BASIQUE")).font(.system(size: 11, weight: .heavy))
                        .padding(.horizontal, 9).frame(height: 30)
                        .background(essential ? AnyShapeStyle(WG.climb) : AnyShapeStyle(PanelBackground(dark: true)), in: Capsule())
                        .foregroundStyle(essential ? .white : PI.ink)
                }
                .accessibilityLabel(essential ? tr("Afficher tout Pilotage") : tr("N'afficher que l'essentiel"))
            }
            // enregistrement : point rouge + durée, au centre exact de l'écran
            Button { app.toggleRecording() } label: {
                HStack(spacing: 6) {
                    Circle().fill(p.recording ? WG.bad : PI.dim).frame(width: 10, height: 10)
                    Text(p.flightTimeText).font(WG.pfd(15)).foregroundStyle(PI.ink)
                }
                .padding(.horizontal, 12).frame(height: 40).background(PanelBackground(dark: true), in: Capsule())
            }
            .accessibilityLabel(p.recording ? tr("Arrêter l'enregistrement") : tr("Démarrer l'enregistrement"))
        }
        .padding(.horizontal, 10).padding(.bottom, 8)
        .frame(maxHeight: .infinity, alignment: .bottom)
    }

    /// Réglages : hors du champ de vol (on ne règle pas en spirale).
    private func settingsMenu() -> some View {
        Menu {
            Toggle(tr("Vario"), isOn: $showVario)
            Toggle(tr("Fréquence radio du terrain"), isOn: $showRadio)
            Toggle(tr("Carte économe (recentrage par pas)"), isOn: $stepMap)
            Button(app.showAirspaces ? tr("Masquer les zones aéronautiques") : tr("Afficher les zones aéronautiques")) {
                if app.airspaces.isEmpty { airspaceHint = true } else { app.showAirspaces.toggle() }
            }
            Button(lang == "fr" ? "English" : "Français") { lang = lang == "fr" ? "en" : "fr" }
            Toggle(tr("Fond satellite"), isOn: $dark)
            // revue résilience M8 : la démo remplace le vol réel ; impossible de la lancer pendant un vol
            Toggle(tr("DÉMO"), isOn: $app.demo).disabled(!app.demo && app.engine.inFlight)
        } label: {
            Image(systemName: "ellipsis").font(.system(size: 20, weight: .bold)).foregroundStyle(PI.ink)
                .frame(width: 44, height: 44).background(PanelBackground(dark: true), in: Circle())
        }
        .accessibilityLabel(tr("Réglages de l'écran de vol"))
    }

    private func roundButton(_ icon: String, tint: Color, a11y: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 18, weight: .medium)).foregroundStyle(tint)
                .frame(width: 44, height: 44).background(PanelBackground(dark: true), in: Circle())
        }
        .accessibilityLabel(a11y)
    }
}

private extension View {
    /// Panneau de Pilotage : dégradé lavande Godspeed (sombre) ou blanc à 90 % (clair), coins arrondis, sans ombre noire.
    func panel(_ dark: Bool, fill: Color? = nil) -> some View {
        padding(.horizontal, 12).padding(.top, 8).padding(.bottom, 4)   // bas remonté de 4 pt (place inutile)
            .background(fill.map { AnyShapeStyle($0) } ?? AnyShapeStyle(PanelBackground(dark: true)), in: RoundedRectangle(cornerRadius: 14))
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
            Text(label).font(Font.system(size: 11 * scale)).foregroundStyle(dark ? .white : PI.dim)
        }
            .toggleStyle(MiniSwitch(scale: scale))
            .padding(.horizontal, dark ? 4 : 0)
            .background(dark ? Color.black.opacity(0.85) : .clear, in: Capsule())
            .accessibilityLabel(isOn ? tr("Masquer %@", label) : tr("Afficher %@", label))
    }
}

/// Interrupteur réduit (≈ 26 × 15 pt), orange de la charte quand il est actif.
struct MiniSwitch: ToggleStyle {
    var scale: CGFloat = 1
    func makeBody(configuration: Configuration) -> some View {
        Button { Haptic.tap(); configuration.isOn.toggle() } label: {
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

/// Coupe vers le terrain, comme Android : relief (vert), pente de plané depuis l'altitude actuelle jusqu'à l'arrivée au
/// terrain (trait plein, inclinée selon la finesse et le vent), altitude nécessaire (pointillés rouges) jusqu'à
/// terrain + hauteur d'arrivée (« +300 »).
struct ProfileStrip: View {
    @EnvironmentObject var app: AppModel
    @AppStorage("pilot.dark") private var darkMode = false   // redessine au changement de mode
    let p: PilotUi

    var body: some View {
        let pts = p.profile
        let arrival = Double(app.arrivalMargin)   // marge d'arrivée réglée en Prévol
        let total = max(pts.last?.sKm ?? 0, p.routeKm)
        let top = max(500, (max(p.currentAltM, p.requiredAltM) / 500).rounded(.up) * 500)
        Chart {
            // une série par tronçon de relief connu : un vrai trou là où il est inconnu (pas de sol interpolé)
            ForEach(Array(pts.enumerated()).filter { $0.element.terrainM.isFinite }, id: \.offset) { i, d in
                AreaMark(x: .value("km", d.sKm), y: .value("relief", d.terrainM), series: .value("tronçon", reliefSegment(pts, i))).foregroundStyle(darkMode ? Color(red: 0.55, green: 1.0, blue: 0.45).opacity(0.75) : PI.good.opacity(0.35))
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
                    .annotation(position: .top, alignment: .trailing) { if arrival > 0 { Text("+\(Int(arrival))").font(WG.caption2).foregroundStyle(darkMode ? Color(red: 1.0, green: 0.6, blue: 0.1) : WG.bad) } }
            }
        }
        .chartXScale(domain: 0...max(total, 0.1))
        .chartYScale(domain: 0...top)
        // axes à gauche comme Android ; en bas seulement « 0 » et l'étiquette de fin (distance · terrain)
        .chartXAxis { AxisMarks(values: .automatic(desiredCount: 3)) { AxisGridLine() } }
        .chartYAxis { AxisMarks(position: .leading, values: .automatic(desiredCount: 3)) { AxisGridLine().foregroundStyle(darkMode ? Color.white.opacity(0.25) : Color.gray.opacity(0.3)); AxisValueLabel().foregroundStyle(darkMode ? Color.white : WG.dim).font(darkMode ? WG.caption2.weight(.semibold) : WG.caption2) } }
        .overlay(alignment: .topTrailing) {
            if !p.arrivalText.isEmpty { Text(p.arrivalText).font(WG.caption2.weight(.semibold)).foregroundStyle(p.arrivalAltM >= p.fieldElevM ? PI.good : WG.bad).padding(.trailing, 80) }
        }
        .overlay(alignment: .bottomTrailing) {
            if !p.profileEndText.isEmpty { Text(p.profileEndText).font(WG.caption2).foregroundStyle(PI.dim).offset(y: 12) }
        }
        .overlay { if total == 0 { Text(tr("Coupe disponible dès la première position")).font(WG.caption).foregroundStyle(PI.dim) } }
        .padding(.horizontal, 12).padding(.top, 14)
    }
}

/// Numéro du tronçon de relief connu auquel appartient le point i (change à chaque trou NaN).
func reliefSegment(_ pts: [ProfileDot], _ i: Int) -> Int {
    var seg = 0
    for k in 0..<i where !pts[k].terrainM.isFinite && (k + 1 < pts.count && pts[k + 1].terrainM.isFinite) { seg += 1 }
    return seg
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
                    .foregroundStyle(WG.airspace(a.family).opacity(WG.airspaceFill(a.family)))
                    .stroke(WG.airspace(a.family), style: StrokeStyle(lineWidth: 1, dash: WG.airspaceDash(a.family)))
            }
            ForEach(Array(segments.enumerated()), id: \.offset) { _, s in
                MapPolyline(coordinates: s.coords).stroke(WG.varioFlash(s.climb), lineWidth: 4)
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
                Button(tr("Automatique (meilleure marge)")) { app.engine.selectField(id: nil); dismiss() }
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
            .navigationTitle(tr("Terrain"))
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
        .accessibilityLabel(tr("Aéronef %@", plane.fullLabel.isEmpty ? trDyn(plane.typeLabel) : plane.fullLabel))
    }
}

/// Pompe repérée par le réseau : cercle coloré selon la montée moyenne.
private struct PumpMark: View {
    let climb: Double
    var body: some View {
        ZStack {
            Circle().fill(WG.varioFlash(climb).opacity(0.25)).frame(width: 30, height: 30)
            Circle().stroke(WG.varioFlash(climb), lineWidth: 2).frame(width: 30, height: 30)
            Text(formatClimb(climb)).font(WG.caption2.weight(.bold)).foregroundStyle(WG.brandInk)
        }
        .accessibilityLabel(tr("Pompe %@ mètres par seconde", formatClimb(climb)))
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
        Button { Haptic.tap(); withAnimation(.easeInOut(duration: 0.25)) { dark.toggle() } } label: {
            HStack(spacing: 0) {
                icon("sun.max.fill", on: !dark)
                icon("moon.fill", on: dark)
            }
            .padding(3).background(Color.black.opacity(0.85), in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(dark ? tr("Passer en mode clair") : tr("Passer en mode sombre"))
    }

    private func icon(_ name: String, on: Bool) -> some View {
        Image(systemName: name).font(.system(size: 13, weight: .semibold))
            .foregroundStyle(on ? Color.black : Color.white.opacity(0.7))
            .frame(width: 30, height: 24)
            .background(on ? Color.white : Color.clear, in: Capsule())
    }
}
