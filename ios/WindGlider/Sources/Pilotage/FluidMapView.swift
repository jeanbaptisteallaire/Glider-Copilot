import MapKit
import SwiftUI
import WindGliderShared

/// V3 — carte de vol fluide (docs/ios/V3-FLUIDITE.md) : MKMapView piloté à chaque image (CADisplayLink), planeur
/// prédit entre deux positions GPS (OwnshipPredictor), cap et zoom lissés, contenu mis à jour par différence.
/// Même contenu et même comportement que FlightMapView (V2) : trace colorée, route, terrain, espaces aériens, OGN.
struct FluidMapView: UIViewRepresentable {
    let p: PilotUi
    var airspaces: [AirspaceShape] = []
    var traffic: TrafficSnapshot?
    let predictor: OwnshipPredictor
    /// Hauteur du panneau vario posé sur la carte : l'échelle et le logo Plans restent au-dessus (la carte, elle,
    /// ne change pas de taille, le planeur ne bouge pas à l'écran).
    var bottomInset: CGFloat = 0
    /// Mode sombre de Pilotage : fond de carte sombre d'Apple, route et terrain en violet fluo très clair.
    var dark = false
    @EnvironmentObject var map: MapControl

    func makeCoordinator() -> Coordinator { Coordinator(predictor: predictor, control: map) }

    func makeUIView(context: Context) -> MKMapView {
        let v = MKMapView()
        let cfg = MKStandardMapConfiguration(elevationStyle: .flat)
        cfg.pointOfInterestFilter = .excludingAll
        v.preferredConfiguration = cfg
        v.showsCompass = false
        v.showsScale = false
        v.isPitchEnabled = false
        v.delegate = context.coordinator
        // échelle en bas à gauche, sous les pastilles GPS · BARO · DATA, juste au-dessus du logo Plans
        let scale = MKScaleView(mapView: v)
        scale.scaleVisibility = .hidden   // épuré : aucune utilité en vol
        scale.legendAlignment = .leading
        scale.translatesAutoresizingMaskIntoConstraints = false
        v.addSubview(scale)
        let bottom = scale.bottomAnchor.constraint(equalTo: v.safeAreaLayoutGuide.bottomAnchor, constant: -36)
        context.coordinator.scaleBottom = bottom
        NSLayoutConstraint.activate([scale.leadingAnchor.constraint(equalTo: v.leadingAnchor, constant: 10), bottom])
        // un geste du pilote (glisser, pincer, tourner) passe la carte en LIBRE
        for g in [UIPanGestureRecognizer(), UIPinchGestureRecognizer(), UIRotationGestureRecognizer()] as [UIGestureRecognizer] {
            g.addTarget(context.coordinator, action: #selector(Coordinator.userGesture(_:)))
            g.delegate = context.coordinator
            v.addGestureRecognizer(g)
        }
        v.addAnnotation(context.coordinator.ownship)
        context.coordinator.attach(v)
        return v
    }

    func updateUIView(_ v: MKMapView, context: Context) {
        context.coordinator.update(p: p, airspaces: airspaces, traffic: traffic, on: v)
        // mode sombre : même fond de carte (couleurs, contraste) qu'en mode clair ; seule la route change de couleur
        if context.coordinator.lastDark != dark {
            context.coordinator.lastDark = dark
            // LIGHT PFD : en sombre, fond satellite ombré (relief lisible, trace « flash » bien détachée) ;
            // en clair, plan standard inchangé
            if dark {
                let cfg = MKHybridMapConfiguration(elevationStyle: .flat)
                cfg.pointOfInterestFilter = .excludingAll
                v.preferredConfiguration = cfg
            } else {
                let cfg = MKStandardMapConfiguration(elevationStyle: .flat)
                cfg.pointOfInterestFilter = .excludingAll
                v.preferredConfiguration = cfg
            }
            context.coordinator.restyle(on: v)
        }
        // V5 : échelle et mentions Plans juste au-dessus du panneau vario (plus cachées derrière)
        let lift = -(bottomInset + 30)   // au-dessus du logo Plans
        if context.coordinator.scaleBottom?.constant != lift {
            context.coordinator.scaleBottom?.constant = lift
        }
        if v.layoutMargins.bottom != bottomInset {
            v.layoutMargins = UIEdgeInsets(top: v.layoutMargins.top, left: v.layoutMargins.left, bottom: bottomInset, right: v.layoutMargins.right)
        }
    }

    static func dismantleUIView(_ v: MKMapView, coordinator: Coordinator) { coordinator.detach() }

    // MARK: -

    final class Coordinator: NSObject, MKMapViewDelegate, UIGestureRecognizerDelegate {
        let predictor: OwnshipPredictor
        var scaleBottom: NSLayoutConstraint?
        var lastDark: Bool?
        let control: MapControl
        let ownship = OwnshipAnnotation()
        private weak var view: MKMapView?
        private var link: CADisplayLink?
        private var p: PilotUi?
        // caméra lissée
        private var camHeading = 0.0, camDistance = 9000.0
        private var camCenter: CLLocationCoordinate2D?
        private var lastFrame = CACurrentMediaTime()
        // contenu déjà posé
        private var airspaceIds: [String] = []
        private var traceVersion: Int64 = -1
        private var traceOverlays: [MKOverlay] = []
        private let route = RouteOverlay()
        private weak var routeRenderer: RouteRenderer?
        private var routeAdded = false
        private var lastRouteRect = MKMapRect.null
        private var field = MKPointAnnotation()
        private var planes: [String: PlaneAnnotation] = [:]
        private var pumps: [String: PumpAnnotation] = [:]
        private var airspaceShapes: [AirspaceShape] = []
        /// Sommets des zones convertis une seule fois (revue énergie 3 : plus d'accès KotlinDouble à 4 Hz) + boîte englobante.
        private var airspaceGeo: [(id: String, shape: AirspaceShape, pts: [(Double, Double)], box: (Double, Double, Double, Double))] = []
        private var lastLabelsAt = 0.0
        private weak var lastTraffic: TrafficSnapshot?
        private var lastCamera: (lat: Double, lon: Double, dist: Double, head: Double)?
        private var airspaceLabels: [String: AirspaceLabel] = [:]

        init(predictor: OwnshipPredictor, control: MapControl) {
            self.predictor = predictor
            self.control = control
            camDistance = control.distance
        }

        func attach(_ v: MKMapView) {
            view = v
            let l = CADisplayLink(target: self, selector: #selector(frame))
            var fps: Float = 30   // 30 i/s : batterie (JB)
            #if DEBUG
            // mesures d'énergie : `-benchFps N` impose une autre cadence
            if let i = ProcessInfo.processInfo.arguments.firstIndex(of: "-benchFps"), i + 1 < ProcessInfo.processInfo.arguments.count,
               let f = Float(ProcessInfo.processInfo.arguments[i + 1]) { fps = f }
            #endif
            l.preferredFrameRateRange = CAFrameRateRange(minimum: fps, maximum: fps, preferred: fps)
            l.add(to: .main, forMode: .common)
            link = l
        }

        func detach() { link?.invalidate(); link = nil }

        @objc func userGesture(_ g: UIGestureRecognizer) {
            if g.state == .began { control.follow = false }
            // fin d'un geste : le zoom choisi par le pilote devient le zoom de référence ; « recentrer » ne fait
            // alors que ramener le planeur au centre, sans rezoomer
            if g.state == .ended || g.state == .cancelled, let v = view {
                let d = min(max(v.camera.centerCoordinateDistance, 600), 200_000)
                camDistance = d
                camHeading = v.camera.heading
                if !(p?.zoomActive ?? false) { control.distance = d }
            }
        }

        func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }

        // MARK: image par image

        @objc private func frame() {
            // onglet Pilotage caché : aucune image (la carte tournait à 30 i/s derrière Prévol)
            guard let v = view, let p, v.window != nil else { return }
            let now = CACurrentMediaTime()
            let dt = min(now - lastFrame, 0.1)
            lastFrame = now
            let s = predictor.state(at: Date().timeIntervalSince1970)
            let me = s.valid ? s.coordinate : CLLocationCoordinate2D(latitude: p.lat, longitude: p.lon)
            let track = s.valid ? s.course : p.trackDeg
            if p.hasPosition { ownship.coordinate = me }

            guard control.follow else {
                (v.view(for: ownship) as? OwnshipView)?.rotate(track - v.camera.heading)
                return
            }
            // cible : spirale (centre du thermique), planeur, ou terrain sans position
            let target: CLLocationCoordinate2D
            var distance = control.distance
            if p.zoomActive {
                target = .init(latitude: p.zoomLat, longitude: p.zoomLon)
                distance = p.zoomRadiusM * 7 * control.distance / 9000
            } else if p.hasPosition {
                target = me
            } else if p.fieldLat != 0 || p.fieldLon != 0 {
                target = .init(latitude: p.fieldLat, longitude: p.fieldLon)
                distance = max(distance, 15000)
            } else { return }
            let heading: Double = switch control.orientation {
            case .north: 0
            case .track: track
            case .auto: (p.zoomActive || p.circling) ? 0 : track
            }
            // lissage exponentiel : cap ~0,4 s, zoom ~0,3 s, centre de spirale ~0,5 s ; planeur suivi sans retard
            camHeading = OwnshipPredictor.norm(camHeading + OwnshipPredictor.angleDiff(heading, camHeading) * (1 - exp(-dt / 0.4)))
            camDistance += (distance - camDistance) * (1 - exp(-dt / 0.3))
            if p.zoomActive, let c = camCenter {
                let k = 1 - exp(-dt / 0.5)
                camCenter = .init(latitude: c.latitude + (target.latitude - c.latitude) * k, longitude: c.longitude + (target.longitude - c.longitude) * k)
            } else if p.hasPosition, v.bounds.height > 0 {
                // planeur un peu plus bas que le centre (10 % de l'espace sous le centre, soit 5 % de la hauteur) :
                // le centre de la carte est avancé d'autant dans la direction du haut de l'écran
                let mid = CGPoint(x: v.bounds.midX, y: v.bounds.midY)
                let a = v.convert(mid, toCoordinateFrom: v), b = v.convert(CGPoint(x: mid.x, y: mid.y + 100), toCoordinateFrom: v)
                let mPerPt = CLLocation(latitude: a.latitude, longitude: a.longitude).distance(from: CLLocation(latitude: b.latitude, longitude: b.longitude)) / 100
                let ahead = Double(v.bounds.height) * 0.05 * mPerPt
                let h = camHeading * .pi / 180
                let (lat, lon) = OwnshipPredictor.offset(lat: target.latitude, lon: target.longitude, east: ahead * sin(h), north: ahead * cos(h))
                camCenter = .init(latitude: lat, longitude: lon)
            } else {
                camCenter = target
            }
            // planeur immobile (au sol, démo arrêtée) : on ne redemande pas d'image à MapKit pour rien
            let c = camCenter!
            if let l = lastCamera, abs(l.lat - c.latitude) < 1e-7, abs(l.lon - c.longitude) < 1e-7,
               abs(l.dist - camDistance) < camDistance * 0.001, abs(OwnshipPredictor.angleDiff(l.head, camHeading)) < 0.05 {
                (v.view(for: ownship) as? OwnshipView)?.rotate(track - camHeading)
                return
            }
            lastCamera = (c.latitude, c.longitude, camDistance, camHeading)
            v.camera = MKMapCamera(lookingAtCenter: c, fromDistance: camDistance, pitch: 0, heading: camHeading)
            (v.view(for: ownship) as? OwnshipView)?.rotate(track - camHeading)
        }

        // MARK: contenu (4 Hz, par différence)

        /// Changement clair / sombre : la route et le drapeau du terrain reprennent la couleur du mode.
        func restyle(on v: MKMapView) {
            routeRenderer?.setNeedsDisplay()
            for a in v.annotations where a is MKPointAnnotation {
                v.view(for: a)?.image = UIImage(systemName: "flag.fill")?.withTintColor(UIColor(PI.route), renderingMode: .alwaysOriginal)
            }
        }

        func update(p: PilotUi, airspaces: [AirspaceShape], traffic: TrafficSnapshot?, on v: MKMapView) {
            let first = self.p == nil
            self.p = p
            if first { camDistance = control.distance }
            // espaces aériens : une fois (ou si le pack change)
            let ids = airspaces.map(\.id)
            if ids != airspaceIds {
                v.removeOverlays(v.overlays.filter { $0 is AirspaceOverlay })
                v.addOverlays(airspaces.map { AirspaceOverlay(shape: $0) }, level: .aboveRoads)
                airspaceIds = ids
                airspaceShapes = airspaces
                airspaceGeo = airspaces.map { a in
                    let k = a.coords.map(\.doubleValue)
                    let pts = stride(from: 0, to: k.count - 1, by: 2).map { (k[$0], k[$0 + 1]) }
                    let box = pts.reduce((90.0, 180.0, -90.0, -180.0)) { b, q in (min(b.0, q.0), min(b.1, q.1), max(b.2, q.0), max(b.3, q.1)) }
                    return (a.id, a, pts, box)
                }
                lastLabelsAt = 0
            }
            // noms des zones : 1 fois par seconde suffit (le planeur fait ~30 m)
            let now = CACurrentMediaTime()
            if now - lastLabelsAt >= 1 { lastLabelsAt = now; placeAirspaceLabels(on: v) }
            // trace : seulement quand un point est ajouté (version = instant du dernier point ; le nombre de points
            // plafonne à 600 et ne suffit pas à voir qu'elle avance)
            if p.traceVersion != traceVersion {
                traceVersion = p.traceVersion
                v.removeOverlays(traceOverlays)
                traceOverlays = Self.segments(p.trace)
                v.addOverlays(traceOverlays, level: .aboveLabels)
            }
            // route vers le terrain : un seul calque, redessiné (pas recréé) à chaque mise à jour
            if !routeAdded { v.addOverlay(route, level: .aboveLabels); routeAdded = true }
            route.from = nil
            if p.fieldLat != 0 || p.fieldLon != 0 {
                let fieldC = CLLocationCoordinate2D(latitude: p.fieldLat, longitude: p.fieldLon)
                if field.coordinate.latitude != fieldC.latitude || field.coordinate.longitude != fieldC.longitude || field.title != p.fieldCode {
                    v.removeAnnotation(field)
                    field = MKPointAnnotation(); field.coordinate = fieldC; field.title = p.fieldCode
                    v.addAnnotation(field)
                }
                if p.hasPosition {
                    route.from = ownship.coordinate.latitude == 0 ? CLLocationCoordinate2D(latitude: p.lat, longitude: p.lon) : ownship.coordinate
                    route.to = fieldC
                }
            }
            // ne redessiner que la zone de la ligne (avant / après), et seulement si elle a bougé (revue énergie 7)
            let rect = route.lineRect
            if !MKMapRectEqualToRect(rect, lastRouteRect) {
                if let r = routeRenderer {
                    if !lastRouteRect.isNull { r.setNeedsDisplay(lastRouteRect) }
                    if !rect.isNull { r.setNeedsDisplay(rect) }
                }
                lastRouteRect = rect
            }
            // trafic OGN : seulement quand l'instantané change (1 Hz), pas à chaque tour de 4 Hz
            guard traffic !== lastTraffic else { return }
            lastTraffic = traffic
            let planeList = traffic?.planes ?? []
            let seen = Set(planeList.map(\.id))
            for (id, a) in planes where !seen.contains(id) { v.removeAnnotation(a); planes[id] = nil }
            for t in planeList {
                if let a = planes[t.id] {
                    a.plane = t
                    UIView.animate(withDuration: 0.25) { a.coordinate = .init(latitude: t.lat, longitude: t.lon) }
                    (v.view(for: a) as? PlaneView)?.configure(t, mapHeading: v.camera.heading)
                } else {
                    let a = PlaneAnnotation(plane: t)
                    planes[t.id] = a
                    v.addAnnotation(a)
                }
            }
            let pumpList = traffic?.pumps ?? []
            let pumpKeys = Set(pumpList.map { "\($0.lat),\($0.lon)" })
            for (k, a) in pumps where !pumpKeys.contains(k) { v.removeAnnotation(a); pumps[k] = nil }
            for t in pumpList where pumps["\(t.lat),\(t.lon)"] == nil {
                let a = PumpAnnotation(pump: t)
                pumps["\(t.lat),\(t.lon)"] = a
                v.addAnnotation(a)
            }
        }

        /// Nom des zones proches, posé sur leur bord au point le plus près du planeur (comme les étiquettes d'Android).
        private func placeAirspaceLabels(on v: MKMapView) {
            let c = ownship.coordinate.latitude != 0 ? ownship.coordinate : v.centerCoordinate
            let span = max(v.region.span.latitudeDelta, 0.05)
            var keep = Set<String>()
            let cosLat = cos(c.latitude * .pi / 180)
            for g in airspaceGeo {
                let a = g.shape
                // zone loin de la vue : pas de parcours de ses sommets
                let m = span * 0.6
                if c.latitude < g.box.0 - m || c.latitude > g.box.2 + m || c.longitude < g.box.1 - m / cosLat || c.longitude > g.box.3 + m / cosLat { continue }
                var best = (d: Double.infinity, lat: 0.0, lon: 0.0)
                for (la, lo) in g.pts {
                    let d = abs(la - c.latitude) + abs(lo - c.longitude) * cosLat
                    if d < best.d { best = (d, la, lo) }
                }
                guard best.d < span * 0.6 else { continue }
                keep.insert(a.id)
                if let l = airspaceLabels[a.id] {
                    l.coordinate = .init(latitude: best.lat, longitude: best.lon)
                } else {
                    let l = AirspaceLabel(shape: a, at: .init(latitude: best.lat, longitude: best.lon))
                    airspaceLabels[a.id] = l
                    v.addAnnotation(l)
                }
            }
            for (id, l) in airspaceLabels where !keep.contains(id) { v.removeAnnotation(l); airspaceLabels[id] = nil }
        }

        private static func segments(_ trace: [TraceDot]) -> [MKOverlay] {
            let fade = overlapFade(trace)
            var out: [MKOverlay] = []
            var cur: [CLLocationCoordinate2D] = []
            var key = (Int.min, -1)
            for (i, d) in trace.enumerated() {
                let k = (Int((d.climbMs * 2).rounded()), fade[i])
                let c = CLLocationCoordinate2D(latitude: d.lat, longitude: d.lon)
                if k != key, let last = cur.last {
                    out.append(TraceOverlay(coordinates: cur, count: cur.count).with(climb: Double(key.0) / 2, fade: key.1)); cur = [last]
                }
                key = k; cur.append(c)
            }
            if cur.count > 1 { out.append(TraceOverlay(coordinates: cur, count: cur.count).with(climb: Double(key.0) / 2, fade: key.1)) }
            if trace.count > 1 {
                var all = trace.map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lon) }
                out.insert(TraceCasing(coordinates: &all, count: all.count), at: 0)
            }
            return out
        }

        /// Traces superposées (spirales) : un passage recouvert par des tours plus récents s'estompe un peu à chaque
        /// tour, sans jamais disparaître (niveau 0 = net … 5 = le plus fade, voir TraceOverlay.alpha). Une trace qui
        /// n'est recouverte par rien reste nette, même après une heure de vol.
        private static func overlapFade(_ trace: [TraceDot]) -> [Int] {
            let n = trace.count
            var fade = [Int](repeating: 0, count: n)
            guard n > 30 else { return fade }
            let cell = 30.0   // m
            let lat0 = trace[0].lat * .pi / 180
            func key(_ d: TraceDot) -> (Int, Int) {
                (Int((d.lon * 111_320 * cos(lat0) / cell).rounded(.down)), Int((d.lat * 110_574 / cell).rounded(.down)))
            }
            var grid: [Int: [Int]] = [:]
            func h(_ x: Int, _ y: Int) -> Int { x &* 73_856_093 ^ y &* 19_349_663 }
            for i in 0..<n { let (x, y) = key(trace[i]); grid[h(x, y), default: []].append(i) }
            for i in 0..<n {
                let (x, y) = key(trace[i])
                var later: [Int] = []
                for dx in -1...1 { for dy in -1...1 { for j in grid[h(x + dx, y + dy)] ?? [] where j > i + 15 { later.append(j) } } }
                guard !later.isEmpty else { continue }
                later.sort()
                // passages distincts plus récents : indices séparés de plus de 15 s
                var passes = 1, prev = later[0]
                for j in later.dropFirst() { if j - prev > 15 { passes += 1 }; prev = j }
                fade[i] = min(passes, 5)
            }
            return fade
        }

        // MARK: rendu

        func mapView(_ m: MKMapView, rendererFor o: MKOverlay) -> MKOverlayRenderer {
            if let t = o as? TraceOverlay {
                // couleur « flash » saturée (LIGHT PFD) ; un passage recouvert garde sa teinte, plus fine
                let r = MKPolylineRenderer(polyline: t); r.strokeColor = UIColor(WG.varioFlash(t.climb)).withAlphaComponent(t.alpha)
                r.lineWidth = t.fade == 0 ? 5 : 4; r.lineCap = .round; r.lineJoin = .round; return r
            }
            if let t = o as? TraceCasing {
                // liseré sombre sous toute la trace : elle ressort sur le relief ombré comme sur le fond clair
                let r = MKPolylineRenderer(polyline: t); r.strokeColor = UIColor(WG.casing).withAlphaComponent(0.85)
                r.lineWidth = 8; r.lineCap = .round; r.lineJoin = .round; return r
            }
            if let t = o as? RouteOverlay {
                let r = RouteRenderer(overlay: t); routeRenderer = r; return r
            }
            if let a = o as? AirspaceOverlay {
                let r = MKPolygonRenderer(polygon: a.polygon)
                let c = UIColor(WG.airspace(a.family))
                // Godspeed : trait de 1 pt (JB : 2 pt trop épais) (plein ou tirets selon la famille) + voile léger ; visibles sur satellite
                r.strokeColor = c; r.fillColor = c.withAlphaComponent(WG.airspaceFill(a.family)); r.lineWidth = 1
                let dash = WG.airspaceDash(a.family)
                if !dash.isEmpty { r.lineDashPattern = dash.map { NSNumber(value: Double($0)) } }
                return r
            }
            return MKOverlayRenderer(overlay: o)
        }

        func mapView(_ m: MKMapView, viewFor a: MKAnnotation) -> MKAnnotationView? {
            switch a {
            case let o as OwnshipAnnotation:
                return m.dequeueReusableAnnotationView(withIdentifier: "own") ?? OwnshipView(annotation: o, reuseIdentifier: "own")
            case let pl as PlaneAnnotation:
                let v = (m.dequeueReusableAnnotationView(withIdentifier: "plane") as? PlaneView) ?? PlaneView(annotation: pl, reuseIdentifier: "plane")
                v.annotation = pl; v.configure(pl.plane, mapHeading: m.camera.heading); return v
            case let pu as PumpAnnotation:
                let v = (m.dequeueReusableAnnotationView(withIdentifier: "pump") as? PumpView) ?? PumpView(annotation: pu, reuseIdentifier: "pump")
                v.annotation = pu; v.configure(pu.pump); return v
            case let l as AirspaceLabel:
                let v = m.dequeueReusableAnnotationView(withIdentifier: "asp") ?? MKAnnotationView(annotation: l, reuseIdentifier: "asp")
                v.annotation = l
                v.subviews.forEach { $0.removeFromSuperview() }
                let t = UILabel()
                t.text = l.shape.name
                // Godspeed : nom en 13 pt, couleur de la famille, halo sombre ; MapKit écarte les étiquettes qui se chevauchent
                t.font = .systemFont(ofSize: 13, weight: .semibold)
                t.textColor = UIColor(WG.airspace(l.shape.family))
                t.layer.shadowColor = UIColor.black.cgColor; t.layer.shadowRadius = 2; t.layer.shadowOpacity = 0.9; t.layer.shadowOffset = .zero
                // halo calculé une fois puis gardé en image (sinon rendu hors écran à chaque image de la carte)
                t.layer.shouldRasterize = true; t.layer.rasterizationScale = UIScreen.main.scale
                t.sizeToFit()
                v.frame = t.bounds; v.addSubview(t)
                v.displayPriority = .defaultLow; v.collisionMode = .rectangle
                return v
            case let f as MKPointAnnotation:
                let v = m.dequeueReusableAnnotationView(withIdentifier: "field") ?? MKAnnotationView(annotation: f, reuseIdentifier: "field")
                v.annotation = f
                v.image = UIImage(systemName: "flag.fill")?.withTintColor(UIColor(PI.route), renderingMode: .alwaysOriginal)
                v.displayPriority = .required
                return v
            default: return nil
            }
        }
    }
}

// MARK: - modèles de carte

final class OwnshipAnnotation: NSObject, MKAnnotation {
    @objc dynamic var coordinate = CLLocationCoordinate2D(latitude: 0, longitude: 0)
}

final class PlaneAnnotation: NSObject, MKAnnotation {
    @objc dynamic var coordinate: CLLocationCoordinate2D
    var plane: TrafficPlane
    init(plane: TrafficPlane) { self.plane = plane; coordinate = .init(latitude: plane.lat, longitude: plane.lon) }
}

final class PumpAnnotation: NSObject, MKAnnotation {
    let coordinate: CLLocationCoordinate2D
    let pump: TrafficPump
    init(pump: TrafficPump) { self.pump = pump; coordinate = .init(latitude: pump.lat, longitude: pump.lon) }
}

final class AirspaceLabel: NSObject, MKAnnotation {
    @objc dynamic var coordinate: CLLocationCoordinate2D
    let shape: AirspaceShape
    init(shape: AirspaceShape, at c: CLLocationCoordinate2D) { self.shape = shape; coordinate = c }
}

/// Liseré sombre sous la trace entière (un seul tracé, sans anneaux aux jonctions des tronçons).
final class TraceCasing: MKPolyline {}

final class TraceOverlay: MKPolyline {
    var climb = 0.0
    var fade = 0
    /// 1 tour plus récent par-dessus : 0,8 ; puis 0,68, 0,58, 0,5 ; plancher 0,45 (toujours visible).
    var alpha: CGFloat { [1.0, 0.8, 0.68, 0.58, 0.5, 0.45][min(fade, 5)] }
    func with(climb: Double, fade: Int = 0) -> TraceOverlay { self.climb = climb; self.fade = fade; return self }
}

/// Route vers le terrain : calque unique couvrant le monde, extrémités modifiables (V3 : trait deux fois plus fin).
final class RouteOverlay: NSObject, MKOverlay {
    var from: CLLocationCoordinate2D?
    var to = CLLocationCoordinate2D(latitude: 0, longitude: 0)
    var coordinate: CLLocationCoordinate2D { to }
    var boundingMapRect: MKMapRect { .world }
    /// Rectangle de la ligne, arrondi à ~50 m (évite de redessiner pour un déplacement invisible), marge pour le trait.
    var lineRect: MKMapRect {
        guard let from else { return .null }
        let a = MKMapPoint(from), b = MKMapPoint(to)
        let q = 50 * MKMapPointsPerMeterAtLatitude(to.latitude)
        func r(_ v: Double) -> Double { (v / q).rounded() * q }
        let rect = MKMapRect(x: r(min(a.x, b.x)), y: r(min(a.y, b.y)), width: r(abs(a.x - b.x)), height: r(abs(a.y - b.y)))
        return rect.insetBy(dx: -4 * q, dy: -4 * q)
    }
}

final class RouteRenderer: MKOverlayRenderer {
    override func draw(_ mapRect: MKMapRect, zoomScale: MKZoomScale, in ctx: CGContext) {
        guard let r = overlay as? RouteOverlay, let from = r.from else { return }
        let a = point(for: MKMapPoint(from)), b = point(for: MKMapPoint(r.to))
        ctx.setStrokeColor(UIColor(PI.route).cgColor)
        ctx.setLineWidth(1 / zoomScale)
        ctx.setLineDash(phase: 0, lengths: [4 / zoomScale, 4 / zoomScale])
        ctx.move(to: a); ctx.addLine(to: b); ctx.strokePath()
    }
}

final class AirspaceOverlay: NSObject, MKOverlay {
    let polygon: MKPolygon
    let family: Int32
    init(shape: AirspaceShape) {
        let c = shape.coords.map { $0.doubleValue }
        var pts = stride(from: 0, to: c.count - 1, by: 2).map { CLLocationCoordinate2D(latitude: c[$0], longitude: c[$0 + 1]) }
        polygon = MKPolygon(coordinates: &pts, count: pts.count)
        family = shape.family
    }
    var coordinate: CLLocationCoordinate2D { polygon.coordinate }
    var boundingMapRect: MKMapRect { polygon.boundingMapRect }
}

/// Planeur (V3) : silhouette vectorielle de planeur vue de dessus — grande envergure, fuselage fin, verrière, empennage
/// en T —, fond blanc et très fin contour noir. Nez vers le haut, tourné vers la route à chaque image.
final class OwnshipView: MKAnnotationView {
    private let icon = GliderIcon(frame: CGRect(x: 0, y: 0, width: 46, height: 46))
    override init(annotation: MKAnnotation?, reuseIdentifier: String?) {
        super.init(annotation: annotation, reuseIdentifier: reuseIdentifier)
        frame = CGRect(x: 0, y: 0, width: 46, height: 46)
        addSubview(icon)
        displayPriority = .required
        zPriority = .max
        collisionMode = .none
        accessibilityLabel = tr("Mon planeur")
    }
    required init?(coder: NSCoder) { fatalError() }
    func rotate(_ deg: Double) { icon.transform = CGAffineTransform(rotationAngle: deg * .pi / 180) }
}

final class GliderIcon: UIView {
    override init(frame: CGRect) { super.init(frame: frame); isOpaque = false; backgroundColor = .clear }
    required init?(coder: NSCoder) { fatalError() }

    /// Pièces du planeur en coordonnées unitaires (0…1), nez en haut, centre de gravité au centre de la vue.
    private func parts(in r: CGRect) -> [UIBezierPath] {
        func pt(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: r.minX + x * r.width, y: r.minY + y * r.height) }
        // ailes : bord d'attaque droit, effilées vers les saumons arrondis
        let wing = UIBezierPath()
        wing.move(to: pt(0.50, 0.37))
        wing.addLine(to: pt(0.02, 0.41))
        wing.addQuadCurve(to: pt(0.02, 0.45), controlPoint: pt(0.00, 0.43))
        wing.addLine(to: pt(0.50, 0.49))
        wing.addLine(to: pt(0.98, 0.45))
        wing.addQuadCurve(to: pt(0.98, 0.41), controlPoint: pt(1.00, 0.43))
        wing.close()
        // fuselage : nez arrondi, verrière plus large, poutre de queue fine
        let body = UIBezierPath()
        body.move(to: pt(0.50, 0.14))
        body.addQuadCurve(to: pt(0.535, 0.30), controlPoint: pt(0.545, 0.17))
        body.addQuadCurve(to: pt(0.512, 0.52), controlPoint: pt(0.530, 0.44))
        body.addLine(to: pt(0.506, 0.86))
        body.addLine(to: pt(0.494, 0.86))
        body.addLine(to: pt(0.488, 0.52))
        body.addQuadCurve(to: pt(0.465, 0.30), controlPoint: pt(0.470, 0.44))
        body.addQuadCurve(to: pt(0.50, 0.14), controlPoint: pt(0.455, 0.17))
        body.close()
        // stabilisateur (empennage en T)
        let tail = UIBezierPath(roundedRect: CGRect(origin: pt(0.36, 0.83), size: CGSize(width: 0.28 * r.width, height: 0.045 * r.height)),
                                cornerRadius: 0.02 * r.height)
        return [wing, body, tail]
    }

    override func draw(_ rect: CGRect) {
        let r = bounds.insetBy(dx: 1.5, dy: 1.5)
        let paths = parts(in: r)
        // contour de la silhouette entière : trait noir sous les pièces, puis remplissage blanc par-dessus
        UIColor.black.setStroke()
        for p in paths { p.lineWidth = 1.2; p.lineJoinStyle = .round; p.stroke() }
        UIColor.white.setFill()
        for p in paths { p.fill() }
    }
}

/// Aéronef OGN : flèche orientée (orange en spirale), étiquette DDB ou repli court, écart d'altitude (comme la V2).
final class PlaneView: MKAnnotationView {
    private let arrow = UIImageView()
    private let label = PaddedLabel()
    override init(annotation: MKAnnotation?, reuseIdentifier: String?) {
        super.init(annotation: annotation, reuseIdentifier: reuseIdentifier)
        frame = CGRect(x: 0, y: 0, width: 120, height: 36)
        arrow.frame = CGRect(x: 52, y: 0, width: 16, height: 16); arrow.contentMode = .center
        // Godspeed : flèche blanche et étiquette blanche à halo sombre, sans cartouche, lisibles sur le satellite
        label.font = .monospacedDigitSystemFont(ofSize: 13, weight: .semibold)
        label.textColor = .white; label.backgroundColor = .clear
        for l in [arrow.layer, label.layer] { l.shadowColor = UIColor.black.cgColor; l.shadowOpacity = 0.9; l.shadowRadius = 1.5; l.shadowOffset = .zero }
        label.layer.shouldRasterize = true; label.layer.rasterizationScale = UIScreen.main.scale   // étiquette fixe : halo mis en cache
        label.textAlignment = .center
        addSubview(arrow); addSubview(label)
        collisionMode = .rectangle
    }
    required init?(coder: NSCoder) { fatalError() }
    func configure(_ t: TrafficPlane, mapHeading: Double) {
        arrow.image = UIImage(systemName: "location.north.fill", withConfiguration: UIImage.SymbolConfiguration(pointSize: 15))?
            .withTintColor(t.circling ? UIColor(WG.climb) : .white, renderingMode: .alwaysOriginal)
        arrow.transform = CGAffineTransform(rotationAngle: ((t.trackDeg?.doubleValue ?? 0) - mapHeading) * .pi / 180)
        let name = t.fullLabel.isEmpty ? (t.shortLabel.isEmpty ? t.typeLabel : t.shortLabel) : t.fullLabel
        label.text = name + (t.relativeAltText.isEmpty ? "" : " " + t.relativeAltText)
        label.sizeToFit()
        label.frame = CGRect(x: (bounds.width - label.frame.width - 8) / 2, y: 17, width: label.frame.width + 8, height: 16)
        alpha = t.ageS > 60 ? 0.5 : 1
        accessibilityLabel = tr("Aéronef %@", t.fullLabel.isEmpty ? trDyn(t.typeLabel) : t.fullLabel)
    }
}

/// Pompe du réseau : cercle coloré selon la montée moyenne, valeur au centre (comme la V2).
final class PumpView: MKAnnotationView {
    private let label = UILabel()
    override init(annotation: MKAnnotation?, reuseIdentifier: String?) {
        super.init(annotation: annotation, reuseIdentifier: reuseIdentifier)
        frame = CGRect(x: 0, y: 0, width: 30, height: 30)
        layer.cornerRadius = 15; layer.borderWidth = 2
        label.frame = bounds; label.textAlignment = .center
        label.font = UIFont(name: "Inter-Bold", size: 10) ?? .boldSystemFont(ofSize: 10)
        label.textColor = UIColor(WG.brandInk)
        addSubview(label)
    }
    required init?(coder: NSCoder) { fatalError() }
    func configure(_ t: TrafficPump) {
        let c = UIColor(WG.varioFlash(t.climbMs))
        layer.borderColor = c.cgColor; backgroundColor = c.withAlphaComponent(0.25)
        let f = NumberFormatter(); f.locale = Locale(identifier: "fr_FR"); f.minimumFractionDigits = 1; f.maximumFractionDigits = 1
        label.text = (t.climbMs >= 0 ? "+" : "") + (f.string(from: NSNumber(value: t.climbMs)) ?? "")
        accessibilityLabel = tr("Pompe %@ mètres par seconde", label.text ?? "")
    }
}

final class PaddedLabel: UILabel {}
