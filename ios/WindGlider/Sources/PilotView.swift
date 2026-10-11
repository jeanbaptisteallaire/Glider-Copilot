import Charts
import MapKit
import SwiftUI
import WindGliderShared

/// Onglet Pilotage (édition Lite), d'après l'écran Android : marge de sécurité, distance et finesse, terrain et cap,
/// coupe vers le terrain, carte (trace colorée par le vario, route, vent, zoom spirale), vario et moyennes, REC.
/// Fond blanc (V18.7). Calculs : moteur commun (FlightEngineCore + SafetyEngine).
/// Coupe et vario se masquent (interrupteurs orange) pour agrandir la carte ; masqués, ils restent en pastille sur la carte.
struct PilotView: View {
    @EnvironmentObject var app: AppModel
    @State private var choosingField = false
    @AppStorage("pilot.showProfile") private var showProfile = true
    @AppStorage("pilot.showVario") private var showVario = true

    var body: some View {
        let p = app.pilot
        VStack(spacing: 0) {
            header(p)
            if showProfile {
                ZStack(alignment: .topTrailing) {
                    VStack(spacing: 0) {
                        ProfileStrip(p: p).frame(height: 92).padding(.bottom, 10)
                        Text(p.fieldElevText).font(WG.caption).foregroundStyle(WG.dim)
                            .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 16).padding(.bottom, 2)
                    }
                    PanelToggle(isOn: $showProfile, label: "coupe").padding(.trailing, 6).padding(.top, 2)
                }
                .background(WG.brandPaper.opacity(0.5))
            }
            ZStack(alignment: .top) {
                FlightMapView(p: p, airspaces: app.airspaces)
                MapOverlay(p: p, showProfile: $showProfile, showVario: $showVario)
            }
            if showVario {
                ZStack(alignment: .topTrailing) {
                    varioBar(p)
                    PanelToggle(isOn: $showVario, label: "vario").padding(.trailing, 6).padding(.top, 2)
                }
            }
        }
        .background(WG.card)
        .sheet(isPresented: $choosingField) { FieldPicker().environmentObject(app).presentationDetents([.medium]) }
        .onAppear { UIApplication.shared.isIdleTimerDisabled = true }
        .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
    }

    // MARK: en-tête : marge, distance, finesse

    private func header(_ p: PilotUi) -> some View {
        let marginColor = p.marginKnown ? (p.marginM >= 0 ? WG.good : WG.bad) : WG.dim
        return HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 0) {
                Text("Marge de sécurité").font(WG.footnote).foregroundStyle(marginColor)
                // une seule ligne, quelle que soit la valeur ; unité en petit dessous
                Text(p.marginText).font(WG.number(40)).foregroundStyle(marginColor)
                    .lineLimit(1).minimumScaleFactor(0.5)
                Text("m").font(WG.caption).foregroundStyle(WG.dim).padding(.top, -4)
                HStack(spacing: 6) { Text("ALT").font(WG.caption).foregroundStyle(WG.dim); Text(p.altText).font(WG.number(16)) }
                HStack(spacing: 6) { Text("SÉCU").font(WG.caption).foregroundStyle(WG.dim); Text(p.secuText).font(WG.number(16)) }
                Text(p.altRefText).font(WG.caption2).foregroundStyle(WG.dim)
            }
            .frame(maxWidth: 130, alignment: .leading)
            Spacer(minLength: 4)
            VStack(spacing: 0) {
                Text("Distance").font(WG.footnote).foregroundStyle(WG.dim)
                Text(p.distanceText).font(WG.number(32)).minimumScaleFactor(0.6).lineLimit(1)
                Text("km").font(WG.caption)
            }
            Spacer(minLength: 4)
            VStack(alignment: .trailing, spacing: 4) {
                HStack(spacing: 4) {
                    Text("Finesse").font(WG.footnote).foregroundStyle(WG.brandInk)
                    Text(p.finesseNote).font(WG.caption2).foregroundStyle(WG.dim)
                }
                .lineLimit(1).fixedSize()
                HStack(spacing: 2) {
                    ForEach([20, 15, 10], id: \.self) { f in
                        Button { app.finesse = f } label: {
                            HStack(alignment: .firstTextBaseline, spacing: 0) {
                                Text("F").font(WG.caption2)
                                Text("\(f)").font(WG.number(20))
                            }
                            .lineLimit(1).fixedSize()
                            .padding(.horizontal, 7).padding(.vertical, 5)
                            .background(app.finesse == f ? WG.sky.opacity(0.15) : .clear, in: RoundedRectangle(cornerRadius: 8))
                            .foregroundStyle(app.finesse == f ? WG.sky : WG.brandInk)
                        }
                    }
                }
                Button { choosingField = true } label: {
                    HStack(spacing: 6) {
                        Text(p.autoField ? "AUTO" : "MANU").font(WG.caption2).foregroundStyle(WG.route)
                        Text(p.fieldCode).font(WG.headline).foregroundStyle(WG.brandInk)
                        Text(p.bearingText).font(WG.number(18)).foregroundStyle(WG.route)
                        Image(systemName: "chevron.down").font(.caption2).foregroundStyle(WG.route)
                    }
                    .lineLimit(1).fixedSize()
                }
                .accessibilityLabel("Choisir le terrain de référence")
            }
        }
        .padding(.horizontal, 14).padding(.top, 6).padding(.bottom, 4)
        .foregroundStyle(WG.brandInk)
    }

    // MARK: vario

    private func varioBar(_ p: PilotUi) -> some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading) {
                Text(p.varioLabel).font(WG.footnote).foregroundStyle(WG.dim)
                Text(p.varioText).font(WG.number(40)).foregroundStyle(WG.vario(p.varioMs)).lineLimit(1).minimumScaleFactor(0.6)
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
        }
        .padding(.horizontal, 14).padding(.top, 14).padding(.bottom, 8)
        .foregroundStyle(WG.brandInk)
    }

    private func avg(_ l: String, _ v: String) -> some View {
        VStack(alignment: .leading) { Text(l).font(WG.caption).foregroundStyle(WG.dim); Text(v).font(WG.number(17)) }
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Petit interrupteur orange d'un panneau (coupe, vario).
struct PanelToggle: View {
    @Binding var isOn: Bool
    let label: String

    var body: some View {
        Toggle(isOn: $isOn) { Text(label).font(WG.caption2).foregroundStyle(WG.dim) }
            .toggleStyle(MiniSwitch())
            .accessibilityLabel(isOn ? "Masquer \(label)" : "Afficher \(label)")
    }
}

/// Interrupteur réduit (≈ 26 × 15 pt), orange de la charte quand il est actif.
struct MiniSwitch: ToggleStyle {
    func makeBody(configuration: Configuration) -> some View {
        Button { configuration.isOn.toggle() } label: {
            HStack(spacing: 4) {
                configuration.label
                Capsule().fill(configuration.isOn ? WG.climb : Color.gray.opacity(0.35))
                    .frame(width: 26, height: 15)
                    .overlay(alignment: configuration.isOn ? .trailing : .leading) {
                        Circle().fill(.white).frame(width: 11, height: 11).padding(2)
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
    @EnvironmentObject var app: AppModel
    @EnvironmentObject var map: MapControl
    let p: PilotUi
    @Binding var showProfile: Bool
    @Binding var showVario: Bool

    var body: some View {
        ZStack {
            // haut gauche : recentrage (AUTO/LIBRE), orientation (AUTO/N↑/RTE), REC compact
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 6) {
                    round(map.follow ? "AUTO" : "LIBRE", a11y: map.follow ? "Carte centrée sur le planeur" : "Recentrer la carte") { map.recenter() }
                    round(map.orientation.label, a11y: "Orientation de la carte : \(map.orientation.label)") { map.cycleOrientation() }
                    Button { app.toggleRecording() } label: {
                        HStack(spacing: 4) {
                            Circle().fill(p.recording ? .white : WG.bad).frame(width: 7, height: 7)
                            Text(p.recording ? p.chronoText : "REC").font(WG.caption2.weight(.bold))
                        }
                        .padding(.horizontal, 8).padding(.vertical, 5)
                        .background(p.recording ? WG.bad : WG.card, in: Capsule())
                        .foregroundStyle(p.recording ? .white : WG.bad)
                        .shadow(color: .black.opacity(0.15), radius: 2)
                    }
                    .accessibilityLabel(p.recording ? "Arrêter l'enregistrement" : "Démarrer l'enregistrement")
                }
                // panneaux masqués : pastilles indépendantes sur la carte
                if !showProfile {
                    HStack(spacing: 4) {
                        Text(p.marginKnown ? "marge \(p.marginText) m" : "coupe").font(WG.caption2)
                        PanelToggle(isOn: $showProfile, label: "")
                    }
                    .padding(.leading, 8).background(WG.card.opacity(0.92), in: Capsule()).shadow(color: .black.opacity(0.12), radius: 2)
                }
                if !showVario {
                    HStack(spacing: 4) {
                        Text("vario").font(WG.caption2).foregroundStyle(WG.dim)
                        Text(p.varioText).font(WG.number(15)).foregroundStyle(WG.vario(p.varioMs))
                        PanelToggle(isOn: $showVario, label: "")
                    }
                    .padding(.leading, 8).background(WG.card.opacity(0.92), in: Capsule()).shadow(color: .black.opacity(0.12), radius: 2)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)

            // bas centre, au-dessus du chrono : mode démo
            if p.demo {
                Text("DÉMO · planeur simulé").font(WG.caption2).padding(.horizontal, 8).padding(.vertical, 4)
                    .background(WG.card.opacity(0.92), in: Capsule())
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom).padding(.bottom, 50)
            }

            // haut droite : DÉMO, vent, échelle vario
            VStack(alignment: .trailing, spacing: 8) {
                Button { app.demo.toggle() } label: {
                    Text("DÉMO").font(WG.caption2.weight(.bold)).padding(.horizontal, 9).padding(.vertical, 5)
                        .background(app.demo ? WG.climb : WG.card, in: Capsule())
                        .foregroundStyle(app.demo ? .white : WG.brandInk).shadow(color: .black.opacity(0.15), radius: 2)
                }
                VStack(spacing: 3) {
                    if p.windKnown {
                        Image(systemName: "arrow.down").font(.subheadline.weight(.semibold)).rotationEffect(.degrees(p.windFromDeg)).foregroundStyle(WG.route)
                        Text(p.windText.replacingOccurrences(of: "/", with: "\n")).font(WG.caption2).multilineTextAlignment(.center)
                    } else {
                        Text("vent\n—").font(WG.caption2).foregroundStyle(WG.dim).multilineTextAlignment(.center)
                    }
                }
                .frame(width: 44).padding(.vertical, 8)
                .background(WG.card, in: RoundedRectangle(cornerRadius: 20)).shadow(color: .black.opacity(0.1), radius: 1)
                .accessibilityLabel(p.windKnown ? "Vent \(p.windText)" : "Vent pas encore estimé : spiralez")
                VStack(spacing: 3) {
                    Text("+4").font(WG.caption2).foregroundStyle(WG.dim)
                    LinearGradient(colors: [WG.vario(4), WG.vario(1.5), WG.vario(0), WG.vario(-3)], startPoint: .top, endPoint: .bottom)
                        .frame(width: 4, height: 50).clipShape(Capsule())
                    Text("−3").font(WG.caption2).foregroundStyle(WG.dim)
                }
                .frame(width: 44)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)

            // bas gauche : états GPS · BARO · DATA
            VStack(alignment: .leading, spacing: 4) {
                badge("GPS", app.status.gps)
                badge("BARO", app.status.baro)
                badge("DATA", app.status.data)
            }
            .accessibilityElement(children: .combine)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading).padding(.bottom, 40)

            // bas centre : chrono du vol
            Text(p.flightTimeText).font(WG.caption.monospacedDigit()).padding(.horizontal, 8).padding(.vertical, 3)
                .background(WG.card.opacity(0.92), in: Capsule())
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom).padding(.bottom, 22)

            // bas droite : zoom
            VStack(spacing: 0) {
                Button { map.zoomIn() } label: { Image(systemName: "plus").frame(width: 40, height: 40) }.accessibilityLabel("Zoom avant")
                Divider().frame(width: 24)
                Button { map.zoomOut() } label: { Image(systemName: "minus").frame(width: 40, height: 40) }.accessibilityLabel("Zoom arrière")
            }
            .font(.title3.weight(.light)).foregroundStyle(WG.brandInk)
            .background(WG.card, in: RoundedRectangle(cornerRadius: 20)).shadow(color: .black.opacity(0.1), radius: 1)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing).padding(.bottom, 22)
        }
        .padding(10)
    }

    private func round(_ label: String, a11y: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(WG.caption2.weight(.semibold)).foregroundStyle(WG.brandInk)
                .frame(width: 40, height: 40).background(WG.card, in: Circle()).shadow(color: .black.opacity(0.15), radius: 2)
        }
        .accessibilityLabel(a11y)
    }

    private func badge(_ label: String, _ ok: Bool) -> some View {
        HStack(spacing: 4) { Circle().fill(ok ? WG.good : WG.bad).frame(width: 6, height: 6); Text(label).font(WG.caption2) }
            .padding(.horizontal, 7).padding(.vertical, 3).background(WG.card, in: Capsule())
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
                Rectangle().fill(WG.brandInk).frame(width: 4, height: g.size.height + 8).offset(x: x - 2)
            }
        }
    }
}

/// Coupe vers le terrain, comme Android : relief (vert), pente de plané depuis l'altitude actuelle jusqu'à l'arrivée au
/// terrain (trait plein, inclinée selon la finesse et le vent), altitude nécessaire (pointillés rouges) jusqu'à
/// terrain + hauteur d'arrivée (« +300 »).
struct ProfileStrip: View {
    let p: PilotUi

    var body: some View {
        let pts = p.profile
        let total = max(pts.last?.sKm ?? 0, p.routeKm)
        let top = max(500, (max(p.currentAltM, p.requiredAltM) / 500).rounded(.up) * 500)
        Chart {
            ForEach(Array(pts.enumerated()), id: \.offset) { _, d in
                AreaMark(x: .value("km", d.sKm), y: .value("relief", d.terrainM)).foregroundStyle(WG.good.opacity(0.35))
            }
            if total > 0 {
                LineMark(x: .value("km", 0.0), y: .value("alt", p.currentAltM), series: .value("s", "plané"))
                    .foregroundStyle(WG.brandInk).lineStyle(StrokeStyle(lineWidth: 2))
                LineMark(x: .value("km", total), y: .value("alt", p.arrivalAltM), series: .value("s", "plané"))
                    .foregroundStyle(WG.brandInk).lineStyle(StrokeStyle(lineWidth: 2))
                LineMark(x: .value("km", 0.0), y: .value("sécu", p.requiredAltM), series: .value("s", "sécu"))
                    .lineStyle(StrokeStyle(lineWidth: 1.5, dash: [5, 4])).foregroundStyle(WG.bad)
                LineMark(x: .value("km", total), y: .value("sécu", p.fieldElevM + 300), series: .value("s", "sécu"))
                    .lineStyle(StrokeStyle(lineWidth: 1.5, dash: [5, 4])).foregroundStyle(WG.bad)
                PointMark(x: .value("km", total), y: .value("sécu", p.fieldElevM + 300))
                    .symbolSize(20).foregroundStyle(WG.route)
                    .annotation(position: .top, alignment: .trailing) { Text("+300").font(WG.caption2).foregroundStyle(WG.bad) }
            }
        }
        .chartXScale(domain: 0...max(total, 0.1))
        .chartYScale(domain: 0...top)
        // axes à gauche comme Android ; en bas seulement « 0 » et l'étiquette de fin (distance · terrain)
        .chartXAxis { AxisMarks(values: .automatic(desiredCount: 3)) { AxisGridLine() } }
        .chartYAxis { AxisMarks(position: .leading, values: .automatic(desiredCount: 3)) { AxisGridLine(); AxisValueLabel() } }
        .overlay(alignment: .topTrailing) {
            if !p.arrivalText.isEmpty { Text(p.arrivalText).font(WG.caption2.weight(.semibold)).foregroundStyle(WG.good).padding(.trailing, 80) }
        }
        .overlay(alignment: .bottomTrailing) {
            if !p.profileEndText.isEmpty { Text(p.profileEndText).font(WG.caption2).foregroundStyle(WG.dim).offset(y: 12) }
        }
        .overlay { if total == 0 { Text("Coupe disponible dès la première position").font(WG.caption).foregroundStyle(WG.dim) } }
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
