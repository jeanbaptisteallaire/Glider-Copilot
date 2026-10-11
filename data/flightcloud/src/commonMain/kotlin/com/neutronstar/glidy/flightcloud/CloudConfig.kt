package com.neutronstar.glidy.flightcloud

/**
 * Projet Supabase de GLIDY (S12). Les deux valeurs viennent des secrets de build (jamais du dépôt public) ;
 * tant qu'elles sont vides, toute la partie cloud reste inactive et l'app fonctionne 100 % en local.
 * [anonKey] est la clé publique « anon » : conçue pour être embarquée dans une app, la sécurité repose
 * sur les règles RLS (docs/supabase/schema.sql). La clé service_role n'est jamais utilisée ici.
 */
data class CloudConfig(val url: String, val anonKey: String) {
    val isConfigured: Boolean get() = url.startsWith("https://") && anonKey.isNotBlank()
    val base: String get() = url.trimEnd('/')

    companion object {
        val DISABLED = CloudConfig("", "")
    }
}

/** Session du pilote connecté (jetons Supabase Auth). */
data class CloudSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String,
    val expiresAtEpochSeconds: Long,
)

/** Stockage de la session (préférences privées de l'app côté Android, mémoire dans les tests). */
interface SessionStore {
    fun load(): CloudSession?
    fun save(session: CloudSession?)
}

class MemorySessionStore(private var session: CloudSession? = null) : SessionStore {
    override fun load(): CloudSession? = session
    override fun save(session: CloudSession?) { this.session = session }
}
