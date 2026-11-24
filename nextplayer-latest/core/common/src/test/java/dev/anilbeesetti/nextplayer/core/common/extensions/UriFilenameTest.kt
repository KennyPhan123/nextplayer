package dev.anilbeesetti.nextplayer.core.common.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UriFilenameTest {

    @Test
    fun extractFilenameFromUrl_torboxDownloadUrlWithFilenameQueryParam_extractsMovieFilename() {
        val url =
            "https://nexus-397.apac.tb-cdn.pw/dld/5c392027-ca7b-4549-9293-8d5c987c9c31" +
                "?token=d012cac7-4a7a-4f5c-851b-f215d505d321" +
                "&filename=The.Shining.1980.REPACK.2160p.UHD.Blu-ray.Remux.DV.HDR.HEVC.FLAC.1.0-CiNEPHiLES.mkv"

        assertEquals(
            "The.Shining.1980.REPACK.2160p.UHD.Blu-ray.Remux.DV.HDR.HEVC.FLAC.1.0-CiNEPHiLES.mkv",
            extractFilenameFromUrl(uriString = url),
        )
    }

    @Test
    fun extractFilenameFromUrl_presignedContentDispositionQueryParam_extractsMovieFilename() {
        val url =
            "https://bucket.r2.cloudflarestorage.com/obj/9f8e7d6c" +
                "?X-Amz-Algorithm=AWS4-HMAC-SHA256" +
                "&response-content-disposition=attachment%3B%20filename%3D%22Interstellar.2014.2160p.HDR10+.mkv%22"

        assertEquals(
            "Interstellar.2014.2160p.HDR10+.mkv",
            extractFilenameFromUrl(uriString = url),
        )
    }

    @Test
    fun extractFilenameFromUrl_pathWithActionSuffix_extractsMediaSegment() {
        val url = "https://cdn.example.com/files/Dune.Part.Two.2024.2160p.mkv/stream?token=123"

        assertEquals(
            "Dune.Part.Two.2024.2160p.mkv",
            extractFilenameFromUrl(uriString = url),
        )
    }

    @Test
    fun extractFilenameFromUrl_withoutFilenameInQueryOrExtension_fallsBackToLastSegment() {
        val url = "https://nexus-397.apac.tb-cdn.pw/dld/5c392027-ca7b-4549-9293-8d5c987c9c31?token=abc"

        assertEquals(
            "5c392027-ca7b-4549-9293-8d5c987c9c31",
            extractFilenameFromUrl(uriString = url),
        )
    }

    @Test
    fun extractFilenameFromHttpHeaders_pixeldrainUnquotedContentDisposition_extractsExactFilename() {
        val headers: Map<String?, List<String>> = mapOf(
            null to listOf("HTTP/1.1 200 OK"),
            "Content-Disposition" to listOf(
                "attachment; filename=DDLBase.com_Gemini.Man.2019.UHD.BluRay.2160p.TrueHD.Atmos.7.1.DV.HEVC.REMUX-FraMeSToR.mkv",
            ),
            "Content-Type" to listOf("video/x-matroska"),
        )

        assertEquals(
            "DDLBase.com_Gemini.Man.2019.UHD.BluRay.2160p.TrueHD.Atmos.7.1.DV.HEVC.REMUX-FraMeSToR.mkv",
            extractFilenameFromHttpHeaders(
                responseHeaders = headers,
                finalUrl = "https://pixeldrain.com/api/file/eXr4nikk?download",
                originalUrl = "https://pixeldrain.com/api/file/eXr4nikk?download",
            ),
        )
    }

    @Test
    fun extractFilenameFromHttpHeaders_rfc5987PreferredOverAsciiFallback() {
        val headers: Map<String?, List<String>> = mapOf(
            "content-disposition" to listOf(
                "attachment; filename=\"fallback.mkv\"; filename*=UTF-8''Gemini%20Man%20%282019%29.mkv",
            ),
        )

        assertEquals(
            "Gemini Man (2019).mkv",
            extractFilenameFromHttpHeaders(responseHeaders = headers),
        )
    }

    @Test
    fun extractFilenameFromHttpHeaders_redirectedFinalUrl_extractsMediaFilename() {
        assertEquals(
            "Gemini.Man.2019.2160p.mkv",
            extractFilenameFromHttpHeaders(
                responseHeaders = emptyMap(),
                finalUrl = "https://cdn.example.com/files/Gemini.Man.2019.2160p.mkv?sig=1",
                originalUrl = "https://short.example.com/eXr4nikk",
            ),
        )
    }

    @Test
    fun isProbeableHttpUrl_filtersLocalManifestAndNonHttpUris() {
        assertTrue(isProbeableHttpUrl(uriString = "https://pixeldrain.com/api/file/eXr4nikk?download"))
        assertFalse(isProbeableHttpUrl(uriString = "http://127.0.0.1:8080/stream"))
        assertFalse(isProbeableHttpUrl(uriString = "http://localhost:8080/stream"))
        assertFalse(isProbeableHttpUrl(uriString = "https://example.com/live/index.m3u8"))
        assertFalse(isProbeableHttpUrl(uriString = "smb://192.168.1.10/Movies/film.mkv"))
        assertFalse(isProbeableHttpUrl(uriString = "content://media/external/video/1"))
    }
}
