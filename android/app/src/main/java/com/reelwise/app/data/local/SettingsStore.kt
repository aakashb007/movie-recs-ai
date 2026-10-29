package com.reelwise.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(
    val region: String,
    val onboardingDone: Boolean,
    val dynamicColor: Boolean,
)

@Singleton
class SettingsStore @Inject constructor(private val store: DataStore<Preferences>) {
    val settings: Flow<Settings> = store.data.map { prefs ->
        Settings(
            region = prefs[REGION] ?: defaultRegion(),
            onboardingDone = prefs[ONBOARDING_DONE] ?: false,
            dynamicColor = prefs[DYNAMIC_COLOR] ?: false,
        )
    }

    suspend fun setRegion(region: String) = store.edit { it[REGION] = region }
    suspend fun setOnboardingDone(done: Boolean) = store.edit { it[ONBOARDING_DONE] = done }
    suspend fun setDynamicColor(enabled: Boolean) = store.edit { it[DYNAMIC_COLOR] = enabled }

    private companion object {
        val REGION = stringPreferencesKey("region")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")

        fun defaultRegion(): String =
            Locale.getDefault().country.takeIf { it.length == 2 }?.uppercase() ?: "US"
    }
}
