import CoreLocation
import CoreMotion
import WindGliderShared

/// Capteurs iOS → moteur de vol commun : GPS 1 Hz (CoreLocation), baromètre (CMAltimeter), gravité et accélération
/// (CMDeviceMotion 50 Hz). Tout est livré sur le fil principal, comme le fil unique du moteur Android.
final class Sensors: NSObject, CLLocationManagerDelegate {
    private let location = CLLocationManager()
    private let altimeter = CMAltimeter()
    private let motion = CMMotionManager()
    private weak var engine: IosFlightEngine?
    var onPosition: ((CLLocation) -> Void)?
    /// V3 : taux de virage (°/s, positif à droite) = rotation du téléphone autour de la verticale (gyroscope).
    var onTurnRate: ((Double, TimeInterval) -> Void)?
    private(set) var authorized = false

    init(engine: IosFlightEngine) {
        self.engine = engine
        super.init()
        location.delegate = self
        location.desiredAccuracy = kCLLocationAccuracyBest   // même cadence 1 Hz, moins d'énergie que BestForNavigation (revue énergie 13)
        location.activityType = .airborne
        location.distanceFilter = kCLDistanceFilterNone
    }

    var baroAvailable: Bool { CMAltimeter.isRelativeAltitudeAvailable() }

    func requestPermission() { location.requestWhenInUseAuthorization() }

    func start() {
        location.allowsBackgroundLocationUpdates = true   // vol écran éteint (UIBackgroundModes location)
        location.pausesLocationUpdatesAutomatically = false
        location.startUpdatingLocation()
        if CMAltimeter.isRelativeAltitudeAvailable() {
            altimeter.startRelativeAltitudeUpdates(to: .main) { [weak self] data, _ in
                guard let d = data else { return }
                self?.engine?.onPressure(kPa: d.pressure.doubleValue, uptimeS: d.timestamp)
            }
        }
        if motion.isDeviceMotionAvailable {
            motion.deviceMotionUpdateInterval = 1.0 / 50
            motion.startDeviceMotionUpdates(to: .main) { [weak self] m, _ in
                guard let m else { return }
                let u = m.userAcceleration, g = m.gravity
                self?.engine?.onMotion(ux: u.x, uy: u.y, uz: u.z, gx: g.x, gy: g.y, gz: g.z, uptimeS: m.timestamp)
                // projection de la rotation sur la gravité (vers le bas) : virage à droite = cap qui augmente
                let r = m.rotationRate, n = sqrt(g.x * g.x + g.y * g.y + g.z * g.z)
                if n > 0.5 { self?.onTurnRate?((r.x * g.x + r.y * g.y + r.z * g.z) / n * 180 / .pi, Date().timeIntervalSince1970) }
            }
        }
    }

    func stop() {
        location.stopUpdatingLocation()
        altimeter.stopRelativeAltitudeUpdates()
        motion.stopDeviceMotionUpdates()
    }

    func locationManagerDidChangeAuthorization(_ m: CLLocationManager) {
        authorized = m.authorizationStatus == .authorizedWhenInUse || m.authorizationStatus == .authorizedAlways
    }

    func locationManager(_ m: CLLocationManager, didUpdateLocations locs: [CLLocation]) {
        for l in locs {
            // revue résilience M3 : jamais de position invalide ni d'une position en cache (parking il y a 2 h)
            guard l.horizontalAccuracy >= 0, l.horizontalAccuracy < 500, abs(l.timestamp.timeIntervalSinceNow) < 10 else { continue }
            onPosition?(l)
            let ell: Double
            if #available(iOS 15, *) { ell = l.ellipsoidalAltitude } else { ell = l.altitude }
            engine?.onLocation(
                lat: l.coordinate.latitude, lon: l.coordinate.longitude,
                altitudeMsl: l.altitude, ellipsoidAlt: ell, hasAltitude: l.verticalAccuracy >= 0,
                speedMs: l.speed, courseDeg: l.course, accuracyM: l.horizontalAccuracy,
                epochMs: Int64(l.timestamp.timeIntervalSince1970 * 1000)
            )
        }
    }
}
