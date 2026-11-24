package dev.anilbeesetti.nextplayer.core.common.extensions

import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

private val MANIFEST_EXTENSIONS = setOf(
    "m3u",
    "m3u8",
    "mpd",
)

private val GENERIC_STREAM_SEGMENTS = setOf(
    "download",
    "stream",
    "video",
    "play",
    "watch",
    "media",
    "file",
    "index",
    "embed",
    "live",
    "player",
)

private val LOCAL_HOSTS = setOf(
    "localhost",
    "127.0.0.1",
    "0.0.0.0",
    "::1",
    "[::1]",
)

private val REDIRECT_STATUS_CODES = setOf(
    HttpURLConnection.HTTP_MOVED_PERM,
    HttpURLConnection.HTTP_MOVED_TEMP,
    HttpURLConnection.HTTP_SEE_OTHER,
    307,
    308,
)

private const val MAX_REDIRECTS = 5
private const val DEFAULT_HTTP_PROBE_TIMEOUT_MS = 4_000
private const val HTTP_USER_AGENT = "NextPlayer/1.0"

private val RFC5987_FILENAME_REGEX = Regex(
    pattern = """(?i)filename\*\s*=\s*(?:[^';\r\n]*''\s*)?(?:"([^"\r\n]+)"|'([^'\r\n]+)'|([^;\r\n]+))""",
)

private val CONTENT_DISPOSITION_FILENAME_REGEX = Regex(
    pattern = """(?i)filename\s*=\s*(?:"([^"\r\n]+)"|'([^'\r\n]+)'|([^;\r\n]+))""",
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
        if (candidate != null && candidate.hasKnownMediaExtension()) {
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
        if (candidate != null && candidate.hasKnownMediaExtension()) {
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
        if (fragmentCandidate != null && fragmentCandidate.hasKnownMediaExtension()) {
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
    pathSegments.lastOrNull(predicate = String::hasKnownMediaExtension)?.let { return it }

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

/**
 * Extracts a filename from HTTP response headers (`Content-Disposition`, `Content-Location`)
 * or a redirected [finalUrl].
 */
fun extractFilenameFromHttpHeaders(
    responseHeaders: Map<out String?, List<String>>,
    finalUrl: String? = null,
    originalUrl: String? = null,
): String? {
    for ((rawKey, values) in responseHeaders) {
        val key = rawKey ?: continue
        if (key.equals(
                other = "Content-Disposition",
                ignoreCase = true,
            )
        ) {
            for (headerValue in values) {
                extractFromContentDisposition(disposition = headerValue)?.let { return it }
            }
        }
    }

    for ((rawKey, values) in responseHeaders) {
        val key = rawKey ?: continue
        if (key.equals(
                other = "Content-Location",
                ignoreCase = true,
            )
        ) {
            for (headerValue in values) {
                val candidate = extractFilenameFromUrl(uriString = headerValue)
                if (candidate != null && !isGenericStreamName(name = candidate)) {
                    return candidate
                }
            }
        }
    }

    if (!finalUrl.isNullOrBlank() && finalUrl != originalUrl) {
        val candidate = extractFilenameFromUrl(uriString = finalUrl)
        val originalFallback = originalUrl?.let { extractFilenameFromUrl(uriString = it) }
        if (candidate != null &&
            !isGenericStreamName(name = candidate) &&
            (candidate.hasKnownMediaExtension() || candidate != originalFallback)
        ) {
            return candidate
        }
    }

    return null
}

/**
 * Probes an HTTP/HTTPS stream URL asynchronously to resolve the true media filename from
 * `Content-Disposition`, `Content-Location`, or redirect targets.
 *
 * Uses `HEAD` first and falls back to `GET` with `Range: bytes=0-0` if the server rejects `HEAD`
 * or omits disposition headers on `HEAD` responses.
 */
suspend fun resolveHttpStreamFilename(
    url: String,
    timeoutMs: Int = DEFAULT_HTTP_PROBE_TIMEOUT_MS,
): String? = withContext(Dispatchers.IO) {
    if (!isProbeableHttpUrl(uriString = url)) return@withContext null

    probeHttpHeaders(
        url = url,
        method = "HEAD",
        rangeHeader = null,
        timeoutMs = timeoutMs,
    )?.let { return@withContext it }

    probeHttpHeaders(
        url = url,
        method = "GET",
        rangeHeader = "bytes=0-0",
        timeoutMs = timeoutMs,
    )
}

/** Returns true if [uriString] is a non-local, non-manifest HTTP/HTTPS URL suitable for header probing. */
fun isProbeableHttpUrl(uriString: String): Boolean {
    val trimmed = uriString.trim()
    val scheme = trimmed.substringBefore(
        delimiter = "://",
        missingDelimiterValue = "",
    ).lowercase(Locale.ROOT)
    if (scheme != "http" && scheme != "https") return false

    val authority = trimmed
        .substringAfter(delimiter = "://")
        .substringBefore(delimiter = '/')
        .substringBefore(delimiter = '?')
        .substringBefore(delimiter = '#')
    val host = authority
        .substringAfterLast(delimiter = '@')
        .substringBefore(delimiter = ':')
        .lowercase(Locale.ROOT)
    if (host.isBlank() || host in LOCAL_HOSTS) return false

    val pathWithoutQuery = trimmed
        .substringBefore(delimiter = '#')
        .substringBefore(delimiter = '?')
    val pathExt = pathWithoutQuery
        .substringAfterLast(
            delimiter = '.',
            missingDelimiterValue = "",
        )
        .lowercase(Locale.ROOT)
    return pathExt !in MANIFEST_EXTENSIONS
}

fun String.hasKnownMediaExtension(): Boolean {
    val ext = substringAfterLast(
        delimiter = '.',
        missingDelimiterValue = "",
    ).lowercase(Locale.ROOT)
    return ext in KNOWN_MEDIA_EXTENSIONS
}

private fun probeHttpHeaders(
    url: String,
    method: String,
    rangeHeader: String?,
    timeoutMs: Int,
): String? {
    var currentUrl = runCatching { URL(url) }.getOrNull() ?: return null
    for (redirect in 0..MAX_REDIRECTS) {
        var connection: HttpURLConnection? = null
        try {
            val opened = currentUrl.openConnection() as? HttpURLConnection ?: return null
            connection = opened
            opened.requestMethod = method
            opened.connectTimeout = timeoutMs
            opened.readTimeout = timeoutMs
            opened.instanceFollowRedirects = false
            opened.setRequestProperty(
                "User-Agent",
                HTTP_USER_AGENT,
            )
            if (rangeHeader != null) {
                opened.setRequestProperty(
                    "Range",
                    rangeHeader,
                )
            }
            opened.connect()

            val responseCode = opened.responseCode
            val headers: Map<out String?, List<String>> = opened.headerFields ?: emptyMap()

            if (responseCode in REDIRECT_STATUS_CODES) {
                val location = opened.getHeaderField("Location")?.trim()
                if (location.isNullOrBlank()) return null
                val nextUrl = runCatching {
                    URL(
                        currentUrl,
                        location,
                    )
                }.getOrNull() ?: return null
                val nextProtocol = nextUrl.protocol.lowercase(Locale.ROOT)
                if (nextProtocol != "http" && nextProtocol != "https") return null
                currentUrl = nextUrl
                continue
            }

            if (responseCode in 200..299) {
                return extractFilenameFromHttpHeaders(
                    responseHeaders = headers,
                    finalUrl = currentUrl.toString(),
                    originalUrl = url,
                )
            }
            return null
        } catch (_: Exception) {
            return null
        } finally {
            connection?.disconnect()
        }
    }
    return null
}

private fun isGenericStreamName(name: String): Boolean = name.lowercase(Locale.ROOT) in GENERIC_STREAM_SEGMENTS

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
    val decoded = if ('%' in disposition && '=' !in disposition) {
        decodeUrlComponent(raw = disposition)
    } else {
        disposition
    }
    RFC5987_FILENAME_REGEX.find(input = decoded)?.let { matchResult ->
        val raw = matchResult.groupValues
            .drop(n = 1)
            .firstOrNull(predicate = String::isNotEmpty)
            ?.trim()
        val candidate = raw?.let {
            sanitizeCandidateFilename(raw = decodeUrlComponent(raw = it))
        }
        if (candidate != null) return candidate
    }
    CONTENT_DISPOSITION_FILENAME_REGEX.find(input = decoded)?.let { matchResult ->
        val raw = matchResult.groupValues
            .drop(n = 1)
            .firstOrNull(predicate = String::isNotEmpty)
            ?.trim()
        val candidate = raw?.let {
            sanitizeCandidateFilename(raw = decodeUrlComponent(raw = it))
        }
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

private fun decodeUrlComponent(raw: String): String = runCatching {
    URLDecoder.decode(
        raw.replace(
            oldValue = "+",
            newValue = "%2B",
        ),
        Charsets.UTF_8.name(),
    )
}.getOrDefault(defaultValue = raw)
