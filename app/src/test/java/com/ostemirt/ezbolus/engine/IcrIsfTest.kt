package com.ostemirt.ezbolus.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class IcrIsfTest {

    @Test
    fun fixed_returnsSameValueAllDay() {
        val f = Schedule.Fixed(12.0)
        for (h in 0..23) {
            assertEquals(12.0, resolveAt(f, h), 0.0, "hour $h")
        }
    }

    @Test
    fun hourly_returnsPerHourValues() {
        val h = Schedule.Hourly(mapOf(0 to 8.0, 12 to 10.0, 23 to 6.0))
        assertEquals(8.0, resolveAt(h, 0))
        assertEquals(10.0, resolveAt(h, 12))
        assertEquals(6.0, resolveAt(h, 23))
    }

    @Test
    fun hourly_boundaryBetweenHours_usesRightBlock() {
        // 06:59:59 -> hour 6; 07:00:00 -> hour 7
        val h6end = ZonedDateTime.of(2026, 3, 1, 6, 59, 59, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        val h7start = ZonedDateTime.of(2026, 3, 1, 7, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals(6, hourOfDay(h6end, ZoneId.of("UTC")))
        assertEquals(7, hourOfDay(h7start, ZoneId.of("UTC")))
    }

    @Test
    fun hourly_missingHour_throws() {
        val h = Schedule.Hourly(mapOf(0 to 8.0, 1 to 8.5))
        assertThrows(IllegalStateException::class.java) { resolveAt(h, 5) }
    }

    @Test
    fun hourly_rejectsOutOfRangeKeys() {
        assertThrows(IllegalArgumentException::class.java) {
            Schedule.Hourly(mapOf(24 to 8.0))
        }
    }

    @Test
    fun schedule_rejectsNonPositiveValues() {
        assertThrows(IllegalArgumentException::class.java) { Schedule.Fixed(0.0) }
        assertThrows(IllegalArgumentException::class.java) { Schedule.Fixed(-1.0) }
        assertThrows(IllegalArgumentException::class.java) { Schedule.Hourly(mapOf(0 to -1.0)) }
    }

    @Test
    fun ratios_eachDimensionResolvesIndependently() {
        // ICR hourly, ISF fixed, target hourly — all mixed modes.
        val icr = Schedule.Hourly((0..23).associateWith { if (it < 12) 10.0 else 12.0 })
        val isf = Schedule.Fixed(40.0)
        val target = Schedule.Hourly((0..23).associateWith { if (it < 6) 110.0 else 100.0 })
        val r = Ratios(icr, isf, target)
        assertEquals(10.0, resolveAt(r.icr, 3))
        assertEquals(12.0, resolveAt(r.icr, 20))
        assertEquals(40.0, resolveAt(r.isf, 3))
        assertEquals(40.0, resolveAt(r.isf, 20))
        assertEquals(110.0, resolveAt(r.target, 3))
        assertEquals(100.0, resolveAt(r.target, 20))
    }
}
