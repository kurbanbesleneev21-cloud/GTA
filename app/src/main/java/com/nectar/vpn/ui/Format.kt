package com.nectar.vpn.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {
    private val COUNTRIES = mapOf(
        "итал" to "IT", "франц" to "FR", "норвег" to "NO", "финлян" to "FI", "швец" to "SE",
        "герман" to "DE", "нидерл" to "NL", "голланд" to "NL", "польш" to "PL", "латв" to "LV",
        "литв" to "LT", "эстон" to "EE", "казах" to "KZ", "турц" to "TR", "сша" to "US", "америк" to "US",
        "великобр" to "GB", "англ" to "GB", "британ" to "GB", "япон" to "JP", "сингап" to "SG", "швейц" to "CH",
        "австри" to "AT", "испан" to "ES", "португ" to "PT", "чех" to "CZ", "румын" to "RO", "болгар" to "BG",
        "венгр" to "HU", "сербия" to "RS", "молдов" to "MD", "украин" to "UA", "росси" to "RU", "армен" to "AM",
        "грузи" to "GE", "азербайдж" to "AZ", "канад" to "CA", "гонконг" to "HK", "корея" to "KR", "индия" to "IN",
        "оаэ" to "AE", "эмират" to "AE", "израил" to "IL", "дания" to "DK", "ирланд" to "IE", "бельги" to "BE",
        "люксемб" to "LU", "исланд" to "IS", "греци" to "GR", "кипр" to "CY", "бразил" to "BR", "австрал" to "AU",
        "europe" to "EU", "европ" to "EU",
        "italy" to "IT", "france" to "FR", "norway" to "NO", "finland" to "FI", "sweden" to "SE",
        "germany" to "DE", "netherlands" to "NL", "poland" to "PL", "usa" to "US", "united states" to "US",
        "united kingdom" to "GB", "japan" to "JP", "singapore" to "SG", "turkey" to "TR", "kazakhstan" to "KZ",
    )

    /** Extracts a two-letter country code from flag emoji (🇮🇹 -> IT) or the server name. */
    fun monogram(name: String): String {
        val cps = name.codePoints().toArray()
        for (i in 0 until cps.size - 1) {
            val a = cps[i]; val b = cps[i + 1]
            if (a in 0x1F1E6..0x1F1FF && b in 0x1F1E6..0x1F1FF) {
                return "${('A' + (a - 0x1F1E6))}${('A' + (b - 0x1F1E6))}"
            }
        }
        val lower = name.lowercase()
        if (lower.contains("авто") || lower.contains("auto") || lower.contains("баланс")) return "A"
        COUNTRIES.entries.firstOrNull { lower.contains(it.key) }?.let { return it.value }
        val letters = cleanName(name).filter { it.isLetterOrDigit() }
        return letters.take(2).uppercase().ifEmpty { "•" }
    }

    /**
     * Flag emoji for a server: taken from the name (🇮🇹 Италия) or built from a recognised
     * country name. Returns null when the country is unknown.
     */
    fun flag(name: String): String? {
        val cps = name.codePoints().toArray()
        for (i in 0 until cps.size - 1) {
            if (cps[i] in 0x1F1E6..0x1F1FF && cps[i + 1] in 0x1F1E6..0x1F1FF) {
                return String(Character.toChars(cps[i])) + String(Character.toChars(cps[i + 1]))
            }
        }
        val lower = name.lowercase()
        val code = COUNTRIES.entries.firstOrNull { lower.contains(it.key) }?.value ?: return null
        return code.uppercase().map { c -> String(Character.toChars(0x1F1E6 + (c - 'A'))) }.joinToString("")
    }

    fun isAuto(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("авто") || lower.contains("auto") || lower.contains("баланс") || lower.contains("best")
    }

    /** Removes emoji (flags, pictographs) from a server name. */
    fun cleanName(name: String): String {
        val sb = StringBuilder()
        name.codePoints().forEach { cp ->
            val isEmoji = cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp == 0xFE0F || cp == 0x200D || cp in 0xE0020..0xE007F
            if (!isEmoji) sb.appendCodePoint(cp)
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim().ifEmpty { name.trim() }
    }

    fun date(ms: Long): String = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(ms))
    fun dateTime(ms: Long): String = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(ms))

    fun bytes(b: Long): String {
        if (b <= 0) return "0 Б"
        val units = listOf("Б", "КБ", "МБ", "ГБ", "ТБ")
        var v = b.toDouble(); var i = 0
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        return if (v >= 100 || i == 0) "${v.toLong()} ${units[i]}" else String.format(Locale.US, "%.1f %s", v, units[i])
    }

    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    fun hoursLabel(h: Int): String {
        val m10 = h % 10; val m100 = h % 100
        val word = when {
            m10 == 1 && m100 != 11 -> "час"
            m10 in 2..4 && m100 !in 12..14 -> "часа"
            else -> "часов"
        }
        return "$h $word"
    }
}
