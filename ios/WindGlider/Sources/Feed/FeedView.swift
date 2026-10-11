import SwiftUI
import WindGliderShared

/// État du fil observé depuis Swift (FeedController commun, core:social).
@MainActor
final class FeedModel: ObservableObject {
    let feed: IosFeed
    @Published var state: FeedUiState

    init() {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("feed").path
        feed = IosFeed(dir: dir)
        state = feed.state
        feed.onChange = { [weak self] s in self?.state = s }
    }
}

/// Feed (FeedScreen d'Android) : recherche de pilotes, suivre, grille des vols des pilotes suivis (exemples).
struct FeedView: View {
    @StateObject private var m = FeedModel()
    @State private var query = ""
    private let columns = Array(repeating: GridItem(.flexible(), spacing: 10), count: 3)

    var body: some View {
        let s = m.state
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 14) {
                Text("Feed").font(WG.largeTitle).foregroundStyle(WG.brandInk)
                HStack {
                    Image(systemName: "magnifyingglass").foregroundStyle(WG.dim)
                    TextField("Nom ou @pseudo", text: $query).textInputAutocapitalization(.never).autocorrectionDisabled()
                        .onChange(of: query) { _, q in m.feed.search(query: q) }
                        .accessibilityLabel("Rechercher un pilote")
                    if !query.isEmpty { Button { query = "" } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(WG.dim) }.accessibilityLabel("Effacer la recherche") }
                }
                .padding(12).background(WG.card, in: RoundedRectangle(cornerRadius: 12))
                if !s.query.trimmingCharacters(in: .whitespaces).isEmpty {
                    if s.results.isEmpty {
                        Text("Aucun résultat pour « \(s.query.trimmingCharacters(in: .whitespaces)) »").font(WG.subhead).foregroundStyle(WG.dim)
                    }
                    ForEach(s.results, id: \.id) { p in pilotRow(p, following: s.following.contains(p.id)) }
                } else {
                    if !s.suggestions.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 10) { ForEach(s.suggestions, id: \.id) { p in suggestion(p, following: s.following.contains(p.id)) } }
                        }
                    }
                    Text("Vols d'exemple en attendant les comptes en ligne.").font(WG.footnote).foregroundStyle(WG.warn)
                    if s.flights.isEmpty && !s.loading {
                        WGCard(title: "Fil vide", systemImage: "person.2", tint: WG.sky) {
                            Text("Suis des pilotes pour voir leurs vols.").font(WG.subhead).foregroundStyle(WG.dim)
                        }
                    }
                    LazyVGrid(columns: columns, spacing: 10) {
                        ForEach(s.flights, id: \.id) { f in
                            Button { m.feed.open(flight: f) } label: { tile(f) }.buttonStyle(.plain)
                                .onAppear { if f.id == s.flights.last?.id { m.feed.loadMore() } }
                        }
                    }
                    if s.loadingMore { ProgressView().frame(maxWidth: .infinity) }
                    if s.endReached && !s.flights.isEmpty { Text("Tu es à jour").font(WG.footnote).foregroundStyle(WG.dim).frame(maxWidth: .infinity) }
                }
            }
            .padding(16)
        }
        .background(WG.brandPaper)
        .refreshable { m.feed.reload() }
        .sheet(isPresented: Binding(get: { m.state.selected != nil }, set: { if !$0 { m.feed.close() } })) {
            if let f = m.state.selected { detail(f) }
        }
    }

    private func avatar(_ p: FeedPilot) -> some View {
        Text(p.initials).font(WG.caption.weight(.bold)).foregroundStyle(.white)
            .frame(width: 36, height: 36).background(WG.sky, in: Circle())
    }

    private func followChip(_ p: FeedPilot, following: Bool) -> some View {
        Button { m.feed.toggleFollow(pilotId: p.id) } label: {
            Text(following ? "Suivi" : "Suivre").font(WG.footnote.weight(.semibold))
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(following ? WG.sky.opacity(0.14) : WG.sky, in: Capsule())
                .foregroundStyle(following ? WG.sky : .white)
        }
    }

    private func pilotRow(_ p: FeedPilot, following: Bool) -> some View {
        HStack(spacing: 12) {
            avatar(p)
            VStack(alignment: .leading, spacing: 1) {
                Text(p.displayName).font(WG.headline).foregroundStyle(WG.brandInk)
                Text("@\(p.username) · \(p.club)").font(WG.footnote).foregroundStyle(WG.dim).lineLimit(1)
            }
            Spacer()
            followChip(p, following: following)
        }
        .padding(12).background(WG.card, in: RoundedRectangle(cornerRadius: 14))
    }

    private func suggestion(_ p: FeedPilot, following: Bool) -> some View {
        VStack(spacing: 6) {
            avatar(p)
            Text(p.displayName).font(WG.footnote.weight(.semibold)).foregroundStyle(WG.brandInk).lineLimit(1)
            followChip(p, following: following)
        }
        .frame(width: 110).padding(10).background(WG.card, in: RoundedRectangle(cornerRadius: 14))
    }

    private func tile(_ f: FeedFlight) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            RouteSketch(xy: m.feed.routeXY(flight: f).map { CGFloat($0.floatValue) })
                .stroke(WG.route, lineWidth: 1.6)
                .aspectRatio(1, contentMode: .fit)
                .padding(8)
                .background(WG.sky.opacity(0.08), in: RoundedRectangle(cornerRadius: 10))
            Text(m.feed.km(meters: f.distanceMeters) + " · " + m.feed.duration(seconds: f.durationSeconds)).font(WG.caption2.weight(.semibold)).foregroundStyle(WG.brandInk).lineLimit(1)
            Text("@\(f.pilot.username)").font(WG.caption2).foregroundStyle(WG.dim).lineLimit(1)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Vol de \(f.pilot.displayName), \(m.feed.km(meters: f.distanceMeters)), \(m.feed.duration(seconds: f.durationSeconds))")
    }

    private func detail(_ f: FeedFlight) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 12) {
                    avatar(f.pilot)
                    VStack(alignment: .leading) {
                        Text(f.pilot.displayName).font(WG.headline).foregroundStyle(WG.brandInk)
                        Text("@\(f.pilot.username) · \(f.site)").font(WG.footnote).foregroundStyle(WG.dim).lineLimit(1)
                    }
                    Spacer()
                    if f.pilot.isDemo { Text("Exemple").font(WG.caption.weight(.semibold)).foregroundStyle(WG.warn).padding(.horizontal, 8).padding(.vertical, 3).background(WG.warn.opacity(0.12), in: Capsule()) }
                }
                RouteSketch(xy: m.feed.routeXY(flight: f).map { CGFloat($0.floatValue) })
                    .stroke(WG.route, lineWidth: 2.5).aspectRatio(1, contentMode: .fit).padding(16)
                    .background(WG.card, in: RoundedRectangle(cornerRadius: 18))
                HStack {
                    kpi(m.feed.duration(seconds: f.durationSeconds), "Durée", WG.sun, unit: nil)
                    kpi(m.feed.km(meters: f.distanceMeters), "Distance", WG.sky, unit: nil)
                    kpi("\(f.maxAltitudeMeters) m", "Alt. max", WG.altitude, unit: nil)
                }
            }
            .padding(16)
        }
        .background(WG.brandPaper)
        .presentationDetents([.large])
    }
}

/// Trace normalisée dans le carré unité (x → est, y → sud).
struct RouteSketch: Shape {
    let xy: [CGFloat]
    func path(in r: CGRect) -> Path {
        var p = Path()
        guard xy.count >= 4 else { return p }
        p.move(to: CGPoint(x: r.minX + xy[0] * r.width, y: r.minY + xy[1] * r.height))
        for i in stride(from: 2, to: xy.count - 1, by: 2) { p.addLine(to: CGPoint(x: r.minX + xy[i] * r.width, y: r.minY + xy[i + 1] * r.height)) }
        return p
    }
}
