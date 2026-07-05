package com.ostemirt.ezbolus.data.libre

import java.time.Instant

/**
 * Domain models + error types for the (unofficial, reverse-engineered) LibreLinkUp
 * integration. See docs/LIBRELINKUP_INTEGRATION.md.
 *
 * Safety note: a LibreLinkUp reading is a *follower feed* value, not a primary
 * monitor. It is only ever used to PRE-FILL the glucose field, never to drive a
 * dose automatically. Callers must show the reading age + trend and honour the
 * user's staleness cutoff before trusting it.
 */

/** Trend arrow reported by LibreLinkUp (numeric code 1..5; 0 = unknown/absent). */
enum class LibreTrend(val code: Int, val arrow: String, val label: String) {
    FALLING_FAST(1, "↓", "falling fast"),
    FALLING(2, "↘", "falling"),
    STABLE(3, "→", "steady"),
    RISING(4, "↗", "rising"),
    RISING_FAST(5, "↑", "rising fast"),
    UNKNOWN(0, "•", "unknown");

    /** Changing fast enough that sensor lag materially matters for a dose. */
    val isRapid: Boolean get() = this == FALLING_FAST || this == RISING_FAST

    companion object {
        fun fromCode(code: Int?): LibreTrend = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/** A single glucose reading from LibreLinkUp, normalised to mg/dL + an instant. */
data class LibreReading(
    /** ValueInMgPerDl — the unit-independent source of truth. */
    val mgdl: Int,
    val trend: LibreTrend,
    /** Reading time. Parsed from FactoryTimestamp (UTC) when present. */
    val timestamp: Instant,
    val isHigh: Boolean,
    val isLow: Boolean,
) {
    /** mmol/L (caller rounds to 1 dp). Uses the app-wide 18.0182 factor. */
    val mmol: Double get() = mgdl / 18.0182

    /** Age of this reading relative to [now], in whole minutes (never negative). */
    fun ageMinutes(now: Instant): Long =
        ((now.toEpochMilli() - timestamp.toEpochMilli()).coerceAtLeast(0L)) / 60_000L
}

/** What kind of thing went wrong, so the UI can show the right message/action. */
enum class LibreErrorKind {
    INVALID_CREDENTIALS,  // wrong email/password
    TERMS_REQUIRED,       // must accept ToS/PP or verify email in the official app
    RATE_LIMITED,         // 429/430 or temporary ban
    NETWORK,              // no connectivity / timeout / server error
    NOT_CONNECTED,        // no stored session, or token expired -> reconnect
    NO_SENSOR,            // account has no shared sensor / connection
    PARSE,                // unexpected response shape (API may have changed)
    UNKNOWN,
}

/** Result wrapper for repository operations the UI consumes. */
sealed interface LibreResult<out T> {
    data class Ok<T>(val value: T) : LibreResult<T>
    data class Err(val kind: LibreErrorKind, val message: String) : LibreResult<Nothing>
}

// ---- Internal API exceptions (thrown by LibreApi, mapped to LibreResult in the repo) ----

sealed class LibreApiException(message: String) : Exception(message)
class LibreAuthException : LibreApiException("Invalid email or password.")
class LibreTermsException : LibreApiException(
    "Open the official LibreLinkUp app to accept the updated terms (or verify your " +
        "email), then try connecting again.",
)
class LibreRateLimitException(val retryAfterSec: Int?) : LibreApiException(
    "LibreLinkUp is rate-limiting requests. Wait a bit and try again.",
)
class LibreNoSensorException : LibreApiException(
    "No FreeStyle Libre sensor is shared with this LibreLinkUp account.",
)
class LibreNetworkException(message: String) : LibreApiException(message)
class LibreParseException(message: String) : LibreApiException(
    "Unexpected response from LibreLinkUp (the API may have changed): $message",
)
