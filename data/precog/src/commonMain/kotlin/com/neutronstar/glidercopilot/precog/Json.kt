package com.neutronstar.glidercopilot.precog

/** Arbre JSON minimal, suffisant pour les réponses precog. */
sealed interface Json {
    data class Obj(val map: Map<String, Json>) : Json
    data class Arr(val items: List<Json>) : Json
    data class Str(val value: String) : Json
    data class Num(val value: Double) : Json
    data class Bool(val value: Boolean) : Json
    data object Null : Json

    operator fun get(key: String): Json? = (this as? Obj)?.map?.get(key)
    val obj: Obj? get() = this as? Obj
    val arr: List<Json> get() = (this as? Arr)?.items ?: emptyList()
    val str: String? get() = (this as? Str)?.value
    val num: Double? get() = (this as? Num)?.value ?: (this as? Str)?.value?.toDoubleOrNull()
    val int: Int? get() = num?.toInt()

    companion object {
        fun parse(text: String): Json = Parser(text).parseDocument()
    }
}

class JsonParseException(message: String) : RuntimeException(message)

private class Parser(private val s: String) {
    private var i = 0

    fun parseDocument(): Json {
        val v = value()
        ws()
        if (i != s.length) fail("contenu après la fin du document")
        return v
    }

    private fun fail(msg: String): Nothing = throw JsonParseException("$msg (position $i)")

    private fun ws() {
        while (i < s.length && s[i].isWhitespace()) i++
    }

    private fun peek(): Char {
        if (i >= s.length) fail("fin inattendue")
        return s[i]
    }

    private fun value(): Json {
        ws()
        return when (val c = peek()) {
            '{' -> obj()
            '[' -> arr()
            '"' -> Json.Str(string())
            't' -> literal("true", Json.Bool(true))
            'f' -> literal("false", Json.Bool(false))
            'n' -> literal("null", Json.Null)
            else -> if (c == '-' || c.isDigit()) number() else fail("caractère inattendu '$c'")
        }
    }

    private fun literal(word: String, v: Json): Json {
        if (!s.startsWith(word, i)) fail("littéral attendu $word")
        i += word.length
        return v
    }

    private fun obj(): Json {
        i++
        val m = LinkedHashMap<String, Json>()
        ws()
        if (peek() == '}') { i++; return Json.Obj(m) }
        while (true) {
            ws()
            if (peek() != '"') fail("clé attendue")
            val k = string()
            ws()
            if (peek() != ':') fail("':' attendu")
            i++
            m[k] = value()
            ws()
            when (peek()) {
                ',' -> i++
                '}' -> { i++; return Json.Obj(m) }
                else -> fail("',' ou '}' attendu")
            }
        }
    }

    private fun arr(): Json {
        i++
        val l = ArrayList<Json>()
        ws()
        if (peek() == ']') { i++; return Json.Arr(l) }
        while (true) {
            l += value()
            ws()
            when (peek()) {
                ',' -> i++
                ']' -> { i++; return Json.Arr(l) }
                else -> fail("',' ou ']' attendu")
            }
        }
    }

    private fun string(): String {
        i++
        val sb = StringBuilder()
        while (true) {
            if (i >= s.length) fail("chaîne non terminée")
            val c = s[i++]
            when (c) {
                '"' -> return sb.toString()
                '\\' -> {
                    val e = peek()
                    i++
                    when (e) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append(8.toChar())
                        'f' -> sb.append(12.toChar())
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (i + 4 > s.length) fail("échappement unicode tronqué")
                            sb.append(s.substring(i, i + 4).toInt(16).toChar())
                            i += 4
                        }
                        else -> fail("échappement inconnu")
                    }
                }
                else -> sb.append(c)
            }
        }
    }

    private fun number(): Json {
        val start = i
        while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '+' || s[i] == '-')) i++
        return Json.Num(s.substring(start, i).toDoubleOrNull() ?: fail("nombre invalide"))
    }
}
