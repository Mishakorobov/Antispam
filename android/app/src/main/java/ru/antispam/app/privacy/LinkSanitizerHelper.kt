package ru.antispam.app.privacy

import java.net.IDN

/**
 * Result of inspecting and sanitizing text/URL for tracking parameters and phishing.
 */
data class SanitizedLinkResult(
    val originalText: String,
    val sanitizedText: String,
    val wasSanitized: Boolean,
    val removedParams: List<String> = emptyList(),
    val isPhishingThreat: Boolean = false,
    val phishingReason: String? = null
)

/**
 * LinkSanitizerHelper:
 * 1. Strips surveillance, advertising, and marketing tracking parameters from copied web URLs
 *    (e.g. utm_source, yclid, gclid, vk_ref, igshid, fbclid, _openstat).
 * 2. Detects Cyrillic-Latin homoglyph attacks and typosquatted phishing domains targeting
 *    Russian public services and financial institutions (Gosuslugi, Sberbank, T-Bank, VTB, Avito, CDEK).
 */
object LinkSanitizerHelper {

    /**
     * Standard tracking and surveillance query parameter keys across Russian and global ad ecosystems.
     */
    val TRACKING_PARAMETERS = setOf(
        "utm_source",
        "utm_medium",
        "utm_campaign",
        "utm_term",
        "utm_content",
        "yclid",
        "ym_campaign",
        "ym_source",
        "ym_debug",
        "_openstat",
        "gclid",
        "gclsrc",
        "dclid",
        "wbraid",
        "gbraid",
        "vk_ref",
        "vk_ad_id",
        "fbclid",
        "igshid",
        "mc_cid",
        "mc_eid",
        "ref_src",
        "tracking_id",
        "from",
        "si"
    )

    // Regex matching URLs including non-ASCII unicode (Cyrillic, IDN, homoglyphs)
    private val URL_REGEX = Regex("https?://[^\\s\"'<>]+", RegexOption.IGNORE_CASE)

    /**
     * Inspects text for web links, strips surveillance/tracking parameters,
     * and performs homoglyph/phishing verification.
     */
    fun sanitizeText(rawText: String?): SanitizedLinkResult {
        if (rawText.isNullOrBlank()) {
            return SanitizedLinkResult(
                originalText = rawText ?: "",
                sanitizedText = rawText ?: "",
                wasSanitized = false
            )
        }

        val nonNullRaw = rawText ?: ""
        val rawMatches = URL_REGEX.findAll(nonNullRaw).toList()
        if (rawMatches.isEmpty()) {
            return SanitizedLinkResult(
                originalText = nonNullRaw,
                sanitizedText = nonNullRaw,
                wasSanitized = false
            )
        }

        var resultText = nonNullRaw
        val allRemovedParams = mutableListOf<String>()
        var hasAnySanitization = false
        var isPhishingThreat = false
        var phishingReason: String? = null

        for (match in rawMatches) {
            // Trim trailing punctuation marks that often trail a URL in chat prose
            var urlStr = match.value
            while (urlStr.isNotEmpty() && urlStr.last() in charArrayOf('.', ',', ';', '!', ')', ']', '>')) {
                urlStr = urlStr.dropLast(1)
            }
            if (urlStr.isEmpty()) continue

            val singleResult = sanitizeSingleUrl(urlStr)

            if (singleResult.wasSanitized) {
                resultText = resultText.replace(urlStr, singleResult.sanitizedUrl)
                allRemovedParams.addAll(singleResult.removedParams)
                hasAnySanitization = true
            }

            if (singleResult.isPhishingThreat) {
                isPhishingThreat = true
                phishingReason = singleResult.phishingReason
            }
        }

        return SanitizedLinkResult(
            originalText = nonNullRaw,
            sanitizedText = resultText,
            wasSanitized = hasAnySanitization,
            removedParams = allRemovedParams.distinct(),
            isPhishingThreat = isPhishingThreat,
            phishingReason = phishingReason
        )
    }

    data class SingleUrlResult(
        val originalUrl: String,
        val sanitizedUrl: String,
        val wasSanitized: Boolean,
        val removedParams: List<String>,
        val isPhishingThreat: Boolean,
        val phishingReason: String?
    )

    /**
     * Strips tracking parameters from a single URL while preserving valid query params and fragments.
     */
    fun sanitizeSingleUrl(urlStr: String): SingleUrlResult {
        val (phishing, reason) = checkPhishingAndHomoglyphs(urlStr)

        val queryIndex = urlStr.indexOf('?')
        if (queryIndex == -1) {
            return SingleUrlResult(
                originalUrl = urlStr,
                sanitizedUrl = urlStr,
                wasSanitized = false,
                removedParams = emptyList(),
                isPhishingThreat = phishing,
                phishingReason = reason
            )
        }

        val fragmentIndex = urlStr.indexOf('#')
        val beforeQuery = urlStr.substring(0, queryIndex)
        val queryPart = if (fragmentIndex != -1 && fragmentIndex > queryIndex) {
            urlStr.substring(queryIndex + 1, fragmentIndex)
        } else if (fragmentIndex == -1) {
            urlStr.substring(queryIndex + 1)
        } else {
            ""
        }
        val fragmentPart = if (fragmentIndex != -1 && fragmentIndex > queryIndex) {
            urlStr.substring(fragmentIndex)
        } else {
            ""
        }

        val pairs = queryPart.split("&").filter { it.isNotBlank() }
        val keptPairs = mutableListOf<String>()
        val removedParams = mutableListOf<String>()

        for (pair in pairs) {
            val key = pair.substringBefore("=").trim()
            val lowerKey = key.lowercase()

            if (TRACKING_PARAMETERS.contains(lowerKey) ||
                lowerKey.startsWith("utm_") ||
                lowerKey.startsWith("yclid") ||
                lowerKey.startsWith("fbclid") ||
                lowerKey.startsWith("gclid")
            ) {
                removedParams.add(key)
            } else {
                keptPairs.add(pair)
            }
        }

        if (removedParams.isEmpty()) {
            return SingleUrlResult(
                originalUrl = urlStr,
                sanitizedUrl = urlStr,
                wasSanitized = false,
                removedParams = emptyList(),
                isPhishingThreat = phishing,
                phishingReason = reason
            )
        }

        val newQuery = if (keptPairs.isNotEmpty()) "?${keptPairs.joinToString("&")}" else ""
        val newUrl = "$beforeQuery$newQuery$fragmentPart"

        return SingleUrlResult(
            originalUrl = urlStr,
            sanitizedUrl = newUrl,
            wasSanitized = true,
            removedParams = removedParams,
            isPhishingThreat = phishing,
            phishingReason = reason
        )
    }

    /**
     * Extracts host safely without throwing URISyntaxException for non-ASCII/homoglyph URLs.
     */
    fun extractHost(urlStr: String): String {
        val withoutScheme = urlStr.replaceFirst(Regex("^https?://", RegexOption.IGNORE_CASE), "")
        val hostPart = withoutScheme.split('/', ':', '?', '#')[0].trim().lowercase()
        return try {
            IDN.toUnicode(hostPart)
        } catch (_: Exception) {
            hostPart
        }
    }

    /**
     * Detects homoglyph attacks (mixed Cyrillic/Latin alphabets) and brand typosquatting.
     */
    fun checkPhishingAndHomoglyphs(urlStr: String): Pair<Boolean, String?> {
        val host = extractHost(urlStr)
        if (host.isEmpty()) return Pair(false, null)

        // 1. Homoglyphs (mixed script confusable attack)
        val hasLatin = host.any { it in 'a'..'z' }
        val hasCyrillic = host.any { it in '\u0400'..'\u04FF' }
        if (hasLatin && hasCyrillic) {
            return Pair(true, "Обнаружена подмена символов (гомоглифы): смесь латиницы и кириллицы в домене $host")
        }

        // 2. Gosuslugi brand spoofing
        if (host.contains("g0suslugi") ||
            (host.contains("gosuslug") && !host.endsWith("gosuslugi.ru")) ||
            (host.contains("gos-uslugi") && !host.endsWith("gosuslugi.ru"))
        ) {
            return Pair(true, "Поддельный домен Госуслуг ($host)")
        }

        // 3. Sberbank spoofing
        if ((host.contains("sber-") || host.contains("sberbank-")) &&
            !host.endsWith("sberbank.ru") && !host.endsWith("sber.ru")
        ) {
            return Pair(true, "Поддельный домен Сбербанка ($host)")
        }

        // 4. T-Bank spoofing
        if ((host.contains("t-bank-") || host.contains("tbank-login") || host.contains("t-inkoff")) &&
            !host.endsWith("tbank.ru") && !host.endsWith("tinkoff.ru")
        ) {
            return Pair(true, "Поддельный домен Т-Банка ($host)")
        }

        // 5. VTB spoofing
        if ((host.contains("vtb-online") || host.contains("vtb-login")) &&
            !host.endsWith("vtb.ru")
        ) {
            return Pair(true, "Поддельный домен ВТБ ($host)")
        }

        // 6. Avito spoofing
        if (host.contains("avito-") && !host.endsWith("avito.ru")) {
            return Pair(true, "Поддельный домен Авито ($host)")
        }

        // 7. CDEK spoofing
        if (host.contains("cdek-") && !host.endsWith("cdek.ru")) {
            return Pair(true, "Поддельный домен СДЭК ($host)")
        }

        return Pair(false, null)
    }
}
