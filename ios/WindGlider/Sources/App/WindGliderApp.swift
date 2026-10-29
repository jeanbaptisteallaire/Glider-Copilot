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
                .environmentObject(app.flight)
                .environmentObject(mapControl)
                .environmentObject(account)
                .onOpenURL { GIDSignIn.sharedInstance.handle($0) }
                .environment(\.locale, Locale(identifier: "fr_FR"))
                // charte Godspeed : toute l'app en sombre, cyan pour ce qui se touche (interrupteurs, liens)
                .preferredColorScheme(.dark)
                .tint(WG.sky)
                .task { app.startEngine() }
        }
    }
}

/// Édition Lite : Prévol, Pilotage, Mes vols (comme BuildConfig.LITE sur Android, sans l'onglet Tuto pour l'instant).
/// Premier lancement : avertissement de sécurité ; « Avant de voler » une fois par lancement avant Pilotage.
struct RootView: View {
    /// Build de développement seulement : `-bench` ouvre directement Pilotage (mesures d'énergie reproductibles
    /// sur l'iPhone, sans toucher l'écran). L'avertissement de prévol doit avoir été acquitté une fois ; jamais en version publiée.
    static var bench: Bool {
        #if DEBUG
        ProcessInfo.processInfo.arguments.contains("-bench")
        #else
        false
        #endif
    }

    /// Redémarrage de l'app pendant un vol (mémoire, plantage) : retour direct à Pilotage, sans accueil ni tutoriel
    /// (revue résilience M9). Vrai si un enregistrement battait encore il y a moins de 10 min.
    static let resumeInFlight: Bool = {
        let t = UserDefaults.standard.double(forKey: AppModel.flightInProgressKey)
        return t > 0 && Date().timeIntervalSince1970 - t < 10 * 60   // battement de cœur toutes les 30 s pendant un vol
    }()

    @EnvironmentObject var app: AppModel
    @AppStorage("disclaimerVersion") private var disclaimerVersion = 0
    @State private var tab: AppTab = TutorialView.launchStep != nil ? .tuto : RootView.bench || RootView.resumeInFlight ? .pilotage : .prevol
    @AppStorage("guideLanguage") private var lang = Locale.current.language.languageCode?.identifier == "fr" ? "fr" : "en"
    @State private var pilotNoticeSeen = RootView.bench || RootView.resumeInFlight
    @Environment(\.scenePhase) private var phase
    /// couverture + connexion à chaque ouverture tant qu'aucun compte n'est connecté (AppRoot.kt, splashDone)
    @State private var welcomeDone = RootView.bench || RootView.resumeInFlight
    @EnvironmentObject var account: AccountModel
    // Tutoriel : plus imposé au premier lancement (JB, 29/10/2026) ; il ne s'ouvre que depuis l'onglet Tuto.

    var body: some View {
        if !welcomeDone {
            WelcomeView { welcomeDone = true }
        } else if disclaimerVersion < DisclaimerView.version {
            DisclaimerView { disclaimerVersion = DisclaimerView.version }
        } else {
            TabView(selection: $tab) {
                ForEach(AppTab.visible) { t in
                    screen(t).id(lang).tabItem { Label(t.label(lang), systemImage: t.icon) }.tag(t)
                }
            }
            .tint(WG.sky)
            .onChange(of: phase) { _, p in
                if p == .background { app.enterBackground() } else if p == .active { app.enterForeground() }
            }
        }
    }
}

extension RootView {
    @ViewBuilder func screen(_ t: AppTab) -> some View {
        switch t {
        case .tuto: TutoHomeView()
        case .prevol: PrevolView()
        case .pilotage: if pilotNoticeSeen { PilotView() } else { PilotNoticeView { pilotNoticeSeen = true } }
        case .mesVols: MyFlightsView()
        case .checklist: ChecklistView()
        }
    }
}
