package com.neutronstar.glidercopilot.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * V19 — textes réglementaires (Google Play) publiés sur GitHub Pages depuis le dossier `site/` du dépôt.
 * Les mêmes adresses sont à saisir dans la Play Console (politique de confidentialité, suppression du compte).
 */
object GcLegal {
    const val BASE = "https://jeanbaptisteallaire.github.io/Glider-Copilot"
    const val PRIVACY = "$BASE/confidentialite.html"
    const val TERMS = "$BASE/conditions.html"
    const val DELETION = "$BASE/suppression-compte.html"
    const val CONTACT = "jb.allaire91@gmail.com"
}

/**
 * Liens « Confidentialité · Conditions · Suppression du compte » — accessibles depuis l'app, sans compte.
 * [color] : couleur des liens (sur fond photo, la page d'accueil passe une encre plus marquée).
 */
@Composable
fun GcLegalLinks(modifier: Modifier = Modifier, color: Color = Gc.colors.route, withDeletion: Boolean = true) {
    val uri = LocalUriHandler.current
    Column(modifier.testTag("legal-links"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LegalLink("Politique de confidentialité", color) { uri.openUri(GcLegal.PRIVACY) }
        LegalLink("Conditions d'utilisation", color) { uri.openUri(GcLegal.TERMS) }
        if (withDeletion) LegalLink("Supprimer mon compte et mes données", color) { uri.openUri(GcLegal.DELETION) }
    }
}

@Composable
private fun LegalLink(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text,
        style = Gc.type.footnote.copy(color = color, fontWeight = FontWeight.Medium),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick).padding(vertical = 6.dp),
    )
}
