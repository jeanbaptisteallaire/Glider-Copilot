import SwiftUI

/// Carte « Sauvegarde en ligne » de Mes vols (AccountCard.kt d'Android) : connexion Google / Apple, état de la
/// sauvegarde, déconnexion et suppression du compte (exigée par Apple pour toute app avec compte).
struct AccountCard: View {
    @EnvironmentObject var account: AccountModel
    @State private var confirmDelete = false

    var body: some View {
        WGCard(title: tr("Sauvegarde en ligne"), systemImage: "icloud", tint: WG.sky) {
            VStack(alignment: .leading, spacing: 10) {
                if !account.configured {
                    Text(tr("Bientôt disponible. Wind Glider fonctionne sans compte : vos vols restent sur cet iPhone."))
                        .font(WG.subhead).foregroundStyle(WG.dim)
                } else if let mail = account.email {
                    Text(mail).font(WG.body.weight(.semibold)).foregroundStyle(WG.brandInk)
                    Text(tr("%d / %d vols sauvegardés", Int(account.synced), Int(account.total))).font(WG.subhead).foregroundStyle(WG.dim)
                    HStack(spacing: 8) {
                        pill(tr("Sauvegarder"), primary: true) { account.syncNow() }
                        pill(tr("Se déconnecter"), primary: false) { account.signOut() }
                    }
                    Button(tr("Supprimer mon compte et mes vols en ligne")) { confirmDelete = true }
                        .font(WG.footnote).foregroundStyle(WG.bad).disabled(account.busy)
                } else {
                    Text(tr("Vos vols sauvegardés, retrouvés sur un autre téléphone ou après une réinstallation."))
                        .font(WG.subhead).foregroundStyle(WG.dim)
                    if account.googleAvailable { pill(tr("Continuer avec Google"), primary: true) { account.signInWithGoogle() } }
                    if account.appleAvailable { pill(tr("Se connecter avec Apple"), primary: !account.googleAvailable) { account.signInWithApple() } }
                    Text(tr("En vous connectant, votre adresse e-mail et vos vols (fichiers IGC : traces GPS, horaires, altitudes) sont enregistrés sur notre serveur sécurisé en Irlande (UE). Vous pouvez tout supprimer à tout moment."))
                        .font(WG.footnote).foregroundStyle(WG.dim)
                }
                if let m = account.message { Text(m).font(WG.footnote).foregroundStyle(WG.warn) }
            }
        }
        .alert(tr("Supprimer le compte ?"), isPresented: $confirmDelete) {
            Button(tr("Supprimer"), role: .destructive) { account.deleteAccount() }
            Button(tr("Annuler"), role: .cancel) {}
        } message: {
            Text(tr("Le compte %@ et ses vols en ligne seront effacés définitivement. Les vols de cet iPhone sont conservés.", account.email ?? ""))
        }
    }

    private func pill(_ text: String, primary: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text).font(WG.headline).frame(maxWidth: .infinity, minHeight: 46)
                .background(primary ? WG.sky : WG.sky.opacity(0.14), in: RoundedRectangle(cornerRadius: 12))
                .foregroundStyle(primary ? .white : WG.sky)
        }
        .disabled(account.busy)
    }
}
