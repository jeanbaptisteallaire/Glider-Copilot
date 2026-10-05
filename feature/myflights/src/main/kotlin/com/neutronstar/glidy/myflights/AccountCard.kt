package com.neutronstar.glidy.myflights

import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neutronstar.glidercopilot.designsystem.Gc
import com.neutronstar.glidercopilot.designsystem.GcButton
import com.neutronstar.glidercopilot.designsystem.GcCard
import com.neutronstar.glidercopilot.designsystem.GcPill
import com.neutronstar.glidercopilot.designsystem.gcHeading

/**
 * Compte GLIDY optionnel (S12) — état fourni par l'application hôte. Ce module ne connaît pas Supabase
 * (frontière : feature:myflights ne dépend pas de data:flightcloud).
 */
data class AccountCardState(
    val configured: Boolean,
    val email: String? = null,
    val codeSentTo: String? = null,
    val busy: Boolean = false,
    val syncedCount: Int = 0,
    val totalCount: Int = 0,
    val message: String? = null,
    /** Faux pendant un vol enregistré : aucun envoi réseau en vol. */
    val canSync: Boolean = true,
    /** S18.2 — bouton « Continuer avec Google » proposé (ID client Google fourni au build). */
    val googleAvailable: Boolean = false,
    /** V19 — connexion par code e-mail proposée (SMTP configuré). */
    val emailAvailable: Boolean = true,
)

interface AccountActions {
    fun sendCode(email: String)
    fun verifyCode(code: String)
    fun cancelCode()
    /** S18.2 — connexion Google ; [context] = activité affichant la feuille de choix du compte. */
    fun signInWithGoogle(context: Context) {}
    fun syncNow()
    fun signOut()
    fun deleteAccount()
}

@Composable
internal fun AccountCard(state: AccountCardState, actions: AccountActions?) {
    val c = Gc.colors
    GcCard(
        modifier = Modifier.testTag("account-card"),
        title = "Sauvegarde en ligne",
        tint = c.sky,
        trailing = {
            // capitales en sombre (charte v8), casse normale sur les pages blanches (V18.1)
            when {
                !state.configured -> GcPill(gcHeading("Bientôt"), c.dim)
                state.email != null -> GcPill(gcHeading("Connecté"), c.ok)
                else -> GcPill(gcHeading("Facultatif"), c.dim)
            }
        },
    ) {
        when {
            !state.configured || actions == null -> Text(
                "Bientôt disponible. spiral fonctionne sans compte : vos vols restent sur ce téléphone.",
                style = Gc.type.bodySmall,
            )
            state.email == null -> SignIn(state, actions)
            else -> SignedIn(state, actions)
        }
        state.message?.let { Text(it, style = Gc.type.bodySmall.copy(color = c.warn)) }
    }
}

@Composable
private fun SignIn(state: AccountCardState, actions: AccountActions) {
    val c = Gc.colors
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    when {
        state.codeSentTo != null -> {
            Text("Code envoyé à ${state.codeSentTo}. Saisissez les 6 chiffres.", style = Gc.type.bodySmall)
            Field(code, { code = it.filter(Char::isDigit).take(8) }, "Code", KeyboardType.Number, "account-code")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GcButton("Valider", onClick = { actions.verifyCode(code) }, primary = true, enabled = !state.busy && code.length >= 6, modifier = Modifier.weight(1f))
                GcButton("Annuler", onClick = { code = ""; actions.cancelCode() }, modifier = Modifier.weight(1f))
            }
        }
        !open -> {
            Text("Vos vols sauvegardés, retrouvés sur un autre téléphone ou après une réinstallation.", style = Gc.type.bodySmall)
            GcButton("Se connecter", onClick = { open = true }, primary = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("account-sign-in"))
        }
        else -> {
            // V19 — information avant la connexion (Google Play : données personnelles et de localisation)
            Text(
                "En vous connectant, votre adresse e-mail et vos vols (fichiers IGC : traces GPS, horaires, altitudes) " +
                    "sont enregistrés sur notre serveur sécurisé en Irlande (UE). Vous pouvez tout supprimer à tout moment (voir « Politique de confidentialité » en bas de page).",
                style = Gc.type.footnote.copy(color = c.dim),
                modifier = Modifier.testTag("account-disclosure"),
            )
            if (state.googleAvailable) {
                GoogleButton(enabled = !state.busy) { actions.signInWithGoogle(context) }
            }
            if (state.googleAvailable && state.emailAvailable) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f), thickness = 0.5.dp, color = c.line)
                    Text("ou", style = Gc.type.bodySmall.copy(color = c.faint), modifier = Modifier.padding(horizontal = 10.dp))
                    HorizontalDivider(Modifier.weight(1f), thickness = 0.5.dp, color = c.line)
                }
            }
            if (state.emailAvailable) {
                Field(email, { email = it }, "Adresse e-mail", KeyboardType.Email, "account-email")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GcButton("Recevoir un code", onClick = { actions.sendCode(email) }, primary = !state.googleAvailable, enabled = !state.busy && email.contains('@'), modifier = Modifier.weight(1f))
                    GcButton("Annuler", onClick = { open = false }, modifier = Modifier.weight(1f))
                }
            } else {
                GcButton("Annuler", onClick = { open = false }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** S18.2 — « Continuer avec Google » : bouton blanc à filet, comme le veut la charte de connexion Google. */
@Composable
private fun GoogleButton(enabled: Boolean, onClick: () -> Unit) {
    val c = Gc.colors
    val social = Gc.social
    Box(
        Modifier.fillMaxWidth().height(if (social) 48.dp else 42.dp)
            .clip(RoundedCornerShape(if (social) 12.dp else 10.dp))
            .background(if (social) c.panel else c.control)
            .border(1.dp, c.line, RoundedCornerShape(if (social) 12.dp else 10.dp))
            .clickable(role = Role.Button, enabled = enabled, onClick = onClick)
            .testTag("account-google"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Continuer avec Google",
            style = if (social) Gc.type.headline.copy(fontSize = 16.sp, color = if (enabled) c.ink else c.faint)
            else Gc.type.body.copy(fontWeight = FontWeight.Bold, color = if (enabled) c.ink else c.faint),
        )
    }
}

@Composable
private fun SignedIn(state: AccountCardState, actions: AccountActions) {
    val c = Gc.colors
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Text(state.email.orEmpty(), style = Gc.type.body.copy(fontWeight = FontWeight.SemiBold))
    Text(
        "${state.syncedCount} / ${state.totalCount} vols sauvegardés" +
            if (!state.canSync) " · envoi suspendu en vol" else "",
        style = Gc.type.bodySmall.copy(color = if (state.syncedCount >= state.totalCount) c.ok else c.dim),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GcButton("Sauvegarder", onClick = actions::syncNow, primary = true, enabled = !state.busy && state.canSync, modifier = Modifier.weight(1f))
        GcButton("Se déconnecter", onClick = actions::signOut, enabled = !state.busy, modifier = Modifier.weight(1f))
    }
    val social = Gc.social
    Box(
        // V18.1 pages blanches : bouton destructif teinté (rouge sur rouge pâle), coins 12 dp ; sombre inchangé
        (if (social) Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(c.danger.copy(alpha = 0.08f))
        else Modifier.fillMaxWidth().height(36.dp).border(1.dp, c.danger.copy(alpha = 0.45f), RoundedCornerShape(10.dp)))
            .clickable(role = Role.Button, enabled = !state.busy) { confirmDelete = true },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Supprimer mon compte et mes vols en ligne",
            style = if (social) Gc.type.subhead.copy(fontWeight = FontWeight.SemiBold, color = c.danger)
            else Gc.type.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.danger),
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer le compte ?", style = Gc.type.body.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)) },
            text = {
                Text(
                    "Le compte ${state.email} et ses vols en ligne seront effacés définitivement. " +
                        "Les vols de ce téléphone sont conservés.",
                    style = Gc.type.body.copy(color = c.dim),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; actions.deleteAccount() }) {
                    Text(gcHeading("Supprimer"), style = Gc.type.body.copy(color = c.danger, fontWeight = FontWeight.Bold))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(gcHeading("Annuler"), style = Gc.type.body.copy(color = if (social) c.route else c.ink, fontWeight = FontWeight.Bold))
                }
            },
            containerColor = if (social) c.panel else c.control,
            shape = RoundedCornerShape(16.dp),
        )
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, type: KeyboardType, tag: String) {
    val c = Gc.colors
    // V18.1 pages blanches : focus en bleu ciel, plus de vert ; sombre inchangé
    val focus = if (Gc.social) c.route else c.ok
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        label = { Text(label, style = Gc.type.bodySmall) },
        textStyle = Gc.type.body,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = Modifier.fillMaxWidth().testTag(tag),
        shape = if (Gc.social) RoundedCornerShape(12.dp) else OutlinedTextFieldDefaults.shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = focus, unfocusedBorderColor = c.inputLine, cursorColor = focus,
            focusedTextColor = c.ink, unfocusedTextColor = c.ink,
        ),
    )
}
