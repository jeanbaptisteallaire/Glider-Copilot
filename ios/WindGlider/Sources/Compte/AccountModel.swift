import Foundation
import WindGliderShared

/// État du compte (équivalent iOS de CloudHost d'Android) : même projet Supabase, même client Kotlin (IosAccount).
/// Invité = aucune session : tout reste sur l'iPhone.
@MainActor
final class AccountModel: ObservableObject {
    @Published private(set) var email: String?
    @Published private(set) var busy = false
    @Published var message: String?
    /// vols sauvegardés en ligne / total (carte « Sauvegarde en ligne » de Mes vols)
    @Published private(set) var synced = 0
    @Published private(set) var total = 0
    /// incrémenté quand des vols ont été restaurés : Mes vols relit le carnet
    @Published private(set) var restoredTick = 0

    private let account: IosAccount
    private let apple = AppleLogin()
    let archive = IosFlightArchive(dir: AppModel.igcDir)
    /// vrai pendant un vol enregistré : aucun envoi réseau en vol (même règle qu'Android)
    var isRecording: () -> Bool = { false }

    init() {
        let info = Bundle.main.infoDictionary ?? [:]
        account = IosAccount(url: info["SUPABASE_URL"] as? String ?? "", anonKey: info["SUPABASE_ANON_KEY"] as? String ?? "",
                             store: KeychainSessionStore())
        email = account.email()
    }

    var configured: Bool { account.configured }
    var googleAvailable: Bool { configured && GoogleLogin.available }
    var appleAvailable: Bool { configured }
    var signedIn: Bool { email != nil }
    /// Clé du profil pour le tutoriel obligatoire (comme tutorialKeyFor d'Android) : « guest » ou identifiant du compte.
    var tutorialKey: String { account.userId() ?? "guest" }

    func signInWithGoogle() { signIn(tr("Connexion Google…")) { try await GoogleLogin.request() } }
    func signInWithApple() { signIn(tr("Connexion Apple…")) { [apple] in try await apple.request() } }

    func signOut() {
        let a = account
        Task.detached { a.signOut() }
        email = nil
        message = tr("Déconnecté. Vos vols restent sur l'iPhone.")
    }

    func deleteAccount() {
        let arch = archive
        run(tr("Suppression du compte…")) { a in a.deleteAccount(archive: arch) } onSuccess: { [weak self] in
            self?.refreshCounts()
            self?.email = nil
            self?.message = tr("Compte supprimé. Vos vols restent sur l'iPhone.")
        }
    }

    /// À l'affichage de Mes vols : compteurs à jour, sauvegarde si connecté et au sol (CloudHost.onShown).
    func onShown() {
        refreshCounts()
        if signedIn && !isRecording() { syncNow() }
    }

    func syncNow() {
        guard signedIn, !busy else { return }
        if isRecording() { message = tr("Envoi suspendu pendant le vol"); return }
        busy = true
        message = tr("Sauvegarde…")
        let a = account, arch = archive
        Task {
            let before = Int(arch.counts().get(index: 1))
            let report = await Task.detached { a.sync(archive: arch) }.value
            busy = false
            message = report
            email = account.email()
            refreshCounts()
            if total != before || report.contains("restauré") { restoredTick += 1 }
        }
    }

    func refreshCounts() {
        let c = archive.counts()
        synced = Int(c.get(index: 0)); total = Int(c.get(index: 1))
    }

    func sendCode(_ mail: String) async -> Bool { await call { $0.sendCode(email: mail) } }
    func verifyCode(_ mail: String, _ code: String) async -> Bool {
        let ok = await call { $0.verifyCode(email: mail, code: code) }
        if ok { email = account.email(); syncNow() }
        return ok
    }

    func sendFeedback(_ text: String, lang: String) async -> Bool {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
        let a = account
        return await Task.detached { a.sendFeedback(text: text, lang: lang, appVersion: "ios-" + version) == nil }.value
    }

    private func signIn(_ busyText: String, token: @escaping @MainActor () async throws -> IdToken) {
        guard !busy else { return }
        busy = true
        message = busyText
        Task {
            defer { busy = false }
            do {
                let t = try await token()
                let a = account
                let err = await Task.detached { a.signInWithIdToken(provider: t.provider, idToken: t.token, rawNonce: t.rawNonce) }.value
                if let err { message = err } else { email = account.email(); message = nil; Task { syncNow() } }   // après le defer (busy encore vrai ici)
            } catch SignInError.cancelled {
                message = nil
            } catch SignInError.failed(let m) {
                message = m
            } catch {
                message = tr("Échec : %@", error.localizedDescription)
            }
        }
    }

    private func call(_ f: @escaping (IosAccount) -> String?) async -> Bool {
        busy = true
        defer { busy = false }
        let a = account
        let err = await Task.detached { f(a) }.value
        message = err
        return err == nil
    }

    private func run(_ busyText: String, _ f: @escaping (IosAccount) -> String?, onSuccess: @escaping () -> Void) {
        message = busyText
        Task { if await call(f) { onSuccess() } }
    }
}
