import MapKit
import SwiftUI
import WindGliderShared

/// Onglet Mes vols (édition Lite, sans partage) : carnet des IGC fermés, détail avec trace et statistiques.
struct MyFlightsView: View {
    @EnvironmentObject var app: AppModel

    var body: some View {
        NavigationStack {
            List {
                if app.flights.isEmpty {
                    Text("Aucun vol enregistré. Lancez REC dans Pilotage, ou activez le départ automatique dans Prévol.")
                        .font(WG.subhead).foregroundStyle(WG.dim)
                }
                ForEach(app.flights, id: \.path) { f in
                    NavigationLink { FlightDetail(f: f) } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(f.title).font(WG.headline).foregroundStyle(WG.brandInk)
                            Text("\(f.durationText) · \(f.distanceText) · max \(f.maxAltText)").font(WG.footnote).foregroundStyle(WG.dim)
                        }
                    }
                }
                .onDelete { idx in idx.map { app.flights[$0] }.forEach(app.delete) }
            }
            .scrollContentBackground(.hidden)
            .background(WG.brandPaper)
            .navigationTitle("Mes vols")
            .refreshable { app.refreshFlights() }
            .onAppear { app.refreshFlights() }
        }
    }
}

struct FlightDetail: View {
    let f: FlightEntry

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                Map {
                    ForEach(Array(f.track.enumerated()), id: \.offset) { i, d in
                        if i > 0 {
                            let a = f.track[i - 1]
                            MapPolyline(coordinates: [.init(latitude: a.lat, longitude: a.lon), .init(latitude: d.lat, longitude: d.lon)])
                                .stroke(WG.vario(d.climbMs), lineWidth: 3)
                        }
                    }
                }
                .frame(height: 360)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                WGCard(title: f.title, systemImage: "airplane", tint: WG.brandInk) {
                    Grid(alignment: .leading, horizontalSpacing: 24, verticalSpacing: 8) {
                        GridRow { stat("Durée", f.durationText); stat("Distance", f.distanceText) }
                        GridRow { stat("Altitude max", f.maxAltText); stat("Gain cumulé", f.gainText) }
                    }
                    Text("\(f.fileName) · \(f.detail) · trace indicative (enregistreur non approuvé)").font(WG.caption).foregroundStyle(WG.dim)
                }
            }
            .padding(16)
        }
        .background(WG.brandPaper)
        .navigationTitle("Vol")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func stat(_ l: String, _ v: String) -> some View {
        VStack(alignment: .leading) { Text(l).font(WG.caption).foregroundStyle(WG.dim); Text(v).font(WG.number(20)).foregroundStyle(WG.brandInk) }
    }
}
