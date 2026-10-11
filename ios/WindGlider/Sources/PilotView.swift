import Charts
import MapKit
import SwiftUI
import WindGliderShared

/// Onglet Pilotage (édition Lite), d'après l'écran Android : marge de sécurité, distance et finesse, terrain et cap,
/// coupe vers le terrain, carte (trace colorée par le vario, route, vent, zoom spirale), vario et moyennes, REC.
/// Fond blanc (V18.7). Calculs : moteur commun (FlightEngineCore + SafetyEngine).
struct PilotView: View {
    @EnvironmentObject var app: AppModel
    @State private var choosingField = false

    var body: some View {
        let p = app.pilot
        VStack(spacing: 0) {
            header(p)
            ProfileStrip(p: p).frame(height: 96)
            HStack {
                Text(p.fieldElevText).foregroundStyle(WG.dim)
                Spacer()
                Text(p.arrivalText).foregroundStyle(WG.good)
            }
            .font(WG.caption).padding(.horizontal, 16).padding(.vertical, 2)
            ZStack(alignment: .top) {
                FlightMapView(p: p)
                mapOverlay(p)
            }
            varioBar(p)
        }
        .background(WG.card)
        .sheet(isPresented: $choosingField) { FieldPicker().environmentObject(app).presentationDetents([.medium]) }
        .onAppear { UIApplication.shared.isIdleTimerDisabled = true }
        .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
    }

    // MARK: en-tête : marge, distance, finesse

    private func header(_ p: PilotUi) -> some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Marge de sécurité").font(WG.footnote).foregroundStyle(p.marginKnown ? (p.marginM >= 0 ? WG.good : WG.bad) : WG.dim)
                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    Text(p.marginText).font(WG.number(52)).foregroundStyle(p.marginKnown ? (p.marginM >= 0 ? WG.good : WG.bad) : WG.dim)
                    Text("m").font(WG.subhead).foregroundStyle(WG.dim)
                }
                HStack(spacing: 6) { Text("ALT").font(WG.caption).foregroundStyle(WG.dim); Text(p.altText).font(WG.number(18)) }
                HStack(spacing: 6) { Text("SÉCU").font(WG.caption).foregroundStyle(WG.dim); Text(p.secuText).font(WG.number(18)) }
                Text(p.altRefText).font(WG.caption).foregroundStyle(WG.dim)
            }
            Spacer()
            VStack(spacing: 2) {
                Text("Distance").font(WG.footnote).foregroundStyle(WG.dim)
                Text(p.distanceText).font(WG.number(34)).minimumScaleFactor(0.6).lineLimit(1)
                Text("km").font(WG.footnote)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 6) {
                Text("Finesse · \(p.windText)").font(WG.caption).foregroundStyle(WG.dim).lineLimit(1).fixedSize()
                HStack(spacing: 2) {
                    ForEach([20, 15, 10], id: \.self) { f in
                        Button { app.finesse = f } label: {
                            Text("F\(f)").font(WG.number(20)).lineLimit(1).fixedSize()
                                .padding(.horizontal, 8).padding(.vertical, 6)
                                .background(app.finesse == f ? WG.sky.opacity(0.15) : .clear, in: RoundedRectangle(cornerRadius: 8))
                                .foregroundStyle(app.finesse == f ? WG.sky : WG.brandInk)
                        }
                    }
                }
                Button { choosingField = true } label: {
                    HStack(spacing: 6) {
                        Text(p.autoField ? "AUTO" : "MANU").font(WG.caption).foregroundStyle(WG.route)
                        Text(p.fieldCode).font(WG.headline).foregroundStyle(WG.brandInk)
                        Text(p.bearingText).font(WG.number(18)).foregroundStyle(WG.route)
                        Image(systemName: "chevron.down").font(.caption).foregroundStyle(WG.route)
                    }
                    .lineLimit(1).fixedSize()
                }
                .accessibilityLabel("Choisir le terrain de référence")
            }
        }
        .padding(.horizontal, 16).padding(.top, 8)
        .foregroundStyle(WG.brandInk)
    }

    // MARK: carte : boutons et badges

    private func mapOverlay(_ p: PilotUi) -> some View {
        VStack {
            HStack(alignment: .top) {
                Button { app.toggleRecording() } label: {
                    Label(p.recording ? p.chronoText : "REC", systemImage: p.recording ? "stop.circle.fill" : "record.circle")
                        .font(WG.headline).padding(.horizontal, 12).padding(.vertical, 8)
                        .background(p.recording ? WG.bad : WG.card, in: Capsule())
                        .foregroundStyle(p.recording ? .white : WG.bad)
                        .shadow(radius: 2)
                }
                .accessibilityLabel(p.recording ? "Arrêter l'enregistrement" : "Démarrer l'enregistrement")
                Spacer()
                if p.demo { Text("DÉMO · planeur simulé").font(WG.caption).padding(6).background(WG.card.opacity(0.9), in: Capsule()) }
                Spacer()
                Button { app.demo.toggle() } label: {
                    Text("DÉMO").font(WG.caption.weight(.bold)).padding(.horizontal, 12).padding(.vertical, 8)
                        .background(app.demo ? WG.climb : WG.card, in: Capsule())
                        .foregroundStyle(app.demo ? .white : WG.brandInk).shadow(radius: 2)
                }
            }
            HStack {
                VStack(alignment: .leading, spacing: 6) {
                    badge("GPS", p.gpsOk)
                    badge("BARO", p.baroOk)
                }
                Spacer()
                if p.windKnown {
                    VStack(spacing: 2) {
                        Image(systemName: "arrow.down").rotationEffect(.degrees(p.windFromDeg)).foregroundStyle(WG.route)
                        Text(p.windText).font(WG.caption)
                    }
                    .padding(8).background(WG.card, in: RoundedRectangle(cornerRadius: 12)).shadow(radius: 1)
                }
            }
            Spacer()
            Text(p.phaseText).font(WG.caption).padding(6).background(WG.card.opacity(0.9), in: Capsule())
        }
        .padding(12)
    }

    private func badge(_ label: String, _ ok: Bool) -> some View {
        HStack(spacing: 4) { Circle().fill(ok ? WG.good : WG.bad).frame(width: 7, height: 7); Text(label).font(WG.caption) }
            .padding(.horizontal, 8).padding(.vertical, 4).background(WG.card, in: Capsule())
    }

    // MARK: vario

    private func varioBar(_ p: PilotUi) -> some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading) {
                Text(p.varioLabel).font(WG.footnote).foregroundStyle(WG.dim)
                Text(p.varioText).font(WG.number(44)).foregroundStyle(WG.vario(p.varioMs))
            }
            VStack(spacing: 6) {
                VarioScale(value: p.varioMs).frame(height: 10)
                HStack {
                    avg("Spirale", p.spiralText); avg("Pompe", p.thermalText); avg("Jour", p.dayText)
                }
            }
            Button { app.soundOn.toggle() } label: {
                Image(systemName: app.soundOn ? "speaker.wave.2" : "speaker.slash").font(.title3)
                    .frame(width: 44, height: 44).overlay(RoundedRectangle(cornerRadius: 10).stroke(WG.line))
            }
            .accessibilityLabel(app.soundOn ? "Couper le son du vario" : "Activer le son du vario")
        }
        .padding(.horizontal, 16).padding(.vertical, 10)
        .foregroundStyle(WG.brandInk)
    }

    private func avg(_ l: String, _ v: String) -> some View {
        VStack(alignment: .leading) { Text(l).font(WG.caption).foregroundStyle(WG.dim); Text(v).font(WG.number(18)) }
            .frame(maxWidth: .infinity, alignment: .leading)
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

/// Coupe vers le terrain : altitude actuelle, altitude nécessaire (pointillés), relief (vert) quand il est connu.
struct ProfileStrip: View {
    let p: PilotUi

    var body: some View {
        let pts = p.profile
        let total = pts.last?.sKm ?? 0
        Chart {
            ForEach(Array(pts.enumerated()), id: \.offset) { _, d in
                AreaMark(x: .value("km", d.sKm), y: .value("relief", d.terrainM)).foregroundStyle(WG.good.opacity(0.35))
            }
            if total > 0 {
                LineMark(x: .value("km", 0.0), y: .value("alt", p.currentAltM), series: .value("s", "alt"))
                LineMark(x: .value("km", total), y: .value("alt", p.currentAltM), series: .value("s", "alt"))
                LineMark(x: .value("km", 0.0), y: .value("sécu", p.requiredAltM), series: .value("s", "secu"))
                    .lineStyle(StrokeStyle(lineWidth: 1.5, dash: [5, 4])).foregroundStyle(WG.bad)
                LineMark(x: .value("km", total), y: .value("sécu", (pts.last?.terrainM ?? 0) + 300), series: .value("s", "secu"))
                    .lineStyle(StrokeStyle(lineWidth: 1.5, dash: [5, 4])).foregroundStyle(WG.bad)
            }
        }
        .chartXAxisLabel("km → \(p.fieldCode)", alignment: .trailing)
        .chartYScale(domain: 0...max(500, (max(p.currentAltM, p.requiredAltM) / 500).rounded(.up) * 500))
        .overlay { if total == 0 { Text("Coupe disponible dès la première position").font(WG.caption).foregroundStyle(WG.dim) } }
        .padding(.horizontal, 12)
        .background(WG.brandPaper.opacity(0.5))
    }
}

/// Carte : trace colorée par la montée, planeur, terrain et route ; zoom automatique sur la spirale (ThermalZoom commun).
struct FlightMapView: View {
    let p: PilotUi
    @State private var camera: MapCameraPosition = .automatic

    var body: some View {
        let me = CLLocationCoordinate2D(latitude: p.lat, longitude: p.lon)
        let field = CLLocationCoordinate2D(latitude: p.fieldLat, longitude: p.fieldLon)
        Map(position: $camera) {
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
                    Image(systemName: "airplane").font(.title2).rotationEffect(.degrees(p.trackDeg - 90)).foregroundStyle(WG.brandInk)
                }
            }
        }
        .mapStyle(.standard(elevation: .flat, pointsOfInterest: .excludingAll))
        .onChange(of: cameraKey) { _, _ in updateCamera() }
        .onAppear { updateCamera() }
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
        p.zoomActive ? "z\(Int(p.zoomLat * 2000)),\(Int(p.zoomLon * 2000))" : "p\(Int(p.lat * 500)),\(Int(p.lon * 500)),\(p.hasPosition)"
    }

    private func updateCamera() {
        withAnimation(.easeInOut(duration: 0.9)) {
            if p.zoomActive {
                // cercle de spirale ≈ 2/3 du plus petit côté de la carte, nord en haut (comme Android)
                camera = .camera(MapCamera(centerCoordinate: .init(latitude: p.zoomLat, longitude: p.zoomLon), distance: p.zoomRadiusM * 7, heading: 0))
            } else if p.hasPosition {
                camera = .camera(MapCamera(centerCoordinate: .init(latitude: p.lat, longitude: p.lon), distance: 9000, heading: 0))
            } else if p.fieldLat != 0 {
                camera = .camera(MapCamera(centerCoordinate: .init(latitude: p.fieldLat, longitude: p.fieldLon), distance: 15000, heading: 0))
            }
        }
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
