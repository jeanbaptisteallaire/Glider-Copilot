package com.neutronstar.glidy.myflights

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
)

interface AccountActions {
    fun sendCode(email: String)
    fun verifyCode(code: String)
    fun cancelCode()
    fun syncNow()
    fun signOut()
    fun deleteAccount()
}

@Composable
internal fun AccountCard(state: AccountCardState, actions: AccountActions?) {
    val c = Gc.colors
    GcCard(
        modifier = Modifier.testTag("account-card"),
        title = "Compte · sauvegarde en ligne",
        trailing = {
            when {
                !state.configured -> GcPill("BIENTÔT", c.dim)
                state.email != null -> GcPill("CONNECTÉ", c.ok)
                else -> GcPill("OPTIONNEL", c.dim)
            }
        },
    ) {
        when {
            !state.configured || actions == null -> Text(
                "Sauvegarde de vos vols en ligne et connexion par e-mail : bientôt disponible. " +
                    "GLIDY fonctionne entièrement sans compte, vos vols restent sur ce téléphone.",
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
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    if (state.codeSentTo == null) {
        Text("Compte facultatif : retrouvez vos vols sur un nouveau téléphone. Aucun mot de passe, un code arrive par e-mail.", style = Gc.type.bodySmall)
        Field(email, { email = it }, "Adresse e-mail", KeyboardType.Email, "account-email")
        GcButton("Recevoir un code", onClick = { actions.sendCode(email) }, primary = true, enabled = !state.busy && email.contains('@'), modifier = Modifier.fillMaxWidth())
    } else {
        Text("Code envoyé à ${state.codeSentTo}. Saisissez les 6 chiffres reçus.", style = Gc.type.bodySmall)
        Field(code, { code = it.filter(Char::isDigit).take(8) }, "Code", KeyboardType.Number, "account-code")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GcButton("Se connecter", onClick = { actions.verifyCode(code) }, primary = true, enabled = !state.busy && code.length >= 6, modifier = Modifier.weight(1f))
            GcButton("Annuler", onClick = { code = ""; actions.cancelCode() }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SignedIn(state: AccountCardState, actions: AccountActions) {
    val c = Gc.colors
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Text(state.email.orEmpty(), style = Gc.type.body.copy(fontWeight = FontWeight.SemiBold))
    Text(
        "${state.syncedCount} / ${state.totalCount} vols sauvegardés en ligne" +
            if (!state.canSync) " · envoi suspendu pendant le vol" else "",
        style = Gc.type.bodySmall.copy(color = if (state.syncedCount >= state.totalCount) c.ok else c.dim),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GcButton("Sauvegarder", onClick = actions::syncNow, primary = true, enabled = !state.busy && state.canSync, modifier = Modifier.weight(1f))
        GcButton("Se déconnecter", onClick = actions::signOut, enabled = !state.busy, modifier = Modifier.weight(1f))
    }
    Box(
        Modifier.fillMaxWidth().height(36.dp).border(1.dp, c.danger.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, enabled = !state.busy) { confirmDelete = true },
        contentAlignment = Alignment.Center,
    ) { Text("Supprimer mon compte et mes vols en ligne", style = Gc.type.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.danger)) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer le compte ?", style = Gc.type.body.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)) },
            text = {
                Text(
                    "Le compte ${state.email} et tous les vols sauvegardés en ligne seront effacés définitivement. " +
                        "Les vols présents sur ce téléphone sont conservés.",
                    style = Gc.type.body.copy(color = c.dim),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; actions.deleteAccount() }) {
                    Text("SUPPRIMER", style = Gc.type.body.copy(color = c.danger, fontWeight = FontWeight.Bold))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("ANNULER", style = Gc.type.body.copy(color = c.ink, fontWeight = FontWeight.Bold)) }
            },
            containerColor = c.control,
            shape = RoundedCornerShape(16.dp),
        )
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, type: KeyboardType, tag: String) {
    val c = Gc.colors
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        label = { Text(label, style = Gc.type.bodySmall) },
        textStyle = Gc.type.body,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = Modifier.fillMaxWidth().testTag(tag),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.ok, unfocusedBorderColor = c.inputLine, cursorColor = c.ok,
            focusedTextColor = c.ink, unfocusedTextColor = c.ink,
        ),
    )
}
