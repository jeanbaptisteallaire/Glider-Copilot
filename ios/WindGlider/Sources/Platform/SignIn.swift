import AuthenticationServices
import CryptoKit
import GoogleSignIn
import UIKit

/// Jeton d'identité obtenu sur l'iPhone, échangé ensuite contre une session Supabase (même chemin qu'Android :
/// SupabaseClient.signInWithIdToken). [rawNonce] part chez Supabase, son empreinte SHA-256 chez Google / Apple.
struct IdToken { let provider: String; let token: String; let rawNonce: String }

enum SignInError: Error { case cancelled, failed(String) }

enum Nonce {
    static func make() -> (raw: String, hashed: String) {
        var bytes = [UInt8](repeating: 0, count: 24)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        let raw = bytes.map { String(format: "%02x", $0) }.joined()
        let hashed = SHA256.hash(data: Data(raw.utf8)).map { String(format: "%02x", $0) }.joined()
        return (raw, hashed)
    }
}

/// « Continuer avec Google » : SDK GoogleSignIn (client iOS du projet Google Cloud GLIDY, GIDClientID dans Info.plist).
enum GoogleLogin {
    static var available: Bool {
        (Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String)?.hasSuffix(".apps.googleusercontent.com") == true
            && !((Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String)?.hasPrefix("000000") ?? true)
    }

    @MainActor
    static func request() async throws -> IdToken {
        guard let root = UIApplication.shared.connectedScenes.compactMap({ ($0 as? UIWindowScene)?.keyWindow?.rootViewController }).first
        else { throw SignInError.failed("Fenêtre introuvable") }
        var top = root
        while let p = top.presentedViewController { top = p }
        let nonce = Nonce.make()
        do {
            let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: top, hint: nil, additionalScopes: nil, nonce: nonce.hashed)
            guard let token = result.user.idToken?.tokenString else { throw SignInError.failed("Jeton Google absent") }
            return IdToken(provider: "google", token: token, rawNonce: nonce.raw)
        } catch let e as NSError where e.domain == kGIDSignInErrorDomain && e.code == GIDSignInError.canceled.rawValue {
            throw SignInError.cancelled
        }
    }
}

/// « Se connecter avec Apple » : AuthenticationServices (capacité Sign in with Apple de l'App ID).
@MainActor
final class AppleLogin: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private var continuation: CheckedContinuation<IdToken, Error>?
    private var nonce = Nonce.make()
    /// gardé jusqu'à la réponse : un contrôleur libéré en cours de route fait échouer la demande (vu sur iPad)
    private var controller: ASAuthorizationController?

    func request() async throws -> IdToken {
        // une demande précédente restée sans réponse est close proprement avant d'en lancer une autre
        finish(.failure(SignInError.cancelled))
        nonce = Nonce.make()
        let req = ASAuthorizationAppleIDProvider().createRequest()
        req.requestedScopes = [.email]
        req.nonce = nonce.hashed
        let controller = ASAuthorizationController(authorizationRequests: [req])
        controller.delegate = self
        controller.presentationContextProvider = self
        self.controller = controller
        return try await withCheckedThrowingContinuation { c in
            continuation = c
            controller.performRequests()
        }
    }

    /// Fenêtre de la scène au premier plan (iPad en mode compatibilité, multifenêtre) : jamais une fenêtre vide.
    nonisolated func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        MainActor.assumeIsolated {
            let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            let active = scenes.filter { $0.activationState == .foregroundActive } + scenes
            for scene in active {
                if let w = scene.keyWindow ?? scene.windows.first(where: { $0.isKeyWindow }) ?? scene.windows.first { return w }
            }
            return ASPresentationAnchor()
        }
    }

    nonisolated func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        MainActor.assumeIsolated {
            // réponse d'un contrôleur déjà remplacé : son nonce n'est plus le bon, ne pas clore la demande en cours
            guard controller === self.controller else { return }
            guard let cred = authorization.credential as? ASAuthorizationAppleIDCredential,
                  let data = cred.identityToken, let token = String(data: data, encoding: .utf8) else {
                finish(.failure(SignInError.failed("Jeton Apple absent"))); return
            }
            finish(.success(IdToken(provider: "apple", token: token, rawNonce: nonce.raw)))
        }
    }

    nonisolated func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        MainActor.assumeIsolated {
            guard controller === self.controller else { return }
            switch (error as? ASAuthorizationError)?.code {
            case .canceled: finish(.failure(SignInError.cancelled))
            // 1000 : aucun compte Apple sur l'appareil (ou capacité absente de l'App ID)
            case .unknown: finish(.failure(SignInError.failed(tr("Connexion Apple indisponible : connectez-vous à votre compte Apple dans Réglages."))))
            default: finish(.failure(SignInError.failed(tr("Connexion Apple impossible, réessayez."))))
            }
        }
    }

    private func finish(_ r: Result<IdToken, Error>) {
        continuation?.resume(with: r)
        continuation = nil
        controller = nil
    }
}
