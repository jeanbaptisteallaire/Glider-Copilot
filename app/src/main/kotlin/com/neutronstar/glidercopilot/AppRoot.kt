package com.neutronstar.glidercopilot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neutronstar.glidercopilot.designsystem.Gc
import com.neutronstar.glidercopilot.feature.flight.FlightScreen
import com.neutronstar.glidercopilot.feature.prevol.PrevolScreen
import com.neutronstar.glidercopilot.feature.prevol.PrevolViewModel
import kotlinx.coroutines.launch

private const val DISCLAIMER_VERSION = 1

private enum class Tab(val label: String) { PREVOL("Prévol"), FLIGHT("Vol") }

@Composable
fun AppRoot(container: AppContainer) {
    val ack by container.prefs.acknowledgedDisclaimer.collectAsState(initial = -1)
    val scope = rememberCoroutineScope()
    when {
        ack == -1 -> Box(Modifier.fillMaxSize().background(Gc.colors.background))
        ack < DISCLAIMER_VERSION -> Disclaimer { scope.launch { container.prefs.acknowledgeDisclaimer(DISCLAIMER_VERSION) } }
        else -> MainScaffold(container)
    }
}

@Composable
private fun MainScaffold(container: AppContainer) {
    val c = Gc.colors
    var tab by rememberSaveable { mutableStateOf(Tab.PREVOL) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val prevolVm: PrevolViewModel = viewModel(factory = PrevolViewModel.Factory(container.weather, container.clubs))

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.PREVOL -> PrevolScreen(prevolVm)
                Tab.FLIGHT -> FlightScreen()
            }
        }
        Row(
            Modifier.fillMaxWidth().background(c.panel).navigationBarsPadding().height(60.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { t ->
                val selected = t == tab
                TextButton(
                    onClick = {
                        when {
                            t == tab -> Unit
                            tab == Tab.FLIGHT -> confirmLeave = true   // quitter le vol : deux gestes
                            else -> tab = t
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        t.label.uppercase(),
                        style = Gc.type.eyebrow.copy(color = if (selected) c.ink else c.faint, fontSize = 13.sp),
                    )
                }
            }
        }
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = c.panel,
            title = { Text("Quitter l'écran de vol ?", style = Gc.type.body.copy(fontWeight = FontWeight.Bold)) },
            text = { Text("En vol, l'écran Prévol n'apporte rien à la sécurité.", style = Gc.type.bodySmall) },
            confirmButton = { TextButton(onClick = { confirmLeave = false; tab = Tab.PREVOL }) { Text("Ouvrir Prévol") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Rester") } },
        )
    }
}

@Composable
private fun Disclaimer(onAccept: () -> Unit) {
    val c = Gc.colors
    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text("GLIDER\nCOPILOT", style = Gc.type.giant.copy(color = c.ink, fontSize = 56.sp, lineHeight = 52.sp))
        Spacer(Modifier.height(16.dp))
        Text(
            "Aide secondaire au vol à voile. L'instrumentation de bord et la veille extérieure priment. " +
                "Cette application ne remplace ni le vario, ni le calculateur, ni le FLARM, et n'est pas un moyen de navigation certifié. " +
                "Le pilote reste seul responsable de la conduite de son vol.",
            style = Gc.type.body.copy(lineHeight = 21.sp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Données : Météo-France via precog. Les estimations thermiques sont calculées dans l'app et doivent être confrontées au ciel.",
            style = Gc.type.bodySmall,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.ink, contentColor = c.background),
        ) { Text("J'ai compris", style = Gc.type.body.copy(color = c.background, fontWeight = FontWeight.Bold)) }
    }
}
