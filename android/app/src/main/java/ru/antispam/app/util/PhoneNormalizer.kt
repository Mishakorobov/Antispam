package ru.antispam.app.util

object PhoneNormalizer {

    /**
     * Normalizes any input phone number string into an 11-digit Russian Long (7XXXXXXXXXX).
     * Handles +7, 8, spaces, parentheses, hyphens, and leading 10-digit formats.
     */
    fun normalize(rawNumber: String?): Long? {
        if (rawNumber.isNullOrBlank()) return null

        val digits = buildString {
            for (ch in rawNumber) {
                if (ch.isDigit()) append(ch)
            }
        }

        val normalizedStr = when {
            digits.length == 11 && digits.startsWith("8") -> "7" + digits.substring(1)
            digits.length == 11 && digits.startsWith("7") -> digits
            digits.length == 10 -> "7$digits"
            else -> null
        }

        return normalizedStr?.toLongOrNull()
    }

    /**
     * Checks if the 11-digit +7 number belongs to the foreign +7 segment (Kazakhstan 6xx, 7xx).
     * In the shared +7 world numbering zone, Russia uses 300..599 and 800..999.
     * Codes in 600..799 belong to Kazakhstan.
     */
    fun isKazakhstanOrForeignPlus7(number: Long): Boolean {
        val s = number.toString()
        if (s.length == 11 && s.startsWith("7")) {
            val code = s.substring(1, 4).toIntOrNull() ?: return false
            if (code in 600..799) return true

            // Check dynamic spoofing prefixes delivered via ASPB v2 (e.g. "76", "77", "7940")
            val dynamicPrefixes = ru.antispam.app.security.DynamicConfigManager.getDynamicSpoofingPrefixes()
            if (dynamicPrefixes.any { prefix -> s.startsWith(prefix) || s.substring(1).startsWith(prefix) }) {
                return true
            }
        }
        return false
    }

    /**
     * Formats any raw input phone string into human-readable format.
     */
    fun formatPretty(rawNumber: String?): String {
        val norm = normalize(rawNumber) ?: return rawNumber ?: ""
        return formatRussian(norm)
    }

    /**
     * Formats an 11-digit number into human-readable string:
     * - +7 (903) 123-45-67 for Russian numbers
     * - +7 (701) 123-45-67 (Казахстан) for foreign +7 segment
     */
    fun formatRussian(number: Long): String {
        val s = number.toString()
        if (s.length == 11 && s.startsWith("7")) {
            val formatted = "+7 (${s.substring(1, 4)}) ${s.substring(4, 7)}-${s.substring(7, 9)}-${s.substring(9, 11)}"
            return if (isKazakhstanOrForeignPlus7(number)) "$formatted (Казахстан)" else formatted
        }
        return "+$s"
    }
}
