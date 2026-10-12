import CoreLocation
import Foundation

/// Position du planeur à chaque image (60 Hz) entre deux positions GPS (1 Hz) : extrapolation en virage à taux
/// constant, puis résorption douce de l'écart à l'arrivée d'une nouvelle position. Affichage de la carte seulement :
/// la sécurité (marge, alertes, IGC) reste calculée sur les vraies positions par le moteur commun.
final class OwnshipPredictor {
    struct State { let coordinate: CLLocationCoordinate2D; let course: Double; let valid: Bool }

    private var lat0 = 0.0, lon0 = 0.0, t0 = 0.0
    private var speed = 0.0          // m/s
    private var course0 = 0.0        // degrés, nord = 0, sens horaire
    private var gpsTurn = 0.0        // °/s dérivé des routes GPS (lissé)
    private var gyroTurn = 0.0       // °/s mesuré par le gyroscope
    private var gyroAt = 0.0
    private var hasFix = false
    // écart résiduel (mètres est/nord, degrés) résorbé après chaque nouvelle position
    private var errE = 0.0, errN = 0.0, errC = 0.0, errAt = 0.0

    static let maxExtrapolation = 3.0
    static let correctionTau = 0.35

    /// Taux de virage instantané (°/s, positif à droite) mesuré par le gyroscope.
    func gyro(turnDegS: Double, at t: Double) {
        gyroTurn = gyroTurn * 0.6 + turnDegS * 0.4
        gyroAt = t
    }

    private var turnRate: Double {
        let now = Date().timeIntervalSince1970
        let rate = (now - gyroAt < 0.5) ? gyroTurn : gpsTurn
        return min(max(rate, -40), 40)
    }

    /// Nouvelle position. [course] < 0 ou vitesse faible : route inconnue (au sol), pas d'extrapolation.
    func fix(lat: Double, lon: Double, speedMs: Double, course: Double, at t: Double) {
        let now = t
        if hasFix {
            // écart entre ce qu'on affichait et la vraie position : on le garde et on le fait décroître
            let shown = predictRaw(at: now)
            let (e, n) = Self.enu(fromLat: lat, lon: lon, toLat: shown.lat, lon: shown.lon)   // brut − vraie position
            if hypot(e, n) < 300 {
                // affiché maintenant = brut + ancien écart décru ; nouvel écart = affiché − vraie position
                let decay = exp(-(now - errAt) / Self.correctionTau)
                errE = e + errE * decay
                errN = n + errN * decay
                let shownCourse = shown.course + errC * decay
                errC = (course >= 0 && speedMs > 3) ? Self.angleDiff(shownCourse, course) : errC * decay
            } else { errE = 0; errN = 0; errC = 0 }   // saut (démo, perte GPS) : pas de glissade absurde
            errAt = now
            if course >= 0, speedMs > 3, now > t0 {
                let measured = Self.angleDiff(course, course0) / max(now - t0, 0.2)
                gpsTurn = gpsTurn * 0.5 + max(min(measured, 40), -40) * 0.5
            }
        }
        lat0 = lat; lon0 = lon; t0 = now
        speed = speedMs > 0 ? speedMs : 0
        if course >= 0 && speedMs > 3 { course0 = course } else { gpsTurn = 0 }
        hasFix = true
    }

    /// Position à afficher maintenant (écart résiduel compris).
    func state(at t: Double) -> State {
        guard hasFix else { return State(coordinate: .init(latitude: 0, longitude: 0), course: 0, valid: false) }
        let raw = predictRaw(at: t)
        let k = exp(-(t - errAt) / Self.correctionTau)
        let (lat, lon) = Self.offset(lat: raw.lat, lon: raw.lon, east: errE * k, north: errN * k)
        return State(coordinate: .init(latitude: lat, longitude: lon), course: Self.norm(raw.course + errC * k), valid: true)
    }

    private func predictRaw(at t: Double) -> (lat: Double, lon: Double, course: Double) {
        let dt = min(max(t - t0, 0), Self.maxExtrapolation)
        guard speed > 3 else { return (lat0, lon0, course0) }
        let w = turnRate * .pi / 180
        let c0 = course0 * .pi / 180
        let e: Double, n: Double
        if abs(w) < 0.002 {
            e = speed * dt * sin(c0); n = speed * dt * cos(c0)
        } else {
            // arc de cercle : intégrale de (sin c, cos c) avec c = c0 + w·t
            e = speed * (cos(c0) - cos(c0 + w * dt)) / w
            n = speed * (sin(c0 + w * dt) - sin(c0)) / w
        }
        let (lat, lon) = Self.offset(lat: lat0, lon: lon0, east: e, north: n)
        return (lat, lon, Self.norm(course0 + turnRate * dt))
    }

    // MARK: géométrie locale (plan tangent, largement suffisant sur quelques centaines de mètres)

    static func offset(lat: Double, lon: Double, east: Double, north: Double) -> (Double, Double) {
        let r = 6_371_000.0
        return (lat + north / r * 180 / .pi, lon + east / (r * cos(lat * .pi / 180)) * 180 / .pi)
    }

    static func enu(fromLat: Double, lon fromLon: Double, toLat: Double, lon toLon: Double) -> (Double, Double) {
        let r = 6_371_000.0
        return ((toLon - fromLon) * .pi / 180 * r * cos(fromLat * .pi / 180), (toLat - fromLat) * .pi / 180 * r)
    }

    static func norm(_ a: Double) -> Double { let x = a.truncatingRemainder(dividingBy: 360); return x < 0 ? x + 360 : x }
    /// a − b ramené dans ]−180, 180].
    static func angleDiff(_ a: Double, _ b: Double) -> Double { var d = (a - b).truncatingRemainder(dividingBy: 360); if d > 180 { d -= 360 }; if d <= -180 { d += 360 }; return d }
}
