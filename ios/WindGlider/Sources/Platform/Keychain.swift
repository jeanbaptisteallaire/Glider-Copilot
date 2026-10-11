import Foundation
import Security
import WindGliderShared

/// Session Supabase rangée dans le Keychain (équivalent iOS des préférences privées d'Android, CloudHost.PrefsSessionStore).
/// Accessible après le premier déverrouillage, jamais sauvegardée hors de cet iPhone.
final class KeychainSessionStore: NSObject, SessionStore {
    private let service = "com.neutronstar.glidercopilot.session"

    func load() -> CloudSession? {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
                                    kSecReturnData as String: true, kSecMatchLimit as String: kSecMatchLimitOne]
        var out: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &out) == errSecSuccess, let data = out as? Data,
              let d = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let access = d["access"] as? String else { return nil }
        return CloudSession(accessToken: access, refreshToken: d["refresh"] as? String ?? "", userId: d["user"] as? String ?? "",
                            email: d["email"] as? String ?? "", expiresAtEpochSeconds: (d["expires"] as? NSNumber)?.int64Value ?? 0)
    }

    func save(session: CloudSession?) {
        let base: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service]
        SecItemDelete(base as CFDictionary)
        guard let s = session else { return }
        let d: [String: Any] = ["access": s.accessToken, "refresh": s.refreshToken, "user": s.userId, "email": s.email,
                                "expires": NSNumber(value: s.expiresAtEpochSeconds)]
        guard let data = try? JSONSerialization.data(withJSONObject: d) else { return }
        var add = base
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(add as CFDictionary, nil)
    }
}
