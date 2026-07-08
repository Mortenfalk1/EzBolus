package com.ostemirt.ezbolus.ui.onboarding

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingStore: DataStore<Preferences> by
    preferencesDataStore(name = "ezbolus_onboarding")

/**
 * Tracks only whether the first-run welcome/disclaimer/ratio-setup gate has been
 * completed. Kept out of [com.ostemirt.ezbolus.data.settings.SettingsRepository]
 * deliberately — that repository's `replace()` (used by backup import) wholesale-
 * overwrites [com.ostemirt.ezbolus.data.settings.AppSettings], and coupling this
 * flag to it would let an import silently re-lock or re-unlock the gate.
 */
class OnboardingRepository(private val context: Context) {

    private object Keys {
        val COMPLETED = booleanPreferencesKey("completed")
    }

    val completed: Flow<Boolean> = context.onboardingStore.data.map { it[Keys.COMPLETED] ?: false }

    suspend fun setCompleted() {
        context.onboardingStore.edit { it[Keys.COMPLETED] = true }
    }
}
