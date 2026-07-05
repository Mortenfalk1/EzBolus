package com.ostemirt.ezbolus.data.libre

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persists the LibreLinkUp session (email + JWT + expiry + Account-Id hash + base
 * URL) so the user stays connected between launches without re-entering a password.
 *
 * We deliberately DO NOT persist the password — re-auth re-prompts for it.
 *
 * TODO(hardening before release): move the token to EncryptedSharedPreferences /
 * Android Keystore. This app-private DataStore is unencrypted at rest; acceptable
 * for a personal build, not ideal for a public one. See docs/LIBRELINKUP_INTEGRATION.md.
 */
private val Context.libreStore: DataStore<Preferences> by preferencesDataStore(name = "librelink")

class LibreCredentialStore(private val context: Context) {

    private object Keys {
        val EMAIL = stringPreferencesKey("email")
        val TOKEN = stringPreferencesKey("token")
        val EXPIRES = longPreferencesKey("expires_epoch_sec")
        val ACCOUNT_ID = stringPreferencesKey("account_id")
        val BASE_URL = stringPreferencesKey("base_url")
    }

    /** Current session, or null when not connected. */
    val session: Flow<LibreSession?> = context.libreStore.data.map { p ->
        val email = p[Keys.EMAIL]
        val token = p[Keys.TOKEN]
        val accountId = p[Keys.ACCOUNT_ID]
        val baseUrl = p[Keys.BASE_URL]
        if (email.isNullOrBlank() || token.isNullOrBlank() ||
            accountId.isNullOrBlank() || baseUrl.isNullOrBlank()
        ) {
            null
        } else {
            LibreSession(
                email = email,
                token = token,
                expiresEpochSec = p[Keys.EXPIRES] ?: 0L,
                accountId = accountId,
                baseUrl = baseUrl,
            )
        }
    }

    suspend fun save(session: LibreSession) {
        context.libreStore.edit { p ->
            p[Keys.EMAIL] = session.email
            p[Keys.TOKEN] = session.token
            p[Keys.EXPIRES] = session.expiresEpochSec
            p[Keys.ACCOUNT_ID] = session.accountId
            p[Keys.BASE_URL] = session.baseUrl
        }
    }

    suspend fun clear() {
        context.libreStore.edit { it.clear() }
    }
}

/** A stored LibreLinkUp session. No password is kept. */
data class LibreSession(
    val email: String,
    val token: String,
    val expiresEpochSec: Long,
    val accountId: String,
    val baseUrl: String,
) {
    fun isExpired(nowEpochSec: Long): Boolean = expiresEpochSec in 1 until nowEpochSec
}
