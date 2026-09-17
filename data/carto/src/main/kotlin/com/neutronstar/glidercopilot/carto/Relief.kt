package com.neutronstar.glidercopilot.carto

import com.neutronstar.glidercopilot.domain.LatLon
import com.neutronstar.glidercopilot.domain.Terrain
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Lecteur PMTiles v3 (lecture seule, fichier local) : en-tête, répertoires compressés gzip, répertoires feuilles,
 * identifiants de tuiles en courbe de Hilbert. Suffisant pour les packs GLIDY (tuiles non compressées).
 */
class PmTiles(private val file: File) : AutoCloseable {
    private val raf = RandomAccessFile(file, "r")
    val minZoom: Int
    val maxZoom: Int
    private val rootOffset: Long
    private val rootLength: Long
    private val leafOffset: Long
    private val dataOffset: Long
    private val internalCompression: Int
    private val tileCompression: Int
    private val root: List<Entry>
    private val leaves = object : LinkedHashMap<Long, List<Entry>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, List<Entry>>?) = size > 8
    }

    data class Entry(val tileId: Long, val offset: Long, val length: Int, val runLength: Int)

    init {
        val h = read(0, 127)
        require(String(h, 0, 7, Charsets.US_ASCII) == "PMTiles" && h[7].toInt() == 3) { "PMTiles v3 attendu : ${file.name}" }
        fun u64(at: Int): Long { var v = 0L; for (i in 7 downTo 0) v = (v shl 8) or (h[at + i].toLong() and 0xff); return v }
        rootOffset = u64(8); rootLength = u64(16)
        leafOffset = u64(40); dataOffset = u64(56)
        internalCompression = h[97].toInt()
        tileCompression = h[98].toInt()
        minZoom = h[100].toInt(); maxZoom = h[101].toInt()
        root = parseDirectory(decompress(read(rootOffset, rootLength.toInt()), internalCompression))
    }

    @Synchronized private fun read(offset: Long, length: Int): ByteArray {
        val b = ByteArray(length)
        raf.seek(offset); raf.readFully(b)
        return b
    }

    fun tile(z: Int, x: Int, y: Int): ByteArray? {
        if (z < minZoom || z > maxZoom) return null
        val id = tileId(z, x, y)
        var dir = root
        repeat(4) {
            val e = find(dir, id) ?: return null
            if (e.runLength > 0) return decompress(read(dataOffset + e.offset, e.length), tileCompression)
            dir = synchronized(leaves) { leaves[e.offset] } ?: parseDirectory(decompress(read(leafOffset + e.offset, e.length), internalCompression)).also { d -> synchronized(leaves) { leaves[e.offset] = d } }
        }
        return null
    }

    private fun find(dir: List<Entry>, id: Long): Entry? {
        var lo = 0; var hi = dir.size - 1
        while (lo <= hi) {
            val m = (lo + hi) ushr 1
            val c = dir[m].tileId.compareTo(id)
            if (c == 0) return dir[m]
            if (c < 0) lo = m + 1 else hi = m - 1
        }
        if (hi >= 0) {
            val e = dir[hi]
            if (e.runLength == 0) return e                         // répertoire feuille couvrant l'identifiant
            if (id - e.tileId < e.runLength) return e
        }
        return null
    }

    override fun close() = raf.close()

    companion object {
        fun tileId(z: Int, x: Int, y: Int): Long {
            var acc = 0L
            for (i in 0 until z) acc += 1L shl (2 * i)
            val n = 1L shl z
            var d = 0L
            var tx = x.toLong(); var ty = y.toLong()
            var s = n / 2
            while (s > 0) {
                val rx = if (tx and s > 0) 1L else 0L
                val ry = if (ty and s > 0) 1L else 0L
                d += s * s * ((3 * rx) xor ry)
                if (ry == 0L) {
                    if (rx == 1L) { tx = n - 1 - tx; ty = n - 1 - ty }
                    val t = tx; tx = ty; ty = t
                }
                s /= 2
            }
            return acc + d
        }

        private fun decompress(b: ByteArray, compression: Int): ByteArray = when (compression) {
            0, 1 -> b
            2 -> GZIPInputStream(b.inputStream()).use { it.readBytes() }
            else -> error("compression PMTiles $compression non prise en charge")
        }

        internal fun parseDirectory(b: ByteArray): List<Entry> {
            var pos = 0
            fun varint(): Long {
                var v = 0L; var shift = 0
                while (true) {
                    val c = b[pos++].toInt() and 0xff
                    v = v or ((c and 0x7f).toLong() shl shift)
                    if (c < 0x80) return v
                    shift += 7
                }
            }
            val n = varint().toInt()
            val ids = LongArray(n); var last = 0L
            for (i in 0 until n) { last += varint(); ids[i] = last }
            val runs = IntArray(n) { varint().toInt() }
            val lengths = IntArray(n) { varint().toInt() }
            val offsets = LongArray(n)
            for (i in 0 until n) {
                val o = varint()
                offsets[i] = if (o == 0L && i > 0) offsets[i - 1] + lengths[i - 1] else o - 1
            }
            return List(n) { Entry(ids[it], offsets[it], lengths[it], runs[it]) }
        }
    }
}

/** Décodeur PNG minimal (8 bits, non entrelacé : gris, RVB, palette, RVBA) → pixels RVB. */
object Png {
    class Image(val width: Int, val height: Int, val rgb: IntArray)

    fun decode(bytes: ByteArray): Image {
        require(bytes.size > 33 && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte()) { "PNG attendu" }
        var pos = 8
        var w = 0; var h = 0; var colorType = 0; var bitDepth = 0; var interlace = 0
        var palette = IntArray(0)
        val idat = ByteArrayOutputStream()
        fun u32(at: Int) = ((bytes[at].toInt() and 0xff) shl 24) or ((bytes[at + 1].toInt() and 0xff) shl 16) or ((bytes[at + 2].toInt() and 0xff) shl 8) or (bytes[at + 3].toInt() and 0xff)
        while (pos + 8 <= bytes.size) {
            val len = u32(pos)
            val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
            val data = pos + 8
            when (type) {
                "IHDR" -> { w = u32(data); h = u32(data + 4); bitDepth = bytes[data + 8].toInt(); colorType = bytes[data + 9].toInt(); interlace = bytes[data + 12].toInt() }
                "PLTE" -> palette = IntArray(len / 3) { i -> ((bytes[data + 3 * i].toInt() and 0xff) shl 16) or ((bytes[data + 3 * i + 1].toInt() and 0xff) shl 8) or (bytes[data + 3 * i + 2].toInt() and 0xff) }
                "IDAT" -> idat.write(bytes, data, len)
                "IEND" -> break
            }
            pos = data + len + 4
        }
        require(bitDepth == 8 && interlace == 0) { "PNG 8 bits non entrelacé attendu" }
        val bpp = when (colorType) { 0 -> 1; 2 -> 3; 3 -> 1; 4 -> 2; 6 -> 4; else -> error("type de couleur PNG $colorType") }
        val inflater = Inflater()
        inflater.setInput(idat.toByteArray())
        val stride = w * bpp
        val raw = ByteArray((stride + 1) * h)
        var got = 0
        while (got < raw.size && !inflater.finished()) {
            val r = inflater.inflate(raw, got, raw.size - got)
            if (r == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            got += r
        }
        inflater.end()
        val out = IntArray(w * h)
        var prev = ByteArray(stride)
        var line = ByteArray(stride)
        for (row in 0 until h) {
            val base = row * (stride + 1)
            val filter = raw[base].toInt()
            System.arraycopy(raw, base + 1, line, 0, stride)
            for (i in 0 until stride) {
                val a = if (i >= bpp) line[i - bpp].toInt() and 0xff else 0
                val b = prev[i].toInt() and 0xff
                val c = if (i >= bpp) prev[i - bpp].toInt() and 0xff else 0
                val x = line[i].toInt() and 0xff
                line[i] = when (filter) {
                    0 -> x
                    1 -> x + a
                    2 -> x + b
                    3 -> x + (a + b) / 2
                    4 -> {
                        val p = a + b - c
                        val pa = abs(p - a); val pb = abs(p - b); val pc = abs(p - c)
                        x + if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
                    }
                    else -> x
                }.toByte()
            }
            for (px in 0 until w) {
                val i = px * bpp
                fun u(k: Int) = line[i + k].toInt() and 0xff
                out[row * w + px] = when (colorType) {
                    2, 6 -> (u(0) shl 16) or (u(1) shl 8) or u(2)
                    3 -> palette.getOrElse(u(0)) { 0 }
                    else -> (u(0) shl 16) or (u(0) shl 8) or u(0)
                }
            }
            val t = prev; prev = line; line = t
        }
        return Image(w, h, out)
    }
}

/**
 * Relief du pack hors ligne (Copernicus GLO-30 encodé Terrarium) : altitude interpolée au niveau de zoom le plus fin.
 * Garde en mémoire les dernières tuiles décodées (512 × 512 → ~1 Mo chacune).
 */
class TerrariumTerrain(private val tiles: PmTiles, private val cacheSize: Int = 9) : Terrain {
    private val zoom = tiles.maxZoom
    private val cache = object : LinkedHashMap<Long, FloatArray?>(cacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, FloatArray?>?) = size > cacheSize
    }
    private var tileSize = 512

    override fun elevationM(p: LatLon): Double? {
        if (abs(p.lat) > 85) return null
        val n = (1 shl zoom).toDouble()
        val fx = (p.lon + 180.0) / 360.0 * n
        val latR = Math.toRadians(p.lat)
        val fy = (1.0 - ln(tan(latR) + 1.0 / kotlin.math.cos(latR)) / PI) / 2.0 * n
        val gx = fx * tileSize - 0.5
        val gy = fy * tileSize - 0.5
        val x0 = floor(gx).toLong(); val y0 = floor(gy).toLong()
        val dx = gx - x0; val dy = gy - y0
        val h00 = sample(x0, y0) ?: return null
        val h10 = sample(x0 + 1, y0) ?: h00
        val h01 = sample(x0, y0 + 1) ?: h00
        val h11 = sample(x0 + 1, y0 + 1) ?: h00
        return (h00 * (1 - dx) + h10 * dx) * (1 - dy) + (h01 * (1 - dx) + h11 * dx) * dy
    }

    private fun sample(px: Long, py: Long): Double? {
        val tx = Math.floorDiv(px, tileSize.toLong()).toInt()
        val ty = Math.floorDiv(py, tileSize.toLong()).toInt()
        val grid = tile(tx, ty) ?: return null
        val ix = Math.floorMod(px, tileSize.toLong()).toInt()
        val iy = Math.floorMod(py, tileSize.toLong()).toInt()
        return grid[iy * tileSize + ix].toDouble()
    }

    @Synchronized private fun tile(x: Int, y: Int): FloatArray? {
        val key = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)
        if (cache.containsKey(key)) return cache[key]
        val grid = tiles.tile(zoom, x, y)?.let { bytes ->
            val img = Png.decode(bytes)
            tileSize = img.width
            FloatArray(img.rgb.size) { i ->
                val c = img.rgb[i]
                (((c shr 16) and 0xff) * 256 + ((c shr 8) and 0xff) + (c and 0xff) / 256f) - 32768f
            }
        }
        cache[key] = grid
        return grid
    }
}
