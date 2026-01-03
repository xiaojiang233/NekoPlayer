package top.xiaojiang233.nekoplayer.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import top.xiaojiang233.nekoplayer.NekoPlayerApplication

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object SettingsRepository {
    private val context = NekoPlayerApplication.getAppContext()
    private val dataStore = context.dataStore

    private val LYRICS_FONT_SIZE_KEY = floatPreferencesKey("lyrics_font_size")
    private val LYRICS_FONT_FAMILY_KEY = stringPreferencesKey("lyrics_font_family")
    private val LYRICS_BLUR_INTENSITY_KEY = floatPreferencesKey("lyrics_blur_intensity")
    private val SHOW_PLATFORM_TAG_KEY = booleanPreferencesKey("show_platform_tag")
    private val SEARCH_HISTORY_KEY = stringSetPreferencesKey("search_history")
    private val VIEW_MODE_KEY = stringPreferencesKey("view_mode")

    val lyricsFontSize: Flow<Float> = dataStore.data.map { preferences ->
        preferences[LYRICS_FONT_SIZE_KEY] ?: 28f
    }

    val lyricsFontFamily: Flow<String> = dataStore.data.map { preferences ->
        preferences[LYRICS_FONT_FAMILY_KEY] ?: "Default"
    }

    val lyricsBlurIntensity: Flow<Float> = dataStore.data.map { preferences ->
        preferences[LYRICS_BLUR_INTENSITY_KEY] ?: 10f
    }

    val showPlatformTag: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SHOW_PLATFORM_TAG_KEY] ?: true
    }

    val searchHistory: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[SEARCH_HISTORY_KEY] ?: emptySet()
    }

    val viewMode: Flow<String> = dataStore.data.map { preferences ->
        preferences[VIEW_MODE_KEY] ?: "List"
    }

    suspend fun setLyricsFontSize(size: Float) {
        dataStore.edit { preferences ->
            preferences[LYRICS_FONT_SIZE_KEY] = size
        }
    }

    suspend fun setLyricsFontFamily(family: String) {
        dataStore.edit { preferences ->
            preferences[LYRICS_FONT_FAMILY_KEY] = family
        }
    }

    suspend fun setLyricsBlurIntensity(intensity: Float) {
        dataStore.edit { preferences ->
            preferences[LYRICS_BLUR_INTENSITY_KEY] = intensity
        }
    }

    suspend fun setShowPlatformTag(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_PLATFORM_TAG_KEY] = show
        }
    }

    suspend fun addSearchHistory(query: String) {
        dataStore.edit { preferences ->
            val currentHistory = preferences[SEARCH_HISTORY_KEY] ?: emptySet()
            preferences[SEARCH_HISTORY_KEY] = currentHistory + query
        }
    }

    suspend fun clearSearchHistory() {
        dataStore.edit { preferences ->
            preferences.remove(SEARCH_HISTORY_KEY)
        }
    }

    suspend fun updateViewMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[VIEW_MODE_KEY] = mode
        }
    }

    @Serializable
    data class SettingsData(
        val lyricsFontSize: Float,
        val lyricsFontFamily: String,
        val lyricsBlurIntensity: Float,
        val showPlatformTag: Boolean,
        val viewMode: String = "List"
    )

    suspend fun getAllSettings(): SettingsData {
        val preferences = dataStore.data.first()
        return SettingsData(
            lyricsFontSize = preferences[LYRICS_FONT_SIZE_KEY] ?: 28f,
            lyricsFontFamily = preferences[LYRICS_FONT_FAMILY_KEY] ?: "Default",
            lyricsBlurIntensity = preferences[LYRICS_BLUR_INTENSITY_KEY] ?: 10f,
            showPlatformTag = preferences[SHOW_PLATFORM_TAG_KEY] ?: true,
            viewMode = preferences[VIEW_MODE_KEY] ?: "List"
        )
    }

    suspend fun restoreSettings(settings: SettingsData) {
        dataStore.edit { preferences ->
            preferences[LYRICS_FONT_SIZE_KEY] = settings.lyricsFontSize
            preferences[LYRICS_FONT_FAMILY_KEY] = settings.lyricsFontFamily
            preferences[LYRICS_BLUR_INTENSITY_KEY] = settings.lyricsBlurIntensity
            preferences[SHOW_PLATFORM_TAG_KEY] = settings.showPlatformTag
        }
    }
}
