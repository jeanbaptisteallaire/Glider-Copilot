package com.neutronstar.glidercopilot.carto

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Flux HTTP brut (les fichiers de carte font des dizaines de Mo : pas de lecture en mémoire). */
fun interface StreamOpener {
    /** Ouvre [url] à partir de l'octet [offset] ; renvoie le flux et la longueur restante (-1 si inconnue). */
    @Throws(IOException::class)
    fun open(url: String, offset: Long): Pair<InputStream, Long>
}

/** Le serveur ne peut pas reprendre au point demandé : le fichier partiel doit être jeté. */
class RangeException(message: String) : IOException(message)

class UrlStreamOpener(private val userAgent: String) : StreamOpener {
    override fun open(url: String, offset: Long): Pair<InputStream, Long> {
        var target = URL(url)
        repeat(5) {
            val c = target.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 20_000
            c.readTimeout = 60_000
            c.setRequestProperty("User-Agent", userAgent)
            if (offset > 0) c.setRequestProperty("Range", "bytes=$offset-")
            when (val code = c.responseCode) {
                in 300..399 -> { target = URL(target, c.getHeaderField("Location")); c.disconnect() }
                200 -> {
                    if (offset > 0) { c.disconnect(); throw RangeException("reprise refusée par le serveur") }
                    return c.inputStream to c.contentLengthLong
                }
                206 -> return c.inputStream to c.contentLengthLong
                416 -> { c.disconnect(); throw RangeException("HTTP 416") }
                else -> { c.disconnect(); throw IOException("HTTP $code") }
            }
        }
        throw IOException("trop de redirections")
    }
}

/**
 * Installe un pack dans [root]/<id>/ : chaque fichier est téléchargé en « .part » (reprise possible), vérifié par SHA-256,
 * puis renommé. Le manifeste est écrit en dernier : un pack sans manifeste n'est jamais considéré installé.
 */
class PackDownloader(private val opener: StreamOpener) {

    fun interface Progress { fun onProgress(doneBytes: Long, totalBytes: Long) }

    @Throws(IOException::class)
    fun install(pack: PackInfo, manifestJson: String, root: File, progress: Progress = Progress { _, _ -> }, cancelled: () -> Boolean = { false }) {
        val dir = File(root, pack.id).apply { mkdirs() }
        val total = pack.totalBytes
        var before = 0L
        for (f in pack.files) {
            val dest = File(dir, f.name)
            if (dest.exists() && dest.length() == f.size && sha256(dest) == f.sha256) {
                before += f.size
                progress.onProgress(before, total)
                continue
            }
            val part = File(dir, f.name + ".part")
            var attempt = 0
            while (true) {
                try {
                    if (part.length() > f.size) part.delete()
                    if (part.length() < f.size) fetch(f, part, before, total, progress, cancelled)
                    if (sha256(part).equals(f.sha256, ignoreCase = true)) break
                    part.delete()
                    throw IOException("empreinte invalide pour ${f.name}")
                } catch (e: IOException) {
                    if (cancelled() || ++attempt >= 3) throw e
                    // reprise impossible (fichier remplacé sur le serveur, 416) : on repart de zéro
                    if (e is RangeException || e.message?.startsWith("empreinte") == true) part.delete()
                    Thread.sleep(2_000L * attempt)
                }
            }
            dest.delete()
            if (!part.renameTo(dest)) throw IOException("impossible d'installer ${f.name}")
            before += f.size
            progress.onProgress(before, total)
        }
        val tmp = File(dir, "manifest.json.tmp")
        tmp.writeText(manifestJson)
        val manifest = File(dir, "manifest.json")
        manifest.delete()
        if (!tmp.renameTo(manifest)) throw IOException("manifeste non écrit")
        // anciens fichiers d'une version précédente
        val keep = pack.files.map { it.name }.toSet() + "manifest.json"
        dir.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
    }

    private fun fetch(f: PackFile, part: File, before: Long, total: Long, progress: Progress, cancelled: () -> Boolean) {
        val offset = part.length()
        val (input, _) = opener.open(f.url, offset)
        input.use { ins ->
            java.io.FileOutputStream(part, offset > 0).use { out ->
                val buf = ByteArray(1 shl 16)
                var done = offset
                var lastReport = 0L
                while (true) {
                    if (cancelled()) throw IOException("téléchargement annulé")
                    val n = ins.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (done - lastReport > 512_000) { progress.onProgress(before + done, total); lastReport = done }
                }
            }
        }
        if (part.length() != f.size) throw IOException("fichier incomplet ${f.name} (${part.length()}/${f.size})")
    }

    companion object {
        fun sha256(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { ins ->
                val buf = ByteArray(1 shl 16)
                while (true) { val n = ins.read(buf); if (n < 0) break; md.update(buf, 0, n) }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        /** Manifeste d'un pack installé, ou null si le pack est absent ou incomplet. */
        fun installed(root: File, id: String): PackInfo? {
            val dir = File(root, id)
            val m = File(dir, "manifest.json").takeIf { it.exists() } ?: return null
            val info = runCatching { PackCatalog.parseManifest(m.readText()) }.getOrNull() ?: return null
            return info.takeIf { p -> p.files.all { File(dir, it.name).length() == it.size } }
        }
    }
}
