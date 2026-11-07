import SwiftUI
import WindGliderShared

/// Édition Lite iOS, étape 1 : la journée météo d'un terrain, calculée par le métier Kotlin partagé
/// (data:precog + core:domain). Toute valeur affichée porte sa source.
@MainActor
final class PrevolModel: ObservableObject {
    enum State { case loading, failed(String), loaded(DayWeather) }
    @Published var state: State = .loading

    private let weather = WindGliderWeather(
        apiKey: Bundle.main.object(forInfoDictionaryKey: "PRECOG_API_KEY") as? String ?? "",
        userAgent: "WindGlider/0.1 (iOS)"
    )

    // Terrain de démonstration : LFNL Saint-Martin-de-Londres (34). Choix du club : étape suivante.
    func load() async {
        state = .loading
        do {
            let r = try await weather.loadDay(lat: 43.80028, lon: 3.78167, departement: "34")
            if let ok = r as? WeatherResultSuccess {
                state = .loaded(ok.day)
            } else if let err = r as? WeatherResultError {
                state = .failed(err.message)
            }
        } catch {
            state = .failed(error.localizedDescription)
        }
    }
}

struct PrevolView: View {
    @StateObject private var model = PrevolModel()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    switch model.state {
                    case .loading:
                        ProgressView().frame(maxWidth: .infinity)
                    case .failed(let message):
                        Text(message).font(WG.body).foregroundStyle(WG.brandInk)
                    case .loaded(let day):
                        DaySummaryCard(day: day)
                        SourcesCard(sources: day.sources)
                    }
                }
                .padding(16)
            }
            .background(WG.brandPaper)
            .navigationTitle("Prévol")
            .task { await model.load() }
            .refreshable { await model.load() }
        }
    }
}

private func hm(_ i: Kotlinx_datetimeInstant?) -> String {
    guard let i else { return "—" }
    let f = DateFormatter()
    f.timeZone = TimeZone(identifier: "Europe/Paris")
    f.dateFormat = "HH:mm"
    return f.string(from: Date(timeIntervalSince1970: WindGliderWeather.companion.epochSeconds(i: i)))
}

struct DaySummaryCard: View {
    let day: DayWeather

    var body: some View {
        let s = day.summary
        VStack(alignment: .leading, spacing: 8) {
            Text("LFNL · \(day.date)").font(WG.headline).foregroundStyle(WG.brandInk)
            HStack {
                kpi(hm(s.triggerTime), "Déclenchement")
                kpi(s.best.map { "\(Int(($0.ceilingMslM / 10).rounded()) * 10) m" } ?? "—", "Plafond")
                kpi(hm(s.endTime), "Fin")
            }
            Text("Modèle ARPEGE, thermique estimée — hypothèse, pas une mesure.")
                .font(WG.caption).foregroundStyle(.secondary)
        }
        .padding(16)
        .background(WG.card, in: RoundedRectangle(cornerRadius: 16))
    }

    private func kpi(_ value: String, _ label: String) -> some View {
        VStack(alignment: .leading) {
            Text(value).font(WG.headline).foregroundStyle(WG.brandInk)
            Text(label).font(WG.caption).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct SourcesCard: View {
    let sources: [SourceInfo]

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Sources").font(WG.headline).foregroundStyle(WG.brandInk)
            ForEach(sources, id: \.product) { s in
                Text("\(s.product) · \(s.attribution) · reçu \(hm(s.fetchedAt))\(s.offline ? " · copie locale" : "")")
                    .font(WG.footnote)
                if let license = s.license { Text(license).font(WG.caption).foregroundStyle(.secondary) }
            }
            Text(WeatherRepositoryKt.PRECOG_CREDIT).font(WG.caption).foregroundStyle(.secondary)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(WG.card, in: RoundedRectangle(cornerRadius: 16))
    }
}
