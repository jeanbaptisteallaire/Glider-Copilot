package com.neutronstar.glidy.flightcloud

/**
 * Lecture/écriture JSON minimales pour les réponses Supabase (module JVM sans dépendance tierce).
 * Objets → Map<String, Any?>, tableaux → List<Any?>, nombres → Double, true/false/null.
 */
internal object MiniJson {
    fun parse(text: String): Any? = Reader(text).run { val v = value(); skipWs(); v }

    fun write(value: Any?): String = buildString { writeTo(this, value) }

    private fun writeTo(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> quote(sb, v)
            is Boolean -> sb.append(v)
            is Int, is Long -> sb.append(v)
            is Number -> { val d = v.toDouble(); if (d.isFinite()) sb.append(d) else sb.append("null") }
            is Map<*, *> -> {
                sb.append('{')
                v.entries.forEachIndexed { i, (k, value) -> if (i > 0) sb.append(','); quote(sb, k.toString()); sb.append(':'); writeTo(sb, value) }
                sb.append('}')
            }
            is Iterable<*> -> { sb.append('['); v.forEachIndexed { i, x -> if (i > 0) sb.append(','); writeTo(sb, x) }; sb.append(']') }
            else -> quote(sb, v.toString())
        }
    }

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (ch in s) when {
            ch == '"' -> sb.append("\\\"")
            ch == '\\' -> sb.append("\\\\")
            ch == '\n' -> sb.append("\\n")
            ch == '\r' -> sb.append("\\r")
            ch == '\t' -> sb.append("\\t")
            ch < ' ' -> sb.append("\\u%04x".format(ch.code))
            else -> sb.append(ch)
        }
        sb.append('"')
    }

    private class Reader(val t: String) {
        var i = 0
        fun skipWs() { while (i < t.length && t[i].isWhitespace()) i++ }
        fun value(): Any? {
            skipWs()
            require(i < t.length) { "JSON tronqué" }
            return when (t[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> { expect("true"); true }
                'f' -> { expect("false"); false }
                'n' -> { expect("null"); null }
                else -> num()
            }
        }
        fun expect(word: String) { require(t.startsWith(word, i)) { "JSON invalide à $i" }; i += word.length }
        fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>(); i++; skipWs()
            if (t[i] == '}') { i++; return m }
            while (true) {
                skipWs(); val k = str(); skipWs(); require(t[i] == ':'); i++
                m[k] = value(); skipWs()
                when (t[i]) { ',' -> i++; '}' -> { i++; return m }; else -> error("JSON invalide à $i") }
            }
        }
        fun arr(): List<Any?> {
            val l = ArrayList<Any?>(); i++; skipWs()
            if (t[i] == ']') { i++; return l }
            while (true) {
                l += value(); skipWs()
                when (t[i]) { ',' -> i++; ']' -> { i++; return l }; else -> error("JSON invalide à $i") }
            }
        }
        fun str(): String {
            require(t[i] == '"'); i++
            val sb = StringBuilder()
            while (t[i] != '"') {
                val ch = t[i]
                if (ch == '\\') {
                    val e = t[i + 1]
                    when (e) {
                        'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r'); 'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                        'u' -> { sb.append(t.substring(i + 2, i + 6).toInt(16).toChar()); i += 4 }
                        else -> sb.append(e)
                    }
                    i += 2
                } else { sb.append(ch); i++ }
            }
            i++
            return sb.toString()
        }
        fun num(): Double {
            val s0 = i
            while (i < t.length && (t[i].isDigit() || t[i] in "+-.eE")) i++
            return t.substring(s0, i).toDouble()
        }
    }
}

@Suppress("UNCHECKED_CAST")
internal fun Any?.obj(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()

internal fun Any?.list(): List<Any?> = this as? List<Any?> ?: emptyList()
