package com.ostemirt.ezbolus.data.libre

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Pure-logic tests for the LibreLinkUp layer. The Account-Id hash is load-bearing:
 * a wrong value silently 911s every authenticated call, so it's pinned to known
 * SHA-256 vectors here.
 */
class LibreTest {

    @Test
    fun sha256Hex_matchesKnownVectors() {
        // NIST/standard vectors.
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            LibreApi.sha256Hex("abc"),
        )
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            LibreApi.sha256Hex(""),
        )
    }

    @Test
    fun sha256Hex_isAlways64LowercaseHex() {
        val hash = LibreApi.sha256Hex("some-user-id-guid")
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' }, "must be lowercase hex")
    }

    @Test
    fun trend_mapsCodesAndFlagsRapid() {
        assertEquals(LibreTrend.FALLING_FAST, LibreTrend.fromCode(1))
        assertEquals(LibreTrend.STABLE, LibreTrend.fromCode(3))
        assertEquals(LibreTrend.RISING_FAST, LibreTrend.fromCode(5))
        assertEquals(LibreTrend.UNKNOWN, LibreTrend.fromCode(null))
        assertEquals(LibreTrend.UNKNOWN, LibreTrend.fromCode(9))

        assertTrue(LibreTrend.FALLING_FAST.isRapid)
        assertTrue(LibreTrend.RISING_FAST.isRapid)
        assertFalse(LibreTrend.STABLE.isRapid)
        assertFalse(LibreTrend.RISING.isRapid)
    }

    @Test
    fun reading_convertsMgdlToMmol() {
        val r = reading(mgdl = 90)
        assertEquals(4.994, r.mmol, 1e-3)   // 90 / 18.0182
    }

    @Test
    fun reading_ageIsWholeMinutesAndNeverNegative() {
        val now = Instant.parse("2026-07-05T12:00:00Z")
        assertEquals(5L, reading(ts = now.minusSeconds(5 * 60 + 30)).ageMinutes(now)) // 5m30s -> 5
        assertEquals(0L, reading(ts = now.plusSeconds(120)).ageMinutes(now))          // future -> 0
    }

    private fun reading(
        mgdl: Int = 100,
        trend: LibreTrend = LibreTrend.STABLE,
        ts: Instant = Instant.parse("2026-07-05T12:00:00Z"),
    ) = LibreReading(mgdl = mgdl, trend = trend, timestamp = ts, isHigh = false, isLow = false)
}
