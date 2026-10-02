package com.neutronstar.glidercopilot

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * S18.2 — « Continuer avec Google » via Credential Manager : la feuille système de Google s'ouvre, le pilote
 * choisit son compte, l'app reçoit un jeton d'identité (aucun mot de passe ne passe par GLIDY).
 * Le jeton est ensuite échangé contre une session Supabase (SupabaseClient.signInWithIdToken).
 */
internal object GoogleSignIn {
    /** Bouton Google affiché seulement si l'ID client Web est fourni au build (secret GOOGLE_WEB_CLIENT_ID). */
    val available: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.endsWith(".apps.googleusercontent.com")

    sealed interface Result {
        /** [rawNonce] : à transmettre à Supabase, qui le compare à l'empreinte envoyée à Google. */
        data class Token(val idToken: String, val rawNonce: String) : Result
        data object Cancelled : Result
        data class Error(val message: String) : Result
    }

    /** [context] : l'activité en cours (la feuille de choix du compte s'affiche par-dessus). */
    suspend fun request(context: Context): Result {
        if (!available) return Result.Error("Connexion Google non configurée")
        val rawNonce = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val hashedNonce = MessageDigest.getInstance("SHA-256").digest(rawNonce.toByteArray()).joinToString("") { "%02x".format(it) }
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).setNonce(hashedNonce).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(context).getCredential(context, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                Result.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken, rawNonce)
            } else {
                Result.Error("Réponse Google inattendue")
            }
        } catch (e: GetCredentialCancellationException) {
            Result.Cancelled
        } catch (e: NoCredentialException) {
            Result.Error("Aucun compte Google sur ce téléphone")
        } catch (e: GetCredentialException) {
            Result.Error("Google : ${e.message ?: e.type}")
        } catch (e: GoogleIdTokenParsingException) {
            Result.Error("Jeton Google illisible")
        }
    }
}
