package com.ostemirt.ezbolus.data.libre

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Low-level HTTP client for the unofficial LibreLinkUp API. Uses the platform's
 * HttpURLConnection + org.json only — no third-party networking deps, matching the
 * app's dependency-light style. All calls are suspend + run on Dispatchers.IO.
 *
 * This layer only speaks HTTP + JSON and throws typed [LibreApiException]s;
 * [LibreRepository] maps those to [LibreResult] and owns the session/token.
 *
 * Endpoints/headers per docs/LIBRELINKUP_INTEGRATION.md. Reverse-engineered and
 * unofficial: expect it to change/break. Keep [VERSION] roughly in step with the
 * real LibreLinkUp Android app.
 */
class LibreApi {

    companion object {
        const val GLOBAL_BASE = "https://api.libreview.io"
        private const val PRODUCT = "llu.android"
        // Must roughly track the real LibreLinkUp Android app version, or the
        // server may reject requests. Bump when connections start failing.
        private const val VERSION = "4.17.0"

        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 15_000

        /** LibreLinkUp timestamps look like "7/5/2026 1:38:50 PM" (US, 12-hour). */
        private val TS_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a", Locale.US)

        /** SHA-256 of [input] as 64-char lowercase hex — used for the Account-Id header. */
        fun sha256Hex(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            // Mask each byte to 0..255 — a raw signed Byte sign-extends to 8 hex chars.
            return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        }
    }

    /**
     * Log in. Handles the region [redirect] transparently (retries once against the
     * regional host) and detects the terms/verify [step].
     *
     * @throws LibreAuthException invalid credentials
     * @throws LibreTermsException must accept terms / verify email in the official app
     * @throws LibreRateLimitException 429/430
     * @throws LibreNetworkException connectivity/server
     * @throws LibreParseException unexpected response
     */
    suspend fun login(email: String, password: String): LibreLoginSuccess = withContext(Dispatchers.IO) {
        var base = GLOBAL_BASE
        val body = JSONObject().put("email", email).put("password", password).toString()

        // At most one redirect hop (global -> regional).
        repeat(2) { attempt ->
            val resp = request("POST", "$base/llu/auth/login", loginHeaders(), body)
            throwIfRateLimited(resp)
            val json = resp.json()
            val data = json.optJSONObject("data")

            // Region redirect: retry against api-<region>.libreview.io.
            if (data != null && data.optBoolean("redirect", false)) {
                val region = data.optString("region", "")
                if (region.isBlank() || attempt > 0) {
                    throw LibreParseException("redirect without a usable region")
                }
                base = "https://api-$region.libreview.io"
                return@repeat
            }

            // Terms of use / privacy / email-verify gate — cannot be handled via API.
            if (data != null && data.has("step")) throw LibreTermsException()
            if (json.optInt("status", 0) == 4) throw LibreTermsException()

            val ticket = data?.optJSONObject("authTicket")
            val userId = data?.optJSONObject("user")?.optString("id").orEmpty()
            if (ticket == null || userId.isBlank()) {
                // No ticket + no redirect + no step => almost always bad credentials.
                throw LibreAuthException()
            }

            val token = ticket.optString("token", "")
            if (token.isBlank()) throw LibreAuthException()
            val expiresSec = ticket.optLong("expires", 0L)

            return@withContext LibreLoginSuccess(
                token = token,
                expiresEpochSec = expiresSec,
                accountId = sha256Hex(userId),
                baseUrl = base,
            )
        }
        throw LibreParseException("login did not resolve after a region redirect")
    }

    /**
     * Fetch the followed connections (sensors) with their latest reading.
     * Uses the [baseUrl] resolved at login and the auth [token] + [accountId] hash.
     */
    suspend fun connections(
        baseUrl: String,
        token: String,
        accountId: String,
    ): List<LibreConnection> = withContext(Dispatchers.IO) {
        val resp = request("GET", "$baseUrl/llu/connections", authedHeaders(token, accountId), null)
        throwIfRateLimited(resp)
        if (resp.code == HttpURLConnection.HTTP_UNAUTHORIZED) throw LibreAuthException()

        val arr: JSONArray = resp.json().optJSONArray("data")
            ?: throw LibreParseException("connections response had no data array")

        (0 until arr.length()).mapNotNull { i ->
            val c = arr.optJSONObject(i) ?: return@mapNotNull null
            val patientId = c.optString("patientId", "")
            if (patientId.isBlank()) return@mapNotNull null
            LibreConnection(
                patientId = patientId,
                reading = c.optJSONObject("glucoseMeasurement")?.let(::parseReading),
            )
        }
    }

    // ---- parsing helpers ----

    private fun parseReading(gm: JSONObject): LibreReading? {
        val mgdl = when {
            gm.has("ValueInMgPerDl") -> gm.optInt("ValueInMgPerDl", -1)
            gm.has("Value") -> gm.optDouble("Value", -1.0).let { if (it < 0) -1 else it.toInt() }
            else -> -1
        }
        if (mgdl < 0) return null
        return LibreReading(
            mgdl = mgdl,
            trend = LibreTrend.fromCode(if (gm.has("TrendArrow")) gm.optInt("TrendArrow") else null),
            timestamp = parseTimestamp(gm),
            isHigh = gm.optBoolean("isHigh", false),
            isLow = gm.optBoolean("isLow", false),
        )
    }

    /** Prefer FactoryTimestamp (UTC); fall back to the account-local Timestamp. */
    private fun parseTimestamp(gm: JSONObject): Instant {
        gm.optString("FactoryTimestamp", "").takeIf { it.isNotBlank() }?.let { s ->
            runCatching { LocalDateTime.parse(s, TS_FORMAT).toInstant(ZoneOffset.UTC) }
                .getOrNull()?.let { return it }
        }
        gm.optString("Timestamp", "").takeIf { it.isNotBlank() }?.let { s ->
            runCatching {
                LocalDateTime.parse(s, TS_FORMAT).atZone(ZoneId.systemDefault()).toInstant()
            }.getOrNull()?.let { return it }
        }
        throw LibreParseException("could not parse reading timestamp")
    }

    // ---- HTTP plumbing ----

    private fun loginHeaders(): Map<String, String> = mapOf(
        "product" to PRODUCT,
        "version" to VERSION,
        "Content-Type" to "application/json",
        "cache-control" to "no-cache",
    )

    private fun authedHeaders(token: String, accountId: String): Map<String, String> =
        loginHeaders() + mapOf(
            "Authorization" to "Bearer $token",
            "Account-Id" to accountId,
        )

    private data class HttpResponse(val code: Int, val body: String, val retryAfterSec: Int?) {
        fun json(): JSONObject = try {
            JSONObject(body)
        } catch (e: JSONException) {
            throw LibreParseException("body was not JSON (HTTP $code)")
        }
    }

    private fun throwIfRateLimited(resp: HttpResponse) {
        // 429 standard, 430 Abbott-specific. Temporary bans can also surface as 403.
        if (resp.code == 429 || resp.code == 430) throw LibreRateLimitException(resp.retryAfterSec)
    }

    private fun request(
        method: String,
        urlStr: String,
        headers: Map<String, String>,
        body: String?,
    ): HttpResponse {
        val conn = try {
            (URL(urlStr).openConnection() as HttpURLConnection)
        } catch (e: IOException) {
            throw LibreNetworkException("Could not reach LibreLinkUp.")
        }
        return try {
            conn.requestMethod = method
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            // NB: do NOT set Accept-Encoding manually — HttpURLConnection adds gzip and
            // decodes transparently. Setting it ourselves would force manual decoding.
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }

            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val retryAfter = conn.getHeaderField("Retry-After")?.trim()?.toIntOrNull()
            HttpResponse(code, text, retryAfter)
        } catch (e: IOException) {
            throw LibreNetworkException("Network error talking to LibreLinkUp.")
        } finally {
            conn.disconnect()
        }
    }
}

/** Successful login outcome: the JWT plus everything needed for later calls. */
data class LibreLoginSuccess(
    val token: String,
    val expiresEpochSec: Long,
    /** SHA-256(user.id) hex — the required Account-Id header value. */
    val accountId: String,
    /** Region-resolved base URL (global or api-<region>). Reused for data calls. */
    val baseUrl: String,
)

/** One followed sensor connection and its latest reading (null if none yet). */
data class LibreConnection(
    val patientId: String,
    val reading: LibreReading?,
)
