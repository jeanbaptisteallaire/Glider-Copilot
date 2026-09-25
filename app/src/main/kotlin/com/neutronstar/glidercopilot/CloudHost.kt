package com.neutronstar.glidercopilot

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.neutronstar.glidy.flightarchive.SyncState
import com.neutronstar.glidy.flightcloud.CloudConfig
import com.neutronstar.glidy.flightcloud.CloudResult
import com.neutronstar.glidy.flightcloud.CloudSession
import com.neutronstar.glidy.flightcloud.FlightSyncService
import com.neutronstar.glidy.flightcloud.SessionStore
import com.neutronstar.glidy.flightcloud.SupabaseClient
import com.neutronstar.glidy.flightcloud.UrlConnectionTransport
import com.neutronstar.glidy.myflights.AccountActions
import com.neutronstar.glidy.myflights.AccountCardState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * S12 — compte GLIDY optionnel + sauvegarde des vols sur Supabase.
 * Inactif tant que BuildConfig.SUPABASE_URL / SUPABASE_ANON_KEY sont vides (secrets GitHub, voir
 * docs/supabase/SETUP.md) : la carte « Compte » affiche alors « bientôt disponible » et rien ne part sur le réseau.
 * Aucune synchronisation pendant un vol enregistré ([recording]).
 */
class CloudHost(context: Context, private val flights: FlightArchiveHost, private val recording: () -> Boolean) : AccountActions {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val config = CloudConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
    private val client = SupabaseClient(config, UrlConnectionTransport(), PrefsSessionStore(context.applicationContext))
    private val sync = FlightSyncService(client, flights.repository) { call -> withContext(Dispatchers.IO) { call() } }

    private val _state = MutableStateFlow(AccountCardState(configured = config.isConfigured, email = client.session?.email))
    val state: StateFlow<AccountCardState> = _state.asStateFlow()

    /** Incrémenté quand le carnet local a changé (vols restaurés) : l'écran Mes vols se relit. */
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    /** Appelé à chaque affichage de l'onglet Mes vols : compteurs à jour, sauvegarde si connecté et au sol. */
    fun onShown() {
        refreshCounts()
        if (client.session != null && !recording()) syncNow()
    }

    override fun sendCode(email: String) = run("Envoi du code…") {
        when (val r = withContext(Dispatchers.IO) { client.sendCode(email) }) {
            is CloudResult.Ok -> _state.update { it.copy(codeSentTo = email.trim(), message = null) }
            else -> _state.update { it.copy(message = r.userMessage()) }
        }
    }

    override fun verifyCode(code: String) = run("Connexion…") {
        val email = _state.value.codeSentTo ?: return@run
        when (val r = withContext(Dispatchers.IO) { client.verifyCode(email, code) }) {
            is CloudResult.Ok -> {
                _state.update { it.copy(email = r.value.email, codeSentTo = null, message = null) }
                doSync()
            }
            else -> _state.update { it.copy(message = r.userMessage()) }
        }
    }

    override fun cancelCode() = _state.update { it.copy(codeSentTo = null, message = null) }

    override fun syncNow() = run("Sauvegarde en cours…") { doSync() }

    override fun signOut() = run("Déconnexion…") {
        withContext(Dispatchers.IO) { client.signOut() }
        _state.update { it.copy(email = null, message = "Déconnecté : vos vols restent sur ce téléphone.") }
    }

    override fun deleteAccount() = run("Suppression du compte…") {
        when (val r = withContext(Dispatchers.IO) { client.deleteAccount() }) {
            is CloudResult.Ok -> {
                flights.repository.listFlights().forEach { flights.repository.updateSyncState(it.id, SyncState.LOCAL_ONLY, null) }
                _state.update { it.copy(email = null, message = "Compte et vols en ligne supprimés. Vos vols restent sur ce téléphone.") }
                refreshCounts()
            }
            else -> _state.update { it.copy(message = r.userMessage()) }
        }
    }

    private suspend fun doSync() {
        if (recording()) { _state.update { it.copy(canSync = false, message = "Envoi suspendu pendant le vol") }; return }
        val report = runCatching { sync.sync() }.onFailure { Log.w(TAG, "sauvegarde impossible", it) }.getOrNull()
        if (report != null && report.restored > 0) _changes.update { it + 1 }
        if (client.session == null) _state.update { it.copy(email = null) }
        _state.update { it.copy(message = report?.message ?: "Sauvegarde impossible (réseau ?)") }
        refreshCounts()
    }

    private fun refreshCounts() {
        scope.launch {
            val all = runCatching { flights.repository.listFlights() }.getOrDefault(emptyList())
                .filterNot { it.file.fileName.startsWith("exemple-", ignoreCase = true) }
            _state.update {
                it.copy(
                    totalCount = all.size,
                    syncedCount = all.count { f -> f.syncState == SyncState.SYNCED },
                    canSync = !recording(),
                    email = client.session?.email,
                )
            }
        }
    }

    private fun run(busyMessage: String, block: suspend () -> Unit) {
        if (!config.isConfigured || _state.value.busy) return
        scope.launch {
            _state.update { it.copy(busy = true, message = busyMessage) }
            try { block() } finally { _state.update { it.copy(busy = false) } }
        }
    }

    private fun CloudResult<*>.userMessage(): String = when (this) {
        is CloudResult.Failed -> "Échec : $reason"
        CloudResult.SignedOut -> "Session expirée : reconnectez-vous"
        CloudResult.NotConfigured -> "Sauvegarde cloud non configurée"
        is CloudResult.Ok -> ""
    }

    private class PrefsSessionStore(context: Context) : SessionStore {
        private val prefs = context.getSharedPreferences("glidy_cloud_session", Context.MODE_PRIVATE)
        override fun load(): CloudSession? {
            val access = prefs.getString("access", null) ?: return null
            return CloudSession(
                accessToken = access,
                refreshToken = prefs.getString("refresh", "").orEmpty(),
                userId = prefs.getString("user", "").orEmpty(),
                email = prefs.getString("email", "").orEmpty(),
                expiresAtEpochSeconds = prefs.getLong("expires", 0L),
            )
        }
        override fun save(session: CloudSession?) = prefs.edit {
            if (session == null) clear()
            else putString("access", session.accessToken).putString("refresh", session.refreshToken)
                .putString("user", session.userId).putString("email", session.email).putLong("expires", session.expiresAtEpochSeconds)
        }
    }

    companion object { private const val TAG = "GlidyCloud" }
}
