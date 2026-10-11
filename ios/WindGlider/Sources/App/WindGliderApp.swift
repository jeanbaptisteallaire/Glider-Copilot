import GoogleSignIn
import SwiftUI

@main
struct WindGliderApp: App {
    @StateObject private var app = AppModel()
    @StateObject private var mapControl = MapControl()
    @StateObject private var account = AccountModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(app)
                .environmentObject(mapControl)
                .environmentObject(account)
                .onOpenURL { GIDSignIn.sharedInstance.handle($0) }
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
    @Environment(\.scenePhase) private var phase
    /// couverture + connexion à chaque ouverture tant qu'aucun compte n'est connecté (AppRoot.kt, splashDone)
    @State private var welcomeDone = false
    @EnvironmentObject var account: AccountModel
    /// profils (« guest » ou identifiant du compte) ayant terminé le tutoriel sur cet iPhone (V20.1 d'Android)
    @AppStorage("tutorialSeen") private var tutorialSeen = ""

    var body: some View {
        if !welcomeDone {
            WelcomeView { welcomeDone = true }
        } else if disclaimerVersion < DisclaimerView.version {
            DisclaimerView { disclaimerVersion = DisclaimerView.version }
        } else if !tutorialSeen.split(separator: ",").contains(Substring(account.tutorialKey)) && !app.pilot.recording {
            // tutoriel complet et obligatoire au premier lancement de chaque profil (ni « Passer »)
            TutorialView(mandatory: true) {
                tutorialSeen = (tutorialSeen.isEmpty ? "" : tutorialSeen + ",") + account.tutorialKey
            }
        } else {
            TabView(selection: $tab) {
                TutoHomeView().tabItem { Label("Tuto", systemImage: "graduationcap") }.tag(3)
                PrevolView().tabItem { Label("Prévol", systemImage: "sun.max") }.tag(0)
                Group {
                    if pilotNoticeSeen { PilotView() } else { PilotNoticeView { pilotNoticeSeen = true } }
                }
                .tabItem { Label("Pilotage", systemImage: "airplane") }.tag(1)
                MyFlightsView().tabItem { Label("Mes vols", systemImage: "person") }.tag(2)
            }
            .tint(WG.sky)
            .onChange(of: phase) { _, p in
                if p == .background { app.enterBackground() } else if p == .active { app.enterForeground() }
            }
        }
    }
}
