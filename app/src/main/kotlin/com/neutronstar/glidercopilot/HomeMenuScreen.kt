package com.neutronstar.glidercopilot

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import com.neutronstar.glidercopilot.designsystem.GcIcons
import com.neutronstar.glidercopilot.designsystem.GlidyAdaptiveTheme
import com.neutronstar.glidercopilot.feature.prevol.PrevolDayPreview
import com.neutronstar.glidercopilot.feature.prevol.PrevolViewModel

private val CardShape = RoundedCornerShape(22.dp)

/**
 * V18.5 — menu d'accueil, trois grandes cartes. V20 « Wind Glider » (maquette de JB) : papier chaud, titres bleu nuit,
 * illustrations peintes (cockpit dessiné pour Pilotage, terrain et pilotes pour Mes vols), titres et sous-titres dans
 * la langue choisie, avec les mêmes noms que la barre d'onglets.
 */
@Composable
internal fun HomeMenuScreen(
    prevol: PrevolViewModel,
    lang: String,
    onPrevol: () -> Unit,
    onPilotage: () -> Unit,
    onMyFlights: () -> Unit,
    onTutorial: () -> Unit,
) {
    val fr = lang == "fr"
    GlidyAdaptiveTheme(light = true) {
        val c = Gc.colors
        Column(
            Modifier
                .fillMaxSize()
                .background(c.brandPaper)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.padding(start = 4.dp, top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.wg_logo), contentDescription = null, modifier = Modifier.size(44.dp).clip(CircleShape))
                Spacer(Modifier.width(12.dp))
                Text(APP_NAME_CAPS, style = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.Medium, fontSize = 20.sp, letterSpacing = 3.sp, color = c.brandInk))
            }

            // V19.1 — case fine « Tutoriel », tout en haut
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.panel)
                    .semantics { contentDescription = "Ouvrir le tutoriel" }
                    .clickable(role = Role.Button, onClick = onTutorial)
                    .padding(horizontal = 18.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(GcIcons.Tab.Tuto, contentDescription = null, tint = c.brandInk, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(if (fr) "Tutoriel" else "Tutorial", style = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = c.brandInk), modifier = Modifier.weight(1f))
                Icon(GcIcons.ChevronRight, contentDescription = null, tint = c.faint, modifier = Modifier.size(14.dp))
            }

            // Prévol & météo — carte blanche : la vraie journée du terrain
            Column(
                Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, CardShape, ambientColor = Color(0x1A1D3550), spotColor = Color(0x1A1D3550))
                    .clip(CardShape)
                    .background(c.panel)
                    .semantics { contentDescription = "Ouvrir Prévol" }
                    .clickable(role = Role.Button, onClick = onPrevol)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MenuTitle(if (fr) "Prévol & météo" else "Pre-flight & weather", c.brandInk)
                Box {
                    PrevolDayPreview(prevol)
                    // le graphique capte ses propres touchers : un voile transparent renvoie tout appui vers Prévol
                    Box(Modifier.matchParentSize().clickable(role = Role.Button, onClick = onPrevol))
                }
            }

            PhotoCard(
                if (fr) "Pilotage" else "Flight",
                if (fr) "Les données utiles, à leur place dans le cockpit." else "The data you need, right where it belongs in the cockpit.",
                R.drawable.wg_cockpit, androidx.compose.ui.BiasAlignment(0f, 0.12f), "Ouvrir Pilotage", onPilotage, // V20.1 : téléphone et ventouse visibles
            )
            PhotoCard(
                if (fr) "Mes vols" else "My flights",
                if (fr) "Retrouvez et revivez vos trajectoires." else "Find and relive your flights.",
                com.neutronstar.glidy.myflights.R.drawable.wg_flights_header, Alignment.Center, "Ouvrir Mes vols", onMyFlights,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MenuTitle(text: String, color: Color) {
    Text(text, style = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.3).sp, color = color))
}

/** V20 — carte illustrée : titre et sous-titre blancs sur le ciel, flèche ↗ en bas à droite. */
@Composable
private fun PhotoCard(title: String, subtitle: String, @DrawableRes image: Int, focus: Alignment, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .shadow(8.dp, CardShape, ambientColor = Color(0x1A1D3550), spotColor = Color(0x1A1D3550))
            .clip(CardShape)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Image(painterResource(image), contentDescription = null, contentScale = ContentScale.Crop, alignment = focus, modifier = Modifier.matchParentSize())
        Box(Modifier.fillMaxWidth().height(110.dp).background(Brush.verticalGradient(listOf(Color(0x66102A44), Color.Transparent))))
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.3).sp, color = Color.White, shadow = Shadow(Color(0x66000000), blurRadius = 10f)))
            Text(subtitle, style = TextStyle(fontFamily = GcFonts.ui, fontSize = 15.sp, color = Color.White, shadow = Shadow(Color(0x66000000), blurRadius = 8f)), modifier = Modifier.fillMaxWidth(0.62f))
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(14.dp).size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center,
        ) { Icon(GcIcons.ChevronRight, contentDescription = null, tint = WG_INK, modifier = Modifier.size(16.dp)) }
    }
}
