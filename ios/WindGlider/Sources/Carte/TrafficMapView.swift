import MapKit
import SwiftUI
import WindGliderShared

/// Carte du trafic (TrafficMapScreen d'Android, édition complète) : aéronefs OGN et pompes du réseau sur une large zone,
/// état de la connexion. Fond MapKit en attendant MapLibre iOS (mêmes packs hors ligne qu'Android).
struct TrafficMapView: View {
    @EnvironmentObject var app: AppModel
    @State private var camera: MapCameraPosition = .automatic
    @State private var picked: TrafficPlane?

    var body: some View {
        let t = app.trafficSnap
        ZStack(alignment: .top) {
            Map(position: $camera) {
                ForEach(t?.pumps ?? [], id: \.self) { pump in
                    Annotation("", coordinate: CLLocationCoordinate2D(latitude: pump.lat, longitude: pump.lon)) {
                        Circle().fill(WG.vario(pump.climbMs).opacity(0.3)).overlay(Circle().stroke(WG.vario(pump.climbMs), lineWidth: 2)).frame(width: 22, height: 22)
                    }
                }
                ForEach(t?.planes ?? [], id: \.id) { a in
                    Annotation("", coordinate: CLLocationCoordinate2D(latitude: a.lat, longitude: a.lon)) {
                        Button { picked = a } label: {
                            VStack(spacing: 0) {
                                Image(systemName: "location.north.fill").font(.system(size: 12))
                                    .rotationEffect(.degrees(a.trackDeg?.doubleValue ?? 0))
                                    .foregroundStyle(a.circling ? WG.climb : WG.brandInk)
                                Text(a.fullLabel.isEmpty ? a.shortLabel : a.fullLabel).font(WG.caption2.weight(.semibold))
                                    .padding(.horizontal, 3).background(WG.card.opacity(0.85), in: RoundedRectangle(cornerRadius: 3))
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
                if let c = app.club?.position {
                    Annotation(app.club?.airfieldIcao ?? "", coordinate: CLLocationCoordinate2D(latitude: c.lat, longitude: c.lon)) {
                        Image(systemName: "flag.fill").foregroundStyle(WG.route)
                    }
                }
            }
            .mapStyle(.standard(elevation: .realistic, pointsOfInterest: .excludingAll))
            VStack(alignment: .leading, spacing: 4) {
                Text(t?.statusLabel ?? "Réseau OGN à l'arrêt").font(WG.caption).foregroundStyle(WG.brandInk)
                if let t, t.connected { Text("OGN " + t.pill).font(WG.caption2).foregroundStyle(WG.dim) }
                if let w = t?.warning { Text(w).font(WG.caption).foregroundStyle(WG.warn) }
                Text("Données OGN (ODbL) · immatriculations selon les choix DDB").font(WG.caption2).foregroundStyle(WG.dim)
            }
            .padding(10).background(WG.card.opacity(0.92), in: RoundedRectangle(cornerRadius: 12)).padding(12)
        }
        .onAppear {
            if let c = app.club?.position {
                camera = .region(MKCoordinateRegion(center: CLLocationCoordinate2D(latitude: c.lat, longitude: c.lon),
                                                    span: MKCoordinateSpan(latitudeDelta: 2.5, longitudeDelta: 2.5)))
            }
        }
        .sheet(item: Binding(get: { picked.map { PickedPlane(plane: $0) } }, set: { picked = $0?.plane })) { p in
            VStack(alignment: .leading, spacing: 8) {
                Text(p.plane.fullLabel.isEmpty ? p.plane.typeLabel : p.plane.fullLabel).font(WG.title.weight(.bold)).foregroundStyle(WG.brandInk)
                Text(p.plane.typeLabel).font(WG.subhead).foregroundStyle(WG.dim)
                HStack {
                    kpi(p.plane.altitudeM.map { "\(Int($0.doubleValue.rounded()))" } ?? "—", "Altitude", WG.altitude, unit: "m")
                    kpi(p.plane.climbMs.map { String(format: "%+.1f", $0.doubleValue).replacingOccurrences(of: ".", with: ",") } ?? "—", "Vario", WG.climb, unit: "m/s")
                    kpi(p.plane.speedKmh.map { "\(Int($0.doubleValue.rounded()))" } ?? "—", "Vitesse", WG.sky, unit: "km/h")
                }
                Text("vu il y a \(p.plane.ageS) s").font(WG.footnote).foregroundStyle(WG.dim)
            }
            .padding(20)
            .presentationDetents([.height(220)])
        }
    }
}

private struct PickedPlane: Identifiable { let plane: TrafficPlane; var id: String { plane.id } }
