import Charts
import SwiftUI
import WindGliderShared

/// Onglet Prévol (édition Lite) : club du jour, journée thermique ARPEGE, heure par heure, vents, vigilance,
/// sources (bouton « i ») et réglages du vol. Toute valeur affichée porte sa source et son hypothèse.
struct PrevolView: View {
    @EnvironmentObject var app: AppModel
    @State private var picking = false
    @State private var showSources = false
    @State private var hourIndex: Int?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    header
                    if app.weatherIsTestData {
                        Text(tr("DONNÉES D'ESSAI · journée synthétique du 16/09/2026, pas une prévision"))
                            .font(WG.caption.weight(.bold)).foregroundStyle(.white)
                            .padding(8).frame(maxWidth: .infinity).background(WG.bad, in: RoundedRectangle(cornerRadius: 10))
                    }
                    PackCard()
                    DayPicker()
                    switch app.weather {
                    case .idle, .loading:
                        WGCard { HStack { ProgressView(); Text(tr("Météo du jour…")).font(WG.subhead) } }
                    case .failed(let message):
                        WGCard(title: tr("Météo"), systemImage: "cloud.sun", tint: WG.sun) {
                            Text(message).font(WG.subhead).foregroundStyle(WG.brandInk)
                            Button(tr("Réessayer")) { Task { await app.loadWeather() } }.font(WG.subhead)
                        }
                    case .loaded(let day):
                        let sel = selected(day)
                        DayCard(day: day, selected: sel) { hourIndex = $0 }
                        HourCard(day: day, selected: sel) { hourIndex = $0 }
                        WindsCard(day: day, index: sel)
                        if let v = day.vigilance { VigilanceCard(v: v) }
                        Text("\(app.club?.shortName ?? app.club?.name ?? "") · ARPEGE · \(day.sources.first?.attribution ?? "Source : Météo-France")")
                            .font(WG.footnote).foregroundStyle(WG.dim).lineLimit(1)
                        Text(WeatherRepositoryKt.PRECOG_CREDIT).font(WG.footnote).foregroundStyle(WG.dim)
                        Text(tr("Estimations de l'app sur le profil ARPEGE (maille ~10 km) : plafond par parcelle adiabatique, base des cumulus par Espy, force par w*. Aide secondaire, à confronter au ciel et aux consignes du club."))
                            .font(WG.footnote).foregroundStyle(WG.dim.opacity(0.8))
                    }
                    SensorsCard()
                    FlightSettingsCard()
                    GlideSettingsCard()
                }
                .padding(16)
            }
            .background(WG.page.ignoresSafeArea())
            .toolbar(.hidden, for: .navigationBar)
            .sheet(isPresented: $picking) { ClubPicker().environmentObject(app) }
            .sheet(isPresented: $showSources) { SourcesSheet().environmentObject(app) }
            .task { if case .idle = app.weather { await app.loadWeather() } }
            .refreshable { await app.loadWeather() }
        }
    }

    /// Heure affichée : celle touchée, sinon l'heure du meilleur plafond (comme Android).
    private func selected(_ day: DayWeather) -> Int {
        if let i = hourIndex, day.hours.indices.contains(i) { return i }
        let best = day.summary.best
        return day.hours.firstIndex { best != nil && $0.analysis.validTime == best!.validTime } ?? 0
    }

    /// En-tête d'Android : grand titre, date et terrain à droite (le terrain ouvre le choix du club), « i », actualiser.
    private var header: some View {
        HStack(alignment: .center) {
            Text(tr("Prévol")).font(WG.largeTitle).lineLimit(1).minimumScaleFactor(0.6).foregroundStyle(WG.brandInk)
            Spacer()
            VStack(alignment: .trailing, spacing: 1) {
                if case .loaded(let day) = app.weather { Text(Fmt.day(day.date)).font(WG.footnote).foregroundStyle(WG.brandInk) }
                Button { picking = true } label: {
                    HStack(spacing: 3) {
                        Text([app.club?.airfieldIcao, app.club?.shortName ?? app.club?.name].compactMap { $0 }.joined(separator: " · "))
                            .font(WG.subhead.weight(.semibold)).lineLimit(1)
                        Image(systemName: "chevron.down").font(.caption2.weight(.bold))
                    }
                    .foregroundStyle(WG.sky)
                }
                .accessibilityLabel(tr("Changer de club"))
            }
            Button { showSources = true } label: { Image(systemName: "info.circle.fill").font(.title3).foregroundStyle(WG.sky) }
                .accessibilityLabel(tr("Sources météo")).padding(.leading, 6)
            Button { Task { await app.loadWeather() } } label: {
                if case .loading = app.weather { ProgressView() } else { Image(systemName: "arrow.clockwise").font(.title3).foregroundStyle(WG.brandInk) }
            }
            .accessibilityLabel(tr("Actualiser")).padding(.leading, 4)
            LanguageSwitch().padding(.leading, 6)
        }
        .padding(.top, 4)
    }
}

// MARK: - formats

enum Fmt {
    static let paris = TimeZone(identifier: "Europe/Paris")!
    static func date(_ i: Kotlinx_datetimeInstant) -> Date { Date(timeIntervalSince1970: WindGliderWeather.companion.epochSeconds(i: i)) }
    static func hm(_ i: Kotlinx_datetimeInstant?) -> String {
        guard let i else { return "—" }
        let f = DateFormatter(); f.timeZone = paris; f.dateFormat = "HH:mm"
        return f.string(from: date(i))
    }
    static func hour(_ i: Kotlinx_datetimeInstant) -> String {
        let f = DateFormatter(); f.timeZone = paris; f.dateFormat = "H'h'"
        return f.string(from: date(i))
    }
    static func m(_ v: Double) -> String { IosFlightEngine.companion.grouped(n: Int32((v / 10).rounded() * 10)) + " m" }
    static func ms(_ v: Double) -> String { String(format: "%.1f m/s", v).replacingOccurrences(of: ".", with: ",") }
    static func num1(_ v: Double) -> String { String(format: "%.1f", v).replacingOccurrences(of: ".", with: ",") }
    /// Altitude arrondie à 10 m, sans unité (l'unité est affichée à part, en petit).
    static func mNumber(_ v: Double) -> String { IosFlightEngine.companion.grouped(n: Int32((v / 10).rounded() * 10)) }
    static func day(_ d: Kotlinx_datetimeLocalDate) -> String {
        var c = DateComponents(); c.year = Int(d.year); c.month = Int(d.monthNumber); c.day = Int(d.dayOfMonth)
        let f = DateFormatter(); f.locale = Locale(identifier: Lang.en ? "en_GB" : "fr_FR"); f.dateFormat = "EEEE d MMMM"
        return Calendar(identifier: .gregorian).date(from: c).map(f.string(from:)) ?? d.description
    }
}

// MARK: - cartes

/// « La journée » (Android DaySummaryCard) : qualité, déclenchement, plafond, fin, graphique des plafonds heure par heure.
struct DayCard: View {
    let day: DayWeather
    let selected: Int
    let onSelect: (Int) -> Void

    var body: some View {
        let s = day.summary
        WGCard {
            HStack {
                Label(tr("La journée"), systemImage: "sun.max").font(WG.headline).foregroundStyle(WG.sun)
                Spacer()
                // score du jour sur 100, anneau façon Apple Watch (remplace la pastille de qualité : même information)
                ScoreRing(result: WindGliderWeather.companion.score(day: day))
            }
            HStack(alignment: .top) {
                kpi(Fmt.hm(s.triggerTime), tr("Déclenchement"), WG.sun, unit: nil)
                kpi(s.best.map { Fmt.mNumber($0.ceilingMslM) } ?? "—", s.best.map { tr("Plafond %@", Fmt.hour($0.validTime)) } ?? tr("Plafond"), WG.altitude, unit: s.best == nil ? nil : "m")
                kpi(Fmt.hm(s.endTime), tr("Fin"), WG.sun, unit: nil)
            }
            CeilingChart(hours: day.hours, selected: selected, onSelect: onSelect).frame(height: 170)
            HStack(spacing: 12) {
                legend(WG.chartClimb(0.4), tr("faible")); legend(WG.chartClimb(1.2), tr("moyen")); legend(WG.chartClimb(2.4), tr("fort"))
                HStack(spacing: 4) { Circle().fill(WG.altitude).frame(width: 7, height: 7); Text(tr("base Cu")).font(WG.caption) }
            }
            if s.overdevelopmentRisk {
                Text(tr("⚠ CAPE élevée sous cumulus : surdéveloppement possible")).font(WG.footnote).foregroundStyle(WG.climb)
            }
        }
    }

    private func legend(_ c: Color, _ t: String) -> some View {
        HStack(spacing: 4) { Capsule().fill(c).frame(width: 7, height: 12); Text(t).font(WG.caption) }
    }
}

/// Chiffre clé façon Apple Santé : libellé teinté au-dessus, unité petite et grise.
func kpi(_ value: String, _ label: String, _ tint: Color, unit: String?) -> some View {
    VStack(alignment: .leading, spacing: 2) {
        Text(label).font(WG.footnote.weight(.semibold)).foregroundStyle(tint).lineLimit(1)
        HStack(alignment: .firstTextBaseline, spacing: 3) {
            Text(value).font(WG.number(30)).foregroundStyle(WG.brandInk).lineLimit(1).minimumScaleFactor(0.6)
            if let unit { Text(unit).font(WG.headline).foregroundStyle(WG.dim) }
        }
    }
    .frame(maxWidth: .infinity, alignment: .leading)
}

/// Graphique des plafonds (Android CeilingChart, pages blanches) : axe gradué depuis le sol, barres en gélule teintées par la
/// montée (grises sans ascendance), point « base Cu » sous cumulus, heure choisie surlignée avec sa valeur. Toucher = choisir.
struct CeilingChart: View {
    let hours: [HourWeather]
    let selected: Int
    let onSelect: (Int) -> Void

    var body: some View {
        GeometryReader { g in
            let left: CGFloat = 40, bottom = g.size.height - 18
            let ground = hours.map { $0.analysis.groundAltitudeM }.min() ?? 0
            let top = max(ground + 1500, (hours.map { $0.analysis.ceilingMslM }.max() ?? 0) + 300)
            let step: Double = top - ground > 2200 ? 1000 : 500
            let w = (g.size.width - left) / CGFloat(max(1, hours.count))
            let y: (Double) -> CGFloat = { alt in bottom - CGFloat((alt - ground) / (top - ground)) * (bottom - 6) }
            ZStack(alignment: .topLeading) {
                // graduations
                ForEach(Array(stride(from: (ground / step).rounded(.up) * step, to: top, by: step)), id: \.self) { v in
                    Path { p in p.move(to: CGPoint(x: left, y: y(v))); p.addLine(to: CGPoint(x: g.size.width, y: y(v))) }
                        .stroke(WG.line, style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
                    Text(IosFlightEngine.companion.grouped(n: Int32(v))).font(WG.caption2.monospacedDigit()).foregroundStyle(WG.dim)
                        .position(x: 18, y: y(v))
                }
                ForEach(Array(hours.enumerated()), id: \.offset) { i, h in
                    let a = h.analysis
                    let x = left + CGFloat(i) * w
                    let on = i == selected
                    let color = a.liftType.name == "NONE" ? Color.gray.opacity(0.3) : WG.chartClimb(a.climbMs)
                    let barTop = y(a.ceilingMslM), bw = w * 0.56
                    if on {
                        RoundedRectangle(cornerRadius: 8).fill(WG.sky.opacity(0.08))
                            .frame(width: w * 0.88, height: bottom).position(x: x + w / 2, y: bottom / 2)
                    }
                    Capsule().fill(LinearGradient(colors: [color, color.opacity(0.55)], startPoint: .top, endPoint: .bottom))
                        .frame(width: bw, height: max(bw, bottom - barTop)).position(x: x + w / 2, y: (barTop + bottom) / 2)
                    if a.liftType.name == "CUMULUS", let cb = a.cloudBaseMslM?.doubleValue {
                        Circle().fill(WG.altitude).frame(width: 6, height: 6).position(x: x + w / 2, y: y(cb) - 5)
                    }
                    if on {
                        Text(Fmt.m(a.ceilingMslM)).font(WG.caption2.weight(.semibold).monospacedDigit()).foregroundStyle(WG.brandInk)
                            .fixedSize().position(x: min(max(x + w / 2, left + 24), g.size.width - 24), y: max(8, barTop - 10))
                    }
                    if i % (hours.count > 8 ? 3 : 2) == 0 || hours.count <= 6 || on {
                        Text(Fmt.hour(a.validTime)).font(on ? WG.caption2.weight(.semibold) : WG.caption2)
                            .foregroundStyle(on ? WG.sky : WG.dim).fixedSize().position(x: x + w / 2, y: bottom + 9)
                    }
                }
                Path { p in p.move(to: CGPoint(x: left, y: y(ground))); p.addLine(to: CGPoint(x: g.size.width, y: y(ground))) }
                    .stroke(WG.line, lineWidth: 1)
            }
            .contentShape(Rectangle())
            .onTapGesture { p in
                guard !hours.isEmpty else { return }
                onSelect(min(max(Int((p.x - left) / w), 0), hours.count - 1))
            }
        }
        .accessibilityLabel(tr("Plafond heure par heure"))
    }
}

/// « Conditions à … » (Android HourCard) : puces horaires, plafond, base Cu, montée, détails.
struct HourCard: View {
    let day: DayWeather
    let selected: Int
    let onSelect: (Int) -> Void

    var body: some View {
        let h = day.hours[selected]
        let a = h.analysis
        let nature = switch a.liftType.name { case "NONE": tr("Pas d'ascendance exploitable"); case "BLUE": tr("Thermique pur (ciel bleu)"); default: tr("Thermique sous cumulus") }
        let capped = a.dryTopCapped && a.liftType.name == "BLUE" ? tr(" (≥ sommet du profil)") : ""
        WGCard(title: tr("Conditions à %@", Fmt.hm(a.validTime)), systemImage: "sun.max", tint: WG.sun) {
            ScrollViewReader { proxy in
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(Array(day.hours.enumerated()), id: \.offset) { i, hh in
                            Button { onSelect(i) } label: {
                                Text(Fmt.hour(hh.analysis.validTime)).font(WG.subhead.weight(.semibold).monospacedDigit())
                                    .padding(.horizontal, 12).padding(.vertical, 6)
                                    .background(i == selected ? WG.sky : Color.gray.opacity(0.12), in: Capsule())
                                    .foregroundStyle(i == selected ? .white : WG.brandInk)
                            }
                            .id(i)
                        }
                    }
                }
                .onAppear { proxy.scrollTo(selected, anchor: .center) }
                .onChange(of: selected) { _, v in withAnimation { proxy.scrollTo(v, anchor: .center) } }
            }
            HStack(alignment: .top) {
                kpi(Fmt.mNumber(a.ceilingMslM), tr("Plafond"), WG.altitude, unit: "m")
                kpi(a.cloudBaseMslM.map { Fmt.mNumber($0.doubleValue) } ?? "—", tr("Base Cu"), WG.altitude, unit: a.cloudBaseMslM == nil ? nil : "m")
                kpi("~" + Fmt.num1(a.climbMs), tr("Montée"), WG.sun, unit: "m/s")
            }
            statRow(tr("Plafond sol"), Fmt.m(a.ceilingAglM) + capped)
            statRow(tr("Nature"), nature)
            statRow(tr("Force estimée"), "w* " + Fmt.ms(a.wStarMs))
            statRow(tr("Nébulosité"), a.totalCloudPct.map { "\(Int($0.doubleValue)) %" + (h.surface.lowCloudPct.map { tr(" (basse %d %%)", Int($0.doubleValue)) } ?? "") } ?? "—")
            if (h.surface.lowCloudPct?.doubleValue ?? 0) >= 85 {
                Text(tr("⚠ Couche basse prévue presque couverte : ascendances sans doute étouffées, base à vérifier au ciel."))
                    .font(WG.footnote).foregroundStyle(WG.climb)
            }
            statRow("CAPE", a.capeJkg.map { "\(Int($0.doubleValue)) J/kg" } ?? "—")
            statRow(tr("Sol du modèle"), Fmt.m(a.groundAltitudeM) + (day.gridDistanceKm.map { tr(" · maille à %@ km", Fmt.num1($0.doubleValue)) } ?? ""))
        }
    }

    private func statRow(_ l: String, _ v: String) -> some View {
        HStack {
            Text(l).font(WG.subhead).foregroundStyle(WG.dim).frame(width: 128, alignment: .leading)
            Text(v).font(WG.subhead.weight(.semibold).monospacedDigit()).foregroundStyle(WG.brandInk)
                .frame(maxWidth: .infinity, alignment: .trailing).multilineTextAlignment(.trailing)
        }
        .frame(minHeight: 26)
    }
}

struct WindsCard: View {
    let day: DayWeather
    let index: Int

    var body: some View {
        let hours = day.hours
        let best = day.summary.best
        let h = hours.indices.contains(index) ? hours[index] : hours.first { best != nil && $0.analysis.validTime == best!.validTime } ?? hours.first
        if let h {
            WGCard(title: tr("Vent par tranches · %@", Fmt.hm(h.analysis.validTime)), systemImage: "wind", tint: WG.wind) {
                ForEach(h.winds, id: \.label) { w in
                    HStack {
                        Text(w.label).font(WG.subhead).frame(width: 90, alignment: .leading)
                        Image(systemName: "arrow.up").rotationEffect(.degrees(w.fromDeg + 180)).foregroundStyle(WG.wind)
                        Text("\(Int(w.fromDeg.rounded()))°").font(WG.subhead.monospacedDigit())
                        Spacer()
                        Text("\(Int(w.speedKmh.rounded())) km/h").font(WG.subhead.monospacedDigit()).foregroundStyle(WG.brandInk)
                    }
                }
                Text(tr("Vent moyen du modèle (direction d'où il vient)")).font(WG.caption).foregroundStyle(WG.dim)
            }
        }
    }
}

struct VigilanceCard: View {
    let v: VigilanceStatus

    var body: some View {
        let color: Color = [1: .green, 2: .yellow, 3: .orange, 4: .red][Int(v.colorId)] ?? .gray
        WGCard(title: tr("Vigilance Météo-France · %@", v.departement), systemImage: "exclamationmark.triangle", tint: WG.brandInk) {
            HStack {
                Circle().fill(color).frame(width: 14, height: 14)
                Text(v.colorLabel.capitalized).font(WG.subhead)
                if !v.phenomena.isEmpty { Text("· " + v.phenomena.map { trDyn($0) }.joined(separator: ", ")).font(WG.subhead).foregroundStyle(WG.dim) }
            }
        }
    }
}

struct FlightSettingsCard: View {
    @EnvironmentObject var app: AppModel

    var body: some View {
        WGCard(title: tr("Vol"), systemImage: "gearshape", tint: WG.brandInk) {
            Toggle(tr("Départ automatique à 50 km/h"), isOn: $app.autoTakeoff).font(WG.subhead)
            Toggle(tr("Son du vario"), isOn: $app.soundOn).font(WG.subhead)
            Toggle(tr("Annonces vocales"), isOn: $app.voiceOn).font(WG.subhead)
            HStack {
                Button(tr("Tester le son")) { app.testSound() }
                Spacer()
                Button(tr("Tester la voix")) { app.testVoice() }
            }
            .font(WG.subhead)
            Text(tr("Sans départ automatique, l'enregistrement se lance avec REC dans Pilotage. Baromètre : %@", app.sensors.baroAvailable ? tr("présent") : tr("absent")))
                .font(WG.caption).foregroundStyle(WG.dim)
        }
        .tint(WG.sky)
    }
}

// MARK: - feuilles

struct ClubPicker: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""

    var body: some View {
        NavigationStack {
            List(app.clubs.search(query: query), id: \.id) { c in
                Button {
                    app.choose(c); dismiss()
                } label: {
                    VStack(alignment: .leading) {
                        Text(c.displayName).font(WG.subhead).foregroundStyle(WG.brandInk)
                        Text([c.airfieldIcao, c.city].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · "))
                            .font(WG.caption).foregroundStyle(WG.dim)
                    }
                }
            }
            .searchable(text: $query, prompt: tr("Sigle, OACI, nom ou ville"))
            .navigationTitle(tr("Choix du club"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(tr("Fermer")) { dismiss() } } }
        }
    }
}

struct SourcesSheet: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                if case .loaded(let day) = app.weather {
                    ForEach(day.sources, id: \.product) { s in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(s.product).font(WG.headline)
                            Text(s.attribution).font(WG.footnote)
                            Text([s.referenceTime.map { tr("réseau de %@", Fmt.hm($0)) }, tr("reçu à %@", Fmt.hm(s.fetchedAt)), s.offline ? tr("copie locale") : nil]
                                .compactMap { $0 }.joined(separator: " · ")).font(WG.caption).foregroundStyle(WG.dim)
                            if let l = s.license { Text(l).font(WG.caption).foregroundStyle(WG.dim) }
                        }
                    }
                } else {
                    Text(tr("Aucune donnée reçue")).font(WG.subhead)
                }
                Section {
                    Text(WeatherRepositoryKt.PRECOG_CREDIT).font(WG.footnote)
                    Text(tr("Données fournies en l'état : estimation d'un modèle, à confronter à votre propre analyse. Ne remplace pas un briefing météo officiel."))
                        .font(WG.caption).foregroundStyle(WG.dim)
                }
            }
            .navigationTitle(tr("Sources météo"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("OK") { dismiss() } } }
        }
    }
}

/// Cartes hors ligne : relief Copernicus et terrains openAIP de la région du club (sécurité sans réseau).
struct PackCard: View {
    @EnvironmentObject var app: AppModel
    @ObservedObject private var packs: PackStore

    init() { _packs = ObservedObject(wrappedValue: PackStoreHolder.current) }

    var body: some View {
        WGCard(title: tr("Carte hors ligne"), systemImage: "map", tint: WG.good) {
            if let a = packs.active {
                Text(tr("%@ · %d terrains · relief %@", a.name, Int(a.airportCount), a.reliefLoaded ? tr("chargé") : tr("absent"))).font(WG.subhead)
                if a.aeroExpired { Text(tr("Données aéro périmées : mettez la carte à jour avant le vol")).font(WG.caption).foregroundStyle(WG.bad) }
                if !app.airspaces.isEmpty {
                    DisclosureGroup(tr("Espaces aériens autour du terrain (%d)", app.airspaces.count)) {
                        ForEach(app.airspaces.prefix(12), id: \.id) { s in
                            HStack(alignment: .top) {
                                RoundedRectangle(cornerRadius: 2).fill(WG.airspace(s.family)).frame(width: 4, height: 30)
                                VStack(alignment: .leading) {
                                    Text(s.name + (s.byNotam ? tr(" · par NOTAM") : "")).font(WG.subhead)
                                    Text("\(s.label) · \(s.vertical)").font(WG.caption).foregroundStyle(WG.dim)
                                }
                            }
                        }
                    }
                    .font(WG.subhead).tint(WG.brandInk)
                }
            } else if let p = packs.available {
                Text(tr("%@ : relief et terrains, %d Mo", p.name, Int(packs.neededMB))).font(WG.subhead)
                Text(tr("Sans carte, la marge ne connaît ni le relief ni les terrains de dégagement.")).font(WG.caption).foregroundStyle(WG.dim)
            } else {
                Text(packs.catalogError ?? tr("Aucune carte pour cette région")).font(WG.subhead).foregroundStyle(WG.dim)
            }
            if let pr = packs.progress {
                ProgressView(value: pr) { Text(tr("Téléchargement %d %%", Int(pr * 100))).font(WG.caption) }
                Button(tr("Annuler")) { packs.cancel() }.font(WG.subhead)
            } else if packs.available != nil && (packs.installed == nil || packs.updateAvailable) {
                Button(packs.updateAvailable ? tr("Mettre à jour") : tr("Télécharger (%d Mo)", Int(packs.neededMB))) { packs.download(club: app.club) }
                    .font(WG.subhead).buttonStyle(.borderedProminent).tint(WG.sky)
            }
            if let e = packs.error { Text(e).font(WG.caption).foregroundStyle(WG.bad) }
            Text(tr("Relief Copernicus GLO-30 · terrains openAIP · vérifiez SUP AIP et NOTAM avant le vol")).font(WG.caption).foregroundStyle(WG.dim)
        }
    }
}

/// Accès au PackStore de l'app pour les vues qui l'observent directement.
@MainActor enum PackStoreHolder { static var current: PackStore! }

/// Capteurs du téléphone (équivalent de SensorsCard d'Android) : source du vario, cadences, précision GPS, altitude.
struct SensorsCard: View {
    @EnvironmentObject var app: AppModel

    var body: some View {
        if let s = app.sensorsUi {
            WGCard(title: tr("Capteurs"), systemImage: "sensor", tint: WG.brandInk) {
                Label(trDyn(s.sourceLabel), systemImage: s.sourceOk ? "checkmark.circle.fill" : "exclamationmark.circle")
                    .font(WG.subhead).foregroundStyle(s.sourceOk ? WG.good : WG.brandInk)
                Grid(alignment: .leading, horizontalSpacing: 16, verticalSpacing: 4) {
                    GridRow { cell(tr("Baromètre"), s.baroHz); cell(tr("Bruit baro"), s.baroNoise) }
                    GridRow { cell(tr("Accéléromètre"), s.accelHz); cell("GPS", s.gpsAccuracy) }
                }
                if !s.altitudeLine.isEmpty { Text(s.altitudeLine).font(WG.footnote) }
                if !s.flightLine.isEmpty { Text(s.flightLine).font(WG.footnote).foregroundStyle(WG.dim) }
            }
        }
    }

    private func cell(_ l: String, _ v: String) -> some View {
        VStack(alignment: .leading) { Text(l).font(WG.caption).foregroundStyle(WG.dim); Text(v).font(WG.subhead.monospacedDigit()) }
    }
}


/// Choix du jour de la météo : aujourd'hui, demain, après-demain (JB, 22/10/2026).
struct DayPicker: View {
    @EnvironmentObject var app: AppModel

    var body: some View {
        Picker(tr("Jour"), selection: Binding(get: { app.dayOffset }, set: { v in
            Haptic.tap(); app.dayOffset = v; Task { await app.loadWeather() }
        })) {
            Text(tr("Aujourd'hui")).tag(0)
            Text(tr("Demain")).tag(1)
            Text(tr("Après-demain")).tag(2)
        }
        .pickerStyle(.segmented)
    }
}

/// Score du jour sur 100 : petit anneau coloré, chiffre au centre (façon Apple Watch). Indicatif.
struct ScoreRing: View {
    let result: DayScoreResult
    var size: CGFloat = 56

    var body: some View {
        let c: Color = switch result.level.name { case "POOR": WG.bad; case "FAIR": WG.warn; case "GOOD": WG.good; default: WG.sky }
        let f = CGFloat(result.score) / 100
        ZStack {
            Circle().stroke(c.opacity(0.22), lineWidth: 7)
            Circle().trim(from: 0, to: max(f, 0.001)).stroke(c, style: StrokeStyle(lineWidth: 7, lineCap: .round)).rotationEffect(.degrees(-90))
            Text("\(result.score)").font(.system(size: size * 0.36, weight: .bold).monospacedDigit()).foregroundStyle(WG.brandInk)
        }
        .frame(width: size, height: size)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(tr("Score du jour %@ sur 100, indicatif", "\(result.score)"))
    }
}


/// Réglage du plané (JB, 22/10/2026) : finesse 10 / 20 / 30 et marge d'arrivée au terrain (0, +300 m ou autre).
/// Alimente directement la marge de sécurité de Pilotage.
struct GlideSettingsCard: View {
    @EnvironmentObject var app: AppModel

    private var custom: Bool { !SafetyConfig.companion.ARRIVAL_PRESETS.map { Int(truncating: $0) }.contains(app.arrivalMargin) }

    var body: some View {
        WGCard(title: tr("Plané"), systemImage: "arrow.down.right", tint: WG.brandInk) {
            Text(tr("Finesse")).font(WG.subhead).foregroundStyle(WG.dim)
            Picker(tr("Finesse"), selection: $app.finesse) {
                ForEach(SafetyConfig.companion.FINESSES.map { Int(truncating: $0) }, id: \.self) { Text("F\($0)").tag($0) }
            }
            .pickerStyle(.segmented)
            Text(tr("Marge d'arrivée au terrain")).font(WG.subhead).foregroundStyle(WG.dim).padding(.top, 4)
            Picker(tr("Marge d'arrivée"), selection: Binding(get: { custom ? -1 : app.arrivalMargin }, set: { v in
                app.arrivalMargin = v == -1 ? 200 : v
            })) {
                Text("0 m").tag(0)
                Text("+300 m").tag(300)
                Text(tr("Autre")).tag(-1)
            }
            .pickerStyle(.segmented)
            if custom {
                Stepper(value: $app.arrivalMargin, in: 0...1000, step: 50) {
                    Text("+\(app.arrivalMargin) m").font(WG.number(20)).foregroundStyle(WG.sky)
                }
            }
            Text(tr("Calcul indicatif de la marge de sécurité en Pilotage : finesse réduite par le vent, arrivée à la hauteur choisie au-dessus du terrain."))
                .font(WG.caption).foregroundStyle(WG.dim)
        }
    }
}
