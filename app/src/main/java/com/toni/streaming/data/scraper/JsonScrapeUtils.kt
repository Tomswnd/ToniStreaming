package com.toni.streaming.data.scraper

import java.util.concurrent.ConcurrentHashMap

/**
 * Generic, domain-agnostic helpers for pulling values out of the raw JSON strings that AnimeUnity
 * embeds in its HTML attributes. Kept as top-level functions in the scraper package so callers use
 * them unqualified.
 *
 * These were previously private members of [AnimeUnityScraper]; extracting them shrinks that class
 * and lets the per-key regexes be compiled once and reused instead of on every call (they used to
 * be rebuilt for every field of every parsed object).
 */

// Per-key compiled regexes, cached so a given key's pattern is only compiled once.
private val jsonStringRegexCache = ConcurrentHashMap<String, Regex>()
private val jsonNumberRegexCache = ConcurrentHashMap<String, Regex>()
private val UNICODE_REGEX = """\\u([0-9a-fA-F]{4})""".toRegex()

/**
 * Extracts a string value for [key] from a JSON object string, handling escaped characters.
 */
fun extractJsonString(json: String, key: String): String? {
    val regex = jsonStringRegexCache.getOrPut(key) {
        """"$key"\s*:\s*"((?:[^"\\]|\\.)*)"""".toRegex()
    }
    return regex.find(json)?.groupValues?.get(1)?.let { value ->
        // Unescape common JSON escapes
        value.replace("\\/", "/")
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
    }
}

/**
 * Extracts a string value for [key] that lives at the TOP LEVEL (depth 1) of the JSON object,
 * ignoring identical keys nested inside arrays/sub-objects (e.g. related-anime entries).
 * Returns null if the key is absent or its value is not a string (e.g. null/number).
 */
fun extractTopLevelJsonString(json: String, key: String): String? {
    val target = "\"$key\""
    var i = 0
    var depth = 0
    while (i < json.length) {
        when (json[i]) {
            '{', '[' -> { depth++; i++ }
            '}', ']' -> { depth--; i++ }
            '"' -> {
                if (depth == 1 && json.startsWith(target, i)) {
                    var j = i + target.length
                    while (j < json.length && json[j].isWhitespace()) j++
                    if (j < json.length && json[j] == ':') {
                        j++
                        while (j < json.length && json[j].isWhitespace()) j++
                        if (j < json.length && json[j] == '"') {
                            val sb = StringBuilder()
                            j++
                            while (j < json.length) {
                                val ch = json[j]
                                if (ch == '\\' && j + 1 < json.length) {
                                    sb.append(ch).append(json[j + 1]); j += 2; continue
                                }
                                if (ch == '"') break
                                sb.append(ch); j++
                            }
                            return sb.toString()
                                .replace("\\/", "/")
                                .replace("\\\"", "\"")
                                .replace("\\n", "\n")
                                .replace("\\t", "\t")
                                .replace("\\\\", "\\")
                        }
                        // top-level key present but value is null/number/etc → no string value
                        return null
                    }
                }
                // skip the rest of this string token so its content doesn't affect depth
                i++
                while (i < json.length) {
                    if (json[i] == '\\') { i += 2; continue }
                    if (json[i] == '"') { i++; break }
                    i++
                }
            }
            else -> i++
        }
    }
    return null
}

/**
 * Extracts a raw value (number, boolean, null) for [key] from a JSON object string.
 */
fun extractJsonValue(json: String, key: String): String? {
    val numRegex = jsonNumberRegexCache.getOrPut(key) {
        """"$key"\s*:\s*(\d+)""".toRegex()
    }
    numRegex.find(json)?.let { return it.groupValues[1] }
    // Also try string value
    return extractJsonString(json, key)
}

/**
 * Iterates over the balanced top-level `{ ... }` objects inside a JSON array string,
 * correctly skipping quoted strings (so braces/quotes inside values don't break parsing).
 */
inline fun forEachTopLevelJsonObject(json: String, action: (String) -> Unit) {
    var depth = 0
    var start = -1
    var i = 0
    while (i < json.length) {
        when (json[i]) {
            '"' -> {
                i++
                while (i < json.length) {
                    if (json[i] == '\\') { i += 2; continue }
                    if (json[i] == '"') break
                    i++
                }
            }
            '{' -> { if (depth == 0) start = i; depth++ }
            '}' -> {
                depth--
                if (depth == 0 && start >= 0) {
                    action(json.substring(start, i + 1))
                    start = -1
                }
            }
        }
        i++
    }
}

/**
 * Decodes dynamic unicode hex escapes like `è` to their respective characters.
 */
fun decodeUnicode(input: String): String {
    return UNICODE_REGEX.replace(input) { matchResult ->
        try {
            val charCode = matchResult.groupValues[1].toInt(16)
            charCode.toChar().toString()
        } catch (e: Exception) {
            matchResult.value
        }
    }
}

/**
 * Cleans escaping and special Unicode characters in scraped text.
 */
fun cleanText(text: String): String {
    val decoded = decodeUnicode(text)
    return decoded
        .replace("\\u201c", "“")
        .replace("\\u201d", "”")
        .replace("\\u2018", "‘")
        .replace("\\u2019", "’")
        .replace("\\u2013", "–")
        .replace("\\u2014", "—")
        .replace("\\u200b", "")
        .replace("\\u00a0", " ")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("\\n", "\n")
        .replace("\\\"", "\"")
        .replace("\\'", "'")
        .replace("\\/", "/")
        .replace("\\r", "")
        .trim()
}
