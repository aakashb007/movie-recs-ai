package com.reelwise.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.reelwise.app.data.model.TasteProfile
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class Settings(
    val region: String,
    val onboardingDone: Boolean,
    val dynamicColor: Boolean,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val taste: TasteProfile = TasteProfile(),
)

@Singleton
class SettingsStore @Inject constructor(
    @Named("settings") private val store: DataStore<Preferences>,
    private val json: Json,
) {
    val settings: Flow<Settings> = store.data.map { prefs ->
        Settings(
            region = prefs[REGION] ?: defaultRegion(),
            onboardingDone = prefs[ONBOARDING_DONE] ?: false,
            dynamicColor = prefs[DYNAMIC_COLOR] ?: false,
            themeMode = prefs[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
            taste = prefs[TASTE]?.let { runCatching { json.decodeFromString(TasteProfile.serializer(), it) }.getOrNull() }
                ?: TasteProfile(),
        )
    }

    suspend fun setRegion(region: String) = store.edit { it[REGION] = region }
    suspend fun setOnboardingDone(done: Boolean) = store.edit { it[ONBOARDING_DONE] = done }
    suspend fun setDynamicColor(enabled: Boolean) = store.edit { it[DYNAMIC_COLOR] = enabled }
    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[THEME] = mode.name }
    suspend fun setTaste(taste: TasteProfile) = store.edit { it[TASTE] = json.encodeToString(TasteProfile.serializer(), taste) }

    private companion object {
        val REGION = stringPreferencesKey("region")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val THEME = stringPreferencesKey("theme_mode")
        val TASTE = stringPreferencesKey("taste_profile")

        fun defaultRegion(): String =
            Locale.getDefault().country.takeIf { it.length == 2 }?.uppercase() ?: "US"
    }
}
