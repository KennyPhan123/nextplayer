package dev.anilbeesetti.nextplayer.core.common.extensions

import org.junit.Assert.assertEquals
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
}
