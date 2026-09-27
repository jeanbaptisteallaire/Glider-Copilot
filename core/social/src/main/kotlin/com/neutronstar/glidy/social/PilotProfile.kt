package com.neutronstar.glidy.social

import java.text.Normalizer

/*
 * GLIDY — profil pilote (S16). Kotlin pur, sans dépendance : partagé par la page profil (Mes vols),
 * le futur fil (S17) et le raccordement Supabase (S18, table `profiles`).
 */

/** Niveau d'expérience déclaré par le pilote (demande JB). */
enum class ExperienceLevel(val label: String) {
    STUDENT("Élève"),
    LICENSED("Breveté"),
    PASSENGER("Autorisé emport passager"),
    INSTRUCTOR("Formateur"),
}

data class PilotProfile(
    /** Nom affiché, 1 à 40 caractères. Vide = profil pas encore créé. */
    val displayName: String = "",
    /** Pseudo unique (S18 : unicité vérifiée côté serveur), sans @, voir [Usernames]. */
    val username: String = "",
    val bio: String = "",
    /** Club de référence (nom libre ou choisi dans la liste FFVP de l'app). */
    val club: String = "",
    val experience: ExperienceLevel? = null,
    /** Heures de vol antérieures à GLIDY, déclarées par le pilote, en minutes. */
    val minutesBeforeApp: Long = 0,
) {
    val isEmpty: Boolean get() = displayName.isBlank() && username.isBlank()

    /** Initiales pour l'avatar par défaut (« Jean-Baptiste Allaire » → « JA »). */
    val initials: String
        get() {
            val words = displayName.trim().split(Regex("[\\s-]+")).filter(String::isNotBlank)
            val letters = when {
                words.size >= 2 -> "${words.first().first()}${words.last().first()}"
                words.size == 1 -> words.first().take(2)
                username.isNotBlank() -> username.take(2)
                else -> "?"
            }
            return letters.uppercase()
        }

    fun validate(): List<String> = buildList {
        if (displayName.isBlank()) add("Le nom affiché est obligatoire.")
        if (displayName.length > MAX_NAME) add("Le nom affiché fait au plus $MAX_NAME caractères.")
        Usernames.problem(username)?.let(::add)
        if (bio.length > MAX_BIO) add("La bio fait au plus $MAX_BIO caractères.")
        if (club.length > MAX_CLUB) add("Le club fait au plus $MAX_CLUB caractères.")
        if (minutesBeforeApp < 0 || minutesBeforeApp > MAX_MINUTES) add("Heures antérieures invalides.")
    }

    companion object {
        const val MAX_NAME = 40
        const val MAX_BIO = 160
        const val MAX_CLUB = 80
        const val MAX_MINUTES = 100_000L * 60
    }
}

/** Règles du pseudo : 3 à 20 caractères, minuscules sans accent, chiffres, « _ » et « . ». */
object Usernames {
    const val MIN = 3
    const val MAX = 20
    private val ALLOWED = Regex("[a-z0-9_.]+")

    /** Met en forme une saisie : minuscules, accents retirés, espaces → « _ », @ initial retiré. */
    fun normalize(input: String): String {
        val noAccent = Normalizer.normalize(input.trim().removePrefix("@"), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noAccent.lowercase().replace(Regex("\\s+"), "_").filter { it.isLetterOrDigit() || it == '_' || it == '.' }
    }

    /** Problème du pseudo, ou null s'il est valide. */
    fun problem(username: String): String? = when {
        username.isBlank() -> "Le pseudo est obligatoire."
        username.length < MIN -> "Le pseudo fait au moins $MIN caractères."
        username.length > MAX -> "Le pseudo fait au plus $MAX caractères."
        !ALLOWED.matches(username) -> "Le pseudo n'utilise que a-z, 0-9, « _ » et « . »."
        username.startsWith(".") || username.endsWith(".") || ".." in username -> "Le pseudo ne commence ni ne finit par un point."
        else -> null
    }

    /** Proposition de pseudo à partir du nom (« Jean-Baptiste Allaire » → « jean_baptiste.allaire » tronqué). */
    fun suggestFrom(displayName: String): String {
        val words = normalize(displayName.replace('-', ' ')).split('_').filter(String::isNotBlank)
        val base = when {
            words.size >= 2 -> words.dropLast(1).joinToString("_") + "." + words.last()
            words.size == 1 -> words.first()
            else -> "pilote"
        }
        return base.take(MAX).trimEnd('.').padEnd(MIN, '0')
    }
}

/** Stockage du profil. Implémentation locale dans l'app (S16), puis synchronisée avec Supabase (S18). */
interface ProfileStore {
    suspend fun load(): PilotProfile
    suspend fun save(profile: PilotProfile)
    /** Efface le profil de ce téléphone (les vols ne sont pas touchés). */
    suspend fun clear()
}

/** Total d'heures affiché sur le profil : heures antérieures déclarées + vols réels du carnet. */
fun totalFlightMinutes(profile: PilotProfile, carnetSeconds: Long): Long =
    profile.minutesBeforeApp.coerceAtLeast(0) + carnetSeconds.coerceAtLeast(0) / 60
