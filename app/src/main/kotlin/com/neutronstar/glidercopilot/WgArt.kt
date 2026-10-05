package com.neutronstar.glidercopilot

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.neutronstar.glidercopilot.designsystem.GcFonts

/*
 * V20 « Wind Glider » — outils de l'univers illustré.
 * Les illustrations (format ~9:16 à 9:21) sont posées en bas de l'écran ; sur un téléphone plus haut, le ciel est
 * prolongé par sa couleur unie, avec un fondu doux sur le bord de l'image : aucune bande visible.
 */

/** Illustration plein écran ancrée en bas, ciel prolongé vers le haut si l'écran est plus haut que l'image. */
@Composable
internal fun ScenicBackground(
    @DrawableRes image: Int,
    imageWidth: Int,
    imageHeight: Int,
    sky: Color,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(sky)) {
        val drawnH = maxWidth * (imageHeight.toFloat() / imageWidth)
        if (drawnH >= maxHeight) {
            Image(
                painterResource(image), contentDescription = null, contentScale = ContentScale.Crop,
                alignment = Alignment.BottomCenter, modifier = Modifier.fillMaxSize().then(imageModifier),
            )
        } else {
            Box(Modifier.fillMaxWidth().height(drawnH).align(Alignment.BottomCenter).then(imageModifier)) {
                Image(painterResource(image), contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxSize())
                // fondu ciel → image sur 72 dp : la jonction disparaît
                Box(Modifier.fillMaxWidth().height(72.dp).background(Brush.verticalGradient(listOf(sky, sky.copy(alpha = 0f)))))
            }
        }
    }
}

/** Titre de marque : Inter en capitales, lettres espacées, blanc avec une ombre très douce (lisible sur le ciel). */
internal fun brandTitleStyle(size: TextUnit = 58.sp, color: Color = Color.White) = TextStyle(
    fontFamily = GcFonts.ui,
    fontWeight = FontWeight.Normal,
    fontSize = size,
    lineHeight = size * 1.12f,
    letterSpacing = size * 0.06f,
    textAlign = TextAlign.Center,
    color = color,
    shadow = Shadow(Color(0x40102030), blurRadius = 18f),
)

internal const val APP_NAME_CAPS = "WIND GLIDER"

/** Bouton de l'univers « Wind Glider » : carte blanche aux coins doux, texte bleu nuit, ombre légère. */
@Composable
internal fun WgButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, tag: String = "", enabled: Boolean = true) {
    androidx.compose.foundation.layout.Box(
        modifier
            .height(56.dp)
            .then(androidx.compose.ui.Modifier.shadow(10.dp, androidx.compose.foundation.shape.RoundedCornerShape(14.dp), ambientColor = Color(0x33102030), spotColor = Color(0x33102030)))
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.95f))
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .semantics { contentDescription = text }
            .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text,
            style = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.Medium, fontSize = 17.sp, letterSpacing = 0.2.sp, color = WG_INK),
        )
    }
}

internal val WG_INK = Color(0xFF1D3550)
