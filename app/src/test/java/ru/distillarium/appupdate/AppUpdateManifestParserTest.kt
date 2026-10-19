package ru.distillarium.appupdate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManifestParserTest {
    @Test
    fun `valid manifest is parsed`() {
        val manifest = AppUpdateManifestParser.parse(
            MANIFEST.toByteArray(),
        )
        assertEquals(1_000_001, manifest.versionCode)
        assertEquals("1.0.1", manifest.versionName)
        assertEquals("https://example.org/distillarium.apk", manifest.apkUrl)
        assertEquals("Заметки", manifest.notes)
    }

    @Test(expected = AppUpdateException::class)
    fun `version code must match version name`() {
        AppUpdateManifestParser.parse(
            MANIFEST.replace("\"versionCode\": 1000001", "\"versionCode\": 1000002")
                .toByteArray(),
        )
    }

    @Test(expected = AppUpdateException::class)
    fun `apk url must be https`() {
        AppUpdateManifestParser.parse(
            MANIFEST.replace("https://example.org", "http://example.org").toByteArray(),
        )
    }

    @Test(expected = AppUpdateException::class)
    fun `unknown schema is rejected`() {
        AppUpdateManifestParser.parse(
            MANIFEST.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2").toByteArray(),
        )
    }

    @Test
    fun `version code is derived from semver`() {
        assertEquals(1_000_000, AppVersion.codeFor("1.0.0"))
        assertEquals(1_002_003, AppVersion.codeFor("1.2.3"))
        assertTrue(AppVersion.isValidName("0.1.0"))
    }

    private companion object {
        val MANIFEST = """
            {
              "schemaVersion": 1,
              "versionCode": 1000001,
              "versionName": "1.0.1",
              "minSdk": 26,
              "released": "2026-10-19",
              "apk": {
                "url": "https://example.org/distillarium.apk",
                "size": 12345678,
                "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
              },
              "notes": "Заметки"
            }
        """.trimIndent()
    }
}
