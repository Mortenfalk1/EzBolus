package com.ostemirt.ezbolus.data.libre

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Persists the LibreLinkUp session (email + JWT + expiry + Account-Id hash + base
 * URL) so the user stays connected between launches without re-entering a password.
 *
 * We deliberately DO NOT persist the password — re-auth re-prompts for it.
 *
 * Backed by [EncryptedSharedPreferences] (AES-256, key held in the Android
 * Keystore) rather than plaintext DataStore — this is another service's
 * credentials sitting on a stranger's device once this ships publicly.
 */
class LibreCredentialStore(private val context: Context) {

    private object Keys {
        const val EMAIL = "email"
        const val TOKEN = "token"
        const val EXPIRES = "expires_epoch_sec"
        const val ACCOUNT_ID = "account_id"
        const val BASE_URL = "base_url"
    }

    private val prefs: SharedPreferences = buildEncryptedPrefs(context)

    init {
        // One-time best-effort cleanup: earlier builds kept this session in a
        // plaintext DataStore file. Nothing reads it anymore — delete it so the
        // JWT doesn't linger unencrypted on disk after this upgrade.
        deleteLegacyPlaintextStore(context)
    }

    private val _session = MutableStateFlow(readSession())

    /** Current session, or null when not connected. */
    val session: StateFlow<LibreSession?> = _session

    suspend fun save(session: LibreSession) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(Keys.EMAIL, session.email)
            .putString(Keys.TOKEN, session.token)
            .putLong(Keys.EXPIRES, session.expiresEpochSec)
            .putString(Keys.ACCOUNT_ID, session.accountId)
            .putString(Keys.BASE_URL, session.baseUrl)
            .apply()
        _session.value = session
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
        _session.value = null
    }

    private fun readSession(): LibreSession? {
        val email = prefs.getString(Keys.EMAIL, null)
        val token = prefs.getString(Keys.TOKEN, null)
        val accountId = prefs.getString(Keys.ACCOUNT_ID, null)
        val baseUrl = prefs.getString(Keys.BASE_URL, null)
        if (email.isNullOrBlank() || token.isNullOrBlank() ||
            accountId.isNullOrBlank() || baseUrl.isNullOrBlank()
        ) {
            return null
        }
        return LibreSession(
            email = email,
            token = token,
            expiresEpochSec = prefs.getLong(Keys.EXPIRES, 0L),
            accountId = accountId,
            baseUrl = baseUrl,
        )
    }
}

private fun deleteLegacyPlaintextStore(context: Context) {
    try {
        java.io.File(context.filesDir, "datastore/librelink.preferences_pb").delete()
    } catch (_: Exception) {
        // Best-effort only — a failed delete here isn't worth surfacing.
    }
}

private fun buildEncryptedPrefs(context: Context): SharedPreferences {
    // androidx.security-crypto's newer MasterKey builder only ships in its 1.1.x
    // alphas; MasterKeys.getOrCreate is the stable-release (1.0.0) equivalent —
    // deprecated, but still the supported path for a non-alpha dependency here.
    val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    return EncryptedSharedPreferences.create(
        "librelink_encrypted",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
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
