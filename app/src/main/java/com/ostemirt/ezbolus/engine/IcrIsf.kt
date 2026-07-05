package com.ostemirt.ezbolus.engine

/**
 * A user-configured value (ICR, ISF, or target) that is either constant
 * all day or varies by hour-of-day.
 */
sealed interface Schedule {
    /** One value used at every hour. */
    data class Fixed(val value: Double) : Schedule {
        init { require(value > 0.0) { "Schedule value must be > 0: $value" } }
    }

    /**
     * Per-hour values keyed by local hour 0..23. Every hour must have a
     * defined value — the safety-critical constraint the resolver enforces.
     */
    data class Hourly(val byHour: Map<Int, Double>) : Schedule {
        init {
            require(byHour.keys.all { it in 0..23 }) {
                "Hourly keys must be in 0..23: ${byHour.keys}"
            }
            require(byHour.values.all { it > 0.0 }) {
                "Hourly values must be > 0: $byHour"
            }
        }
    }
}

/** ICR + ISF + target glucose. Each may be Fixed or Hourly independently. */
data class Ratios(
    val icr: Schedule,
    val isf: Schedule,
    val target: Schedule,
)

/**
 * Look up a Schedule's value at the given local hour (0..23).
 *
 * If an [Schedule.Hourly] has no entry for `hour`, this throws — a missing
 * entry is a configuration error and must not silently produce a wrong dose.
 */
fun resolveAt(schedule: Schedule, hour: Int): Double {
    require(hour in 0..23) { "Hour must be in 0..23: $hour" }
    return when (schedule) {
        is Schedule.Fixed -> schedule.value
        is Schedule.Hourly -> schedule.byHour[hour]
            ?: error("No Hourly value configured for hour $hour")
    }
}

/**
 * Local-hour extractor used by the calculator screens to resolve ratios
 * at "now". Kept here so the engine has a single place to convert epoch
 * millis -> hour-of-day.
 */
fun hourOfDay(epochMillis: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Int =
    java.time.Instant.ofEpochMilli(epochMillis).atZone(zone).hour
