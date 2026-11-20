package dev.anilbeesetti.nextplayer.core.common.extensions

import android.net.Uri
import java.net.URLDecoder
import java.util.Locale

private val PRIORITY_FILENAME_QUERY_KEYS = listOf(
    "filename",
    "file_name",
    "title",
    "video_title",
    "media_title",
    "dn",
    "name",
    "file",
)

private val CONTENT_DISPOSITION_QUERY_KEYS = setOf(
    "response-content-disposition",
    "content-disposition",
    "rscd",
)

private val KNOWN_MEDIA_EXTENSIONS = setOf(
    "mp4",
    "m4v",
    "mkv",
    "webm",
    "avi",
    "mov",
    "wmv",
    "flv",
    "mpeg",
    "mpg",
    "3gp",
    "ts",
    "m2ts",
    "mts",
    "vob",
    "ogv",
    "rmvb",
    "asf",
    "divx",
    "f4v",
    "iso",
    "m3u8",
    "mpd",
    "mp3",
    "flac",
    "aac",
    "ogg",
    "opus",
    "wav",
    "m4a",
)

private val RFC5987_FILENAME_REGEX = Regex(
    pattern = """(?i)filename\*\s*=\s*(?:UTF-8''|utf-8'')?([^\s;]+)""",
)

private val CONTENT_DISPOSITION_FILENAME_REGEX = Regex(
    pattern = """(?i)filename\s*=\s*(?:"([^"]+)"|'([^']+)'|([^\s;]+))""",
)

/**
 * Extracts the best human-readable media filename from a [Uri], checking query parameters
 * (such as `filename=`, `response-content-disposition=`, `title=`, `file=`), URL fragments,
 * and path segments before falling back to [Uri.getLastPathSegment].
 */
fun Uri.extractFilenameFromUri(): String? = extractFilenameFromUrl(
    uriString = toString(),
    fallbackSegment = lastPathSegment,
    fallbackHost = host,
)

/**
 * Extracts the best human-readable media filename from a raw URL/URI string.
 */
fun extractFilenameFromUrl(
    uriString: String,
    fallbackSegment: String? = null,
    fallbackHost: String? = null,
): String? {
    val trimmed = uriString.trim()
    if (trimmed.isEmpty()) return null

    val withoutFragment = trimmed.substringBefore(delimiter = '#')
    val rawFragment = trimmed.substringAfter(
        delimiter = '#',
        missingDelimiterValue = "",
    )
    val rawQuery = withoutFragment.substringAfter(
        delimiter = '?',
        missingDelimiterValue = "",
    )
    val beforeQuery = withoutFragment.substringBefore(delimiter = '?')

    val queryParams = parseQueryParameters(rawQuery = rawQuery)

    // 1. Check Content-Disposition style query parameters (e.g., S3/R2/CDN presigned URLs).
    for ((key, value) in queryParams) {
        if (key.lowercase(Locale.ROOT) in CONTENT_DISPOSITION_QUERY_KEYS) {
            extractFromContentDisposition(disposition = value)?.let { return it }
        }
    }

    // 2. Check explicit filename/title query parameters that have a known media extension first.
    for (targetKey in PRIORITY_FILENAME_QUERY_KEYS) {
        val match = queryParams.firstOrNull { (key, value) ->
            key.equals(
                other = targetKey,
                ignoreCase = true,
            ) &&
                value.isNotBlank()
        }?.second
        val candidate = match?.let(block = ::sanitizeCandidateFilename)
        if (candidate != null && hasKnownMediaExtension(name = candidate)) {
            return candidate
        }
    }

    // 3. Check explicit filename/title query parameters even without a known extension.
    for (targetKey in PRIORITY_FILENAME_QUERY_KEYS) {
        val match = queryParams.firstOrNull { (key, value) ->
            key.equals(
                other = targetKey,
                ignoreCase = true,
            ) &&
                value.isNotBlank()
        }?.second
        val candidate = match?.let(block = ::sanitizeCandidateFilename)
        if (candidate != null) {
            return candidate
        }
    }

    // 4. Check any other query parameter whose value ends with a known media extension.
    for ((_, value) in queryParams) {
        val candidate = sanitizeCandidateFilename(raw = value)
        if (candidate != null && hasKnownMediaExtension(name = candidate)) {
            return candidate
        }
    }

    // 5. Check URL fragment (#filename=... or #Movie.Name.mkv).
    if (rawFragment.isNotBlank()) {
        val fragmentParams = parseQueryParameters(rawQuery = rawFragment)
        for (targetKey in PRIORITY_FILENAME_QUERY_KEYS) {
            val match = fragmentParams.firstOrNull { (key, value) ->
                key.equals(
                    other = targetKey,
                    ignoreCase = true,
                ) &&
                    value.isNotBlank()
            }?.second
            val candidate = match?.let(block = ::sanitizeCandidateFilename)
            if (candidate != null) {
                return candidate
            }
        }
        val fragmentCandidate = sanitizeCandidateFilename(raw = decodeUrlComponent(rawFragment))
        if (fragmentCandidate != null && hasKnownMediaExtension(name = fragmentCandidate)) {
            return fragmentCandidate
        }
    }

    // 6. Check URL path segments from right to left for a segment with a known media extension.
    val rawPath = extractRawPath(uriWithoutQuery = beforeQuery)
    val pathSegments = rawPath
        .split('/')
        .mapNotNull { segment ->
            sanitizeCandidateFilename(raw = decodeUrlComponent(segment))
        }
    pathSegments.lastOrNull(predicate = ::hasKnownMediaExtension)?.let { return it }

    // 7. Fallback to last path segment or host.
    val fallbackFromSegment = fallbackSegment
        ?.let(block = ::decodeUrlComponent)
        ?.let(block = ::sanitizeCandidateFilename)
        ?: pathSegments.lastOrNull()
    if (!fallbackFromSegment.isNullOrBlank()) {
        return fallbackFromSegment
    }

    return fallbackHost?.takeIf(predicate = String::isNotBlank)
}

private fun parseQueryParameters(rawQuery: String): List<Pair<String, String>> {
    if (rawQuery.isBlank()) return emptyList()
    return rawQuery
        .split('&')
        .mapNotNull { part ->
            if (part.isBlank()) return@mapNotNull null
            val eqIndex = part.indexOf(char = '=')
            if (eqIndex <= 0) return@mapNotNull null
            val rawKey = part.substring(
                startIndex = 0,
                endIndex = eqIndex,
            )
            val rawValue = part.substring(startIndex = eqIndex + 1)
            val key = decodeUrlComponent(raw = rawKey).trim()
            val value = decodeUrlComponent(raw = rawValue).trim()
            if (key.isEmpty() || value.isEmpty()) null else key to value
        }
}

private fun extractFromContentDisposition(disposition: String): String? {
    val decoded = decodeUrlComponent(raw = disposition)
    RFC5987_FILENAME_REGEX.find(input = decoded)?.groupValues?.getOrNull(index = 1)?.let { raw ->
        val candidate = sanitizeCandidateFilename(
            raw = decodeUrlComponent(raw = raw.trim('"', '\'')),
        )
        if (candidate != null) return candidate
    }
    CONTENT_DISPOSITION_FILENAME_REGEX.find(input = decoded)?.let { matchResult ->
        val raw = matchResult.groupValues
            .drop(n = 1)
            .firstOrNull(predicate = String::isNotEmpty)
            ?.let(block = ::decodeUrlComponent)
        val candidate = raw?.let(block = ::sanitizeCandidateFilename)
        if (candidate != null) return candidate
    }
    return null
}

private fun extractRawPath(uriWithoutQuery: String): String {
    val schemeIndex = uriWithoutQuery.indexOf(string = "://")
    if (schemeIndex < 0) return uriWithoutQuery
    val authorityAndPath = uriWithoutQuery.substring(startIndex = schemeIndex + 3)
    val slashIndex = authorityAndPath.indexOf(char = '/')
    return if (slashIndex >= 0) {
        authorityAndPath.substring(startIndex = slashIndex + 1)
    } else {
        ""
    }
}

private fun sanitizeCandidateFilename(raw: String): String? {
    val cleaned = raw
        .trim()
        .trim('"', '\'')
        .substringBefore(delimiter = '?')
        .substringBefore(delimiter = '#')
        .replace(
            oldChar = '\\',
            newChar = '/',
        )
        .trimEnd('/')
        .substringAfterLast(delimiter = '/')
        .trim()
    return cleaned.takeIf(predicate = String::isNotEmpty)
}

private fun hasKnownMediaExtension(name: String): Boolean {
    val ext = name.substringAfterLast(
        delimiter = '.',
        missingDelimiterValue = "",
    ).lowercase(Locale.ROOT)
    return ext in KNOWN_MEDIA_EXTENSIONS
}

private fun decodeUrlComponent(raw: String): String = runCatching {
    URLDecoder.decode(
        raw.replace(
            oldValue = "+",
            newValue = "%2B",
        ),
        Charsets.UTF_8.name(),
    )
}.getOrDefault(defaultValue = raw)
