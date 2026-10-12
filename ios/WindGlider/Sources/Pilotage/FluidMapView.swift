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
        // échelle en bas à gauche, au-dessus des pastilles (disposition de la V2)
        let scale = MKScaleView(mapView: v)
        scale.scaleVisibility = .visible
        scale.legendAlignment = .leading
        scale.translatesAutoresizingMaskIntoConstraints = false
        v.addSubview(scale)
        NSLayoutConstraint.activate([
            scale.leadingAnchor.constraint(equalTo: v.leadingAnchor, constant: 10),
            scale.bottomAnchor.constraint(equalTo: v.bottomAnchor, constant: -128),
        ])
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
    }

    static func dismantleUIView(_ v: MKMapView, coordinator: Coordinator) { coordinator.detach() }

    // MARK: -

    final class Coordinator: NSObject, MKMapViewDelegate, UIGestureRecognizerDelegate {
        let predictor: OwnshipPredictor
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
        private var traceCount = -1
        private var traceOverlays: [MKOverlay] = []
        private var routeOverlay: MKPolyline?
        private var field = MKPointAnnotation()
        private var planes: [String: PlaneAnnotation] = [:]
        private var pumps: [String: PumpAnnotation] = [:]

        init(predictor: OwnshipPredictor, control: MapControl) {
            self.predictor = predictor
            self.control = control
            camDistance = control.distance
        }

        func attach(_ v: MKMapView) {
            view = v
            let l = CADisplayLink(target: self, selector: #selector(frame))
            l.preferredFrameRateRange = CAFrameRateRange(minimum: 30, maximum: 60, preferred: 60)
            l.add(to: .main, forMode: .common)
            link = l
        }

        func detach() { link?.invalidate(); link = nil }

        @objc func userGesture(_ g: UIGestureRecognizer) {
            if g.state == .began { control.follow = false }
        }

        func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }

        // MARK: image par image

        @objc private func frame() {
            guard let v = view, let p else { return }
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
            } else {
                camCenter = target
            }
            v.camera = MKMapCamera(lookingAtCenter: camCenter!, fromDistance: camDistance, pitch: 0, heading: camHeading)
            (v.view(for: ownship) as? OwnshipView)?.rotate(track - camHeading)
        }

        // MARK: contenu (4 Hz, par différence)

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
            }
            // trace : seulement quand elle change de longueur
            if p.trace.count != traceCount {
                traceCount = p.trace.count
                v.removeOverlays(traceOverlays)
                traceOverlays = Self.segments(p.trace)
                v.addOverlays(traceOverlays, level: .aboveLabels)
            }
            // route vers le terrain
            if let r = routeOverlay { v.removeOverlay(r) }
            routeOverlay = nil
            if p.fieldLat != 0 || p.fieldLon != 0 {
                let fieldC = CLLocationCoordinate2D(latitude: p.fieldLat, longitude: p.fieldLon)
                if field.coordinate.latitude != fieldC.latitude || field.coordinate.longitude != fieldC.longitude || field.title != p.fieldCode {
                    v.removeAnnotation(field)
                    field = MKPointAnnotation(); field.coordinate = fieldC; field.title = p.fieldCode
                    v.addAnnotation(field)
                }
                if p.hasPosition {
                    let me = ownship.coordinate.latitude == 0 ? CLLocationCoordinate2D(latitude: p.lat, longitude: p.lon) : ownship.coordinate
                    let r = RouteOverlay(coordinates: [me, fieldC], count: 2)
                    v.addOverlay(r, level: .aboveLabels)
                    routeOverlay = r
                }
            }
            // trafic OGN
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

        private static func segments(_ trace: [TraceDot]) -> [MKOverlay] {
            var out: [MKOverlay] = []
            var cur: [CLLocationCoordinate2D] = []
            var bucket = Int.min
            for d in trace {
                let b = Int((d.climbMs * 2).rounded())
                let c = CLLocationCoordinate2D(latitude: d.lat, longitude: d.lon)
                if b != bucket, let last = cur.last {
                    out.append(TraceOverlay(coordinates: cur, count: cur.count).with(climb: Double(bucket) / 2)); cur = [last]
                }
                bucket = b; cur.append(c)
            }
            if cur.count > 1 { out.append(TraceOverlay(coordinates: cur, count: cur.count).with(climb: Double(bucket) / 2)) }
            return out
        }

        // MARK: rendu

        func mapView(_ m: MKMapView, rendererFor o: MKOverlay) -> MKOverlayRenderer {
            if let t = o as? TraceOverlay {
                let r = MKPolylineRenderer(polyline: t); r.strokeColor = UIColor(WG.vario(t.climb)); r.lineWidth = 4; r.lineCap = .round; return r
            }
            if let t = o as? RouteOverlay {
                let r = MKPolylineRenderer(polyline: t); r.strokeColor = UIColor(WG.route); r.lineWidth = 2; r.lineDashPattern = [4, 5]; return r
            }
            if let a = o as? AirspaceOverlay {
                let r = MKPolygonRenderer(polygon: a.polygon)
                let c = UIColor(WG.airspace(a.family))
                r.strokeColor = c; r.fillColor = c.withAlphaComponent(0.06); r.lineWidth = 1.5
                if a.family == 1 { r.lineDashPattern = [6, 4] }
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
            case let f as MKPointAnnotation:
                let v = m.dequeueReusableAnnotationView(withIdentifier: "field") ?? MKAnnotationView(annotation: f, reuseIdentifier: "field")
                v.annotation = f
                v.image = UIImage(systemName: "flag.fill")?.withTintColor(UIColor(WG.route), renderingMode: .alwaysOriginal)
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

final class TraceOverlay: MKPolyline {
    var climb = 0.0
    func with(climb: Double) -> TraceOverlay { self.climb = climb; return self }
}

final class RouteOverlay: MKPolyline {}

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

/// Planeur : même symbole que la V2 (avion bleu nuit), tourné vers la route à chaque image.
final class OwnshipView: MKAnnotationView {
    private let icon = UIImageView(image: UIImage(systemName: "airplane", withConfiguration: UIImage.SymbolConfiguration(textStyle: .title2))?
        .withTintColor(UIColor(WG.brandInk), renderingMode: .alwaysOriginal))
    override init(annotation: MKAnnotation?, reuseIdentifier: String?) {
        super.init(annotation: annotation, reuseIdentifier: reuseIdentifier)
        frame = CGRect(x: 0, y: 0, width: 36, height: 36)
        icon.frame = bounds; icon.contentMode = .center
        addSubview(icon)
        displayPriority = .required
        zPriority = .max
        collisionMode = .none
    }
    required init?(coder: NSCoder) { fatalError() }
    /// « airplane » pointe vers l'est : −90° pour l'aligner sur la route.
    func rotate(_ deg: Double) { icon.transform = CGAffineTransform(rotationAngle: (deg - 90) * .pi / 180) }
}

/// Aéronef OGN : flèche orientée (orange en spirale), étiquette DDB ou repli court, écart d'altitude (comme la V2).
final class PlaneView: MKAnnotationView {
    private let arrow = UIImageView()
    private let label = PaddedLabel()
    override init(annotation: MKAnnotation?, reuseIdentifier: String?) {
        super.init(annotation: annotation, reuseIdentifier: reuseIdentifier)
        frame = CGRect(x: 0, y: 0, width: 120, height: 36)
        arrow.frame = CGRect(x: 52, y: 0, width: 16, height: 16); arrow.contentMode = .center
        label.font = UIFont(name: "Inter-SemiBold", size: 11) ?? .systemFont(ofSize: 11, weight: .semibold)
        label.textColor = UIColor(WG.brandInk); label.backgroundColor = UIColor(WG.card).withAlphaComponent(0.85)
        label.layer.cornerRadius = 4; label.clipsToBounds = true; label.textAlignment = .center
        addSubview(arrow); addSubview(label)
        collisionMode = .rectangle
    }
    required init?(coder: NSCoder) { fatalError() }
    func configure(_ t: TrafficPlane, mapHeading: Double) {
        arrow.image = UIImage(systemName: "location.north.fill", withConfiguration: UIImage.SymbolConfiguration(pointSize: 13))?
            .withTintColor(UIColor(t.circling ? WG.climb : WG.brandInk), renderingMode: .alwaysOriginal)
        arrow.transform = CGAffineTransform(rotationAngle: ((t.trackDeg?.doubleValue ?? 0) - mapHeading) * .pi / 180)
        let name = t.fullLabel.isEmpty ? (t.shortLabel.isEmpty ? t.typeLabel : t.shortLabel) : t.fullLabel
        label.text = name + (t.relativeAltText.isEmpty ? "" : " " + t.relativeAltText)
        label.sizeToFit()
        label.frame = CGRect(x: (bounds.width - label.frame.width - 8) / 2, y: 17, width: label.frame.width + 8, height: 16)
        alpha = t.ageS > 60 ? 0.5 : 1
        accessibilityLabel = "Aéronef \(t.fullLabel.isEmpty ? t.typeLabel : t.fullLabel)"
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
        let c = UIColor(WG.vario(t.climbMs))
        layer.borderColor = c.cgColor; backgroundColor = c.withAlphaComponent(0.25)
        let f = NumberFormatter(); f.locale = Locale(identifier: "fr_FR"); f.minimumFractionDigits = 1; f.maximumFractionDigits = 1
        label.text = (t.climbMs >= 0 ? "+" : "") + (f.string(from: NSNumber(value: t.climbMs)) ?? "")
        accessibilityLabel = "Pompe \(label.text ?? "") mètres par seconde"
    }
}

final class PaddedLabel: UILabel {}
