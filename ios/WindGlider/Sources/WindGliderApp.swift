import SwiftUI

@main
struct WindGliderApp: App {
    @StateObject private var app = AppModel()

    var body: some Scene {
        WindowGroup {
            // Édition Lite : Prévol, Pilotage, Mes vols (comme BuildConfig.LITE sur Android, sans l'onglet Tuto pour l'instant)
            TabView {
                PrevolView().tabItem { Label("Prévol", systemImage: "sun.max") }
                PilotView().tabItem { Label("Pilotage", systemImage: "airplane") }
                MyFlightsView().tabItem { Label("Mes vols", systemImage: "person") }
            }
            .tint(WG.sky)
            .environmentObject(app)
            .environment(\.locale, Locale(identifier: "fr_FR"))
            .task { app.startEngine() }
        }
    }
}
