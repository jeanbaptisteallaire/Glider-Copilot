package com.neutronstar.glidercopilot.shared

import com.neutronstar.glidy.flightcloud.CloudConfig
import com.neutronstar.glidy.flightcloud.CloudResult
import com.neutronstar.glidy.flightcloud.FlightSyncService
import com.neutronstar.glidy.flightcloud.KtorCloudTransport
import com.neutronstar.glidy.flightcloud.SessionStore
import com.neutronstar.glidy.flightcloud.SupabaseClient
import com.neutronstar.glidy.flightcloud.userMessage
import kotlinx.coroutines.runBlocking

/**
 * Compte Wind Glider sur iOS : même client Supabase qu'Android (SupabaseClient, data:flightcloud), même projet.
 * La session est rangée par Swift dans le Keychain ([store]). Appels bloquants (réseau) : à lancer hors du fil
 * principal. Aucune exception ne remonte vers Swift : chaque action rend null (succès) ou le message à afficher.
 */
class IosAccount(url: String, anonKey: String, store: SessionStore) {
    private val config = CloudConfig(url, anonKey)
    private val client = SupabaseClient(config, KtorCloudTransport(), store)

    val configured: Boolean get() = config.isConfigured

    /** Adresse du compte connecté, null en invité. */
    fun email(): String? = runCatching { client.session?.email }.getOrNull()

    /** Identifiant Supabase du pilote connecté (clé du tutoriel par profil, comme Android). */
    fun userId(): String? = runCatching { client.session?.userId }.getOrNull()

    /** Google ou Apple : [provider] = "google" | "apple", [rawNonce] dont l'empreinte SHA-256 a été donnée au fournisseur. */
    fun signInWithIdToken(provider: String, idToken: String, rawNonce: String?): String? =
        message { client.signInWithIdToken(provider, idToken, rawNonce) }

    fun sendCode(email: String): String? = message { client.sendCode(email) }
    fun verifyCode(email: String, code: String): String? = message { client.verifyCode(email, code) }
    fun refresh(): String? = message { client.freshSession() }
    fun sendFeedback(text: String, lang: String, appVersion: String): String? = message { client.sendFeedback(text, lang, appVersion) }

    /**
     * Sauvegarde du carnet [archive] et restauration des vols déjà en ligne (FlightSyncService, comme Android).
     * Rend le message de bilan. Jamais pendant un vol enregistré : c'est à l'appelant de vérifier.
     */
    fun sync(archive: IosFlightArchive): String = runCatching {
        runBlocking { FlightSyncService(client, archive).sync().message ?: "Carnet à jour en ligne" }
    }.getOrElse { "Échec de la sauvegarde (réseau ?)" }

    /** Suppression du compte et des vols en ligne ; les vols de l'iPhone restent, marqués « local seulement ». */
    fun deleteAccount(archive: IosFlightArchive): String? = message { client.deleteAccount() }.also { if (it == null) archive.markAllLocal() }

    fun signOut() { runCatching { client.signOut() } }

    private fun message(call: () -> CloudResult<*>): String? = runCatching {
        val r = call()
        if (r is CloudResult.Ok) null else r.userMessage()
    }.getOrElse { "Échec : ${it.message ?: "réseau indisponible"}" }
}
