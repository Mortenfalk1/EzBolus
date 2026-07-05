package com.ostemirt.ezbolus.data.libre

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Orchestrates the LibreLinkUp integration for the UI: connect / disconnect and a
 * single on-demand [fetchLatest]. Owns the session store; maps low-level
 * [LibreApiException]s to a UI-friendly [LibreResult].
 *
 * There is NO background polling here by design — the UI calls [fetchLatest] only
 * when the user taps the Fetch button (keeps us gentle on Abbott's rate limits).
 */
class LibreRepository(context: Context) {

    private val api = LibreApi()
    private val store = LibreCredentialStore(context.applicationContext)

    /** Whether a session exists (drives the Settings "connected" UI). */
    val isConnected: Flow<Boolean> = store.session.map { it != null }

    /** Connected account email, or null. */
    val connectedEmail: Flow<String?> = store.session.map { it?.email }

    /** Log in and persist the session. Password is used once and not stored. */
    suspend fun connect(email: String, password: String): LibreResult<Unit> = runCatchingLibre {
        val success = api.login(email.trim(), password)
        store.save(
            LibreSession(
                email = email.trim(),
                token = success.token,
                expiresEpochSec = success.expiresEpochSec,
                accountId = success.accountId,
                baseUrl = success.baseUrl,
            ),
        )
        Unit
    }

    suspend fun disconnect() = store.clear()

    /**
     * Fetch the newest reading for the (first) shared sensor. On-demand only.
     * Returns [LibreErrorKind.NOT_CONNECTED] if there's no session or the token
     * has expired (v1 requires an explicit reconnect since we don't store the
     * password to silently re-auth).
     */
    suspend fun fetchLatest(nowEpochSec: Long): LibreResult<LibreReading> {
        val session = currentSession()
            ?: return LibreResult.Err(LibreErrorKind.NOT_CONNECTED, "Connect LibreLinkUp first.")
        if (session.isExpired(nowEpochSec)) {
            return LibreResult.Err(
                LibreErrorKind.NOT_CONNECTED,
                "Your LibreLinkUp session expired — reconnect in Settings.",
            )
        }
        return runCatchingLibre {
            val connections = api.connections(session.baseUrl, session.token, session.accountId)
            val reading = connections.firstNotNullOfOrNull { it.reading }
                ?: throw LibreNoSensorException()
            reading
        }
    }

    // ---- internals ----

    private suspend fun currentSession(): LibreSession? = store.session.first()

    /** Runs a suspend block, mapping known LibreLinkUp failures to [LibreResult.Err]. */
    private suspend fun <T> runCatchingLibre(block: suspend () -> T): LibreResult<T> = try {
        LibreResult.Ok(block())
    } catch (e: LibreAuthException) {
        LibreResult.Err(LibreErrorKind.INVALID_CREDENTIALS, e.message.orEmpty())
    } catch (e: LibreTermsException) {
        LibreResult.Err(LibreErrorKind.TERMS_REQUIRED, e.message.orEmpty())
    } catch (e: LibreRateLimitException) {
        LibreResult.Err(LibreErrorKind.RATE_LIMITED, e.message.orEmpty())
    } catch (e: LibreNoSensorException) {
        LibreResult.Err(LibreErrorKind.NO_SENSOR, e.message.orEmpty())
    } catch (e: LibreNetworkException) {
        LibreResult.Err(LibreErrorKind.NETWORK, e.message.orEmpty())
    } catch (e: LibreParseException) {
        LibreResult.Err(LibreErrorKind.PARSE, e.message.orEmpty())
    } catch (e: LibreApiException) {
        LibreResult.Err(LibreErrorKind.UNKNOWN, e.message.orEmpty())
    }
}
