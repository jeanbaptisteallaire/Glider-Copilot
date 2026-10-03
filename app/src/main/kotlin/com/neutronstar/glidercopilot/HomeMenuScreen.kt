package com.neutronstar.glidercopilot

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neutronstar.glidercopilot.designsystem.Gc
import com.neutronstar.glidercopilot.designsystem.GcFonts
import com.neutronstar.glidercopilot.designsystem.GlidyAdaptiveTheme
import com.neutronstar.glidercopilot.feature.prevol.PrevolDayPreview
import com.neutronstar.glidercopilot.feature.prevol.PrevolViewModel

private val CardShape = RoundedCornerShape(22.dp)

/**
 * V18.5 — menu d'accueil spiral, après la page de connexion : trois grandes cartes arrondies.
 * 1° Préparation et météo : la vraie courbe des plafonds de la journée (météo prévue du terrain) → onglet Prévol.
 * 2° Calculateur de vol : photo du cockpit → Pilotage. 3° Carnet 3D : rejeu → Mes vols.
 * Photos à 90 % d'opacité, recadrées à 1,45:1 (fichiers de « 1 Graphic assets »).
 */
@Composable
internal fun HomeMenuScreen(
    prevol: PrevolViewModel,
    onPrevol: () -> Unit,
    onPilotage: () -> Unit,
    onMyFlights: () -> Unit,
) {
    GlidyAdaptiveTheme(light = true) {
        val c = Gc.colors
        Column(
            Modifier
                .fillMaxSize()
                .background(c.background)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.padding(start = 8.dp, top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.spiral_logo), contentDescription = null, modifier = Modifier.height(40.dp).alpha(0.6f))
                Spacer(Modifier.width(14.dp))
                Text("spiral", style = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.Normal, fontSize = 30.sp, color = c.faint))
            }

            // 1° — carte blanche : titre puis aperçu réel de la journée
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(c.panel)
                    .semantics { contentDescription = "Ouvrir Prévol" }
                    .clickable(role = Role.Button, onClick = onPrevol)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MenuTitle("1° Preflight & weather")
                Box {
                    PrevolDayPreview(prevol)
                    // le graphique capte ses propres touchers : un voile transparent renvoie tout appui vers Prévol
                    Box(Modifier.matchParentSize().clickable(role = Role.Button, onClick = onPrevol))
                }
            }

            PhotoCard("2° Flight computer", R.drawable.menu_flight_computer, "Ouvrir Pilotage", onPilotage)
            PhotoCard("3° 3D flight log", R.drawable.menu_flight_log, "Ouvrir Mes vols", onMyFlights)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MenuTitle(text: String) {
    Text(text, style = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.3).sp, color = Gc.colors.ink))
}

/** Carte photo arrondie : image à 90 % d'opacité, bandeau clair dégradé en haut pour le titre. */
@Composable
private fun PhotoCard(title: String, @DrawableRes image: Int, description: String, onClick: () -> Unit) {
    val c = Gc.colors
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.45f)
            .clip(CardShape)
            .background(c.panel)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Image(
            painterResource(image),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().alpha(0.9f),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(76.dp)
                .background(Brush.verticalGradient(listOf(c.panel.copy(alpha = 0.72f), c.panel.copy(alpha = 0.0f)))),
        )
        Box(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) { MenuTitle(title) }
    }
}
