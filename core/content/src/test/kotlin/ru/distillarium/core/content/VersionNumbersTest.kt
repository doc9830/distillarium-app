package ru.distillarium.core.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class VersionNumbersTest {
    @Test
    fun comparesNumericSegmentsNotStrings() {
        assertTrue(VersionNumbers.compare("2026.10.1", "2026.9.9") > 0)
        assertTrue(VersionNumbers.compare("2026.10.1", "2026.10.0") > 0)
        assertEquals(0, VersionNumbers.compare("2026.10.1", "2026.10.1.0"))
        assertTrue(VersionNumbers.compare("9.0.0", "0.1.0") > 0)
    }

    @Test
    fun rejectsGarbage() {
        assertThrows(ContentException::class.java) {
            VersionNumbers.compare("2026.10", "x")
        }
    }
}
