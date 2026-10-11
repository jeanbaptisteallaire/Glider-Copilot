import SwiftUI

@main
struct WindGliderApp: App {
    @StateObject private var app = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(app)
                .environment(\.locale, Locale(identifier: "fr_FR"))
                .task { app.startEngine() }
        }
    }
}

/// Édition Lite : Prévol, Pilotage, Mes vols (comme BuildConfig.LITE sur Android, sans l'onglet Tuto pour l'instant).
/// Premier lancement : avertissement de sécurité ; « Avant de voler » une fois par lancement avant Pilotage.
struct RootView: View {
    @EnvironmentObject var app: AppModel
    @AppStorage("disclaimerVersion") private var disclaimerVersion = 0
    @State private var tab = 0
    @State private var pilotNoticeSeen = false

    var body: some View {
        if disclaimerVersion < DisclaimerView.version {
            DisclaimerView { disclaimerVersion = DisclaimerView.version }
        } else {
            TabView(selection: $tab) {
                PrevolView().tabItem { Label("Prévol", systemImage: "sun.max") }.tag(0)
                Group {
                    if pilotNoticeSeen { PilotView() } else { PilotNoticeView { pilotNoticeSeen = true } }
                }
                .tabItem { Label("Pilotage", systemImage: "airplane") }.tag(1)
                MyFlightsView().tabItem { Label("Mes vols", systemImage: "person") }.tag(2)
            }
            .tint(WG.sky)
        }
    }
}
