package top.xiaojiang233.nekoplayer.data.repository

import android.annotation.SuppressLint
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.xiaojiang233.nekoplayer.NekoPlayerApplication

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@SuppressLint("StaticFieldLeak")
object SettingsRepository {
    private val context = NekoPlayerApplication.getAppContext()
    private val dataStore = context.dataStore

    private val LYRICS_FONT_SIZE_KEY = floatPreferencesKey("lyrics_font_size")
    private val LYRICS_FONT_FAMILY_KEY = stringPreferencesKey("lyrics_font_family")
    private val LYRICS_BLUR_INTENSITY_KEY = floatPreferencesKey("lyrics_blur_intensity")
    private val SHOW_PLATFORM_TAG_KEY = booleanPreferencesKey("show_platform_tag")
    private val SEARCH_HISTORY_KEY = stringSetPreferencesKey("search_history")
    private val SEARCH_HISTORY_JSON_KEY = stringPreferencesKey("search_history_json")
    private val VIEW_MODE_KEY = stringPreferencesKey("view_mode")
    private val PLAYBACK_DELAY_KEY = intPreferencesKey("fade_in_duration") // Wait, this key looks duplicated in original file?
    private val FADE_IN_DURATION_KEY = intPreferencesKey("fade_in_duration")
    private val WATCH_SCALE_KEY = floatPreferencesKey("watch_scale")


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

    val searchHistory: Flow<List<String>> = dataStore.data.map { preferences ->
        val jsonString = preferences[SEARCH_HISTORY_JSON_KEY]
        if (jsonString != null) {
            try {
                Json.decodeFromString<List<String>>(jsonString)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            // Migration or fallback
            preferences[SEARCH_HISTORY_KEY]?.toList() ?: emptyList()
        }
    }

    val viewMode: Flow<String> = dataStore.data.map { preferences ->
        preferences[VIEW_MODE_KEY] ?: "List"
    }

    val playbackDelay: Flow<Int> = dataStore.data.map { preferences ->
        preferences[PLAYBACK_DELAY_KEY] ?: 0
    }

    val fadeInDuration: Flow<Int> = dataStore.data.map { preferences ->
        preferences[FADE_IN_DURATION_KEY] ?: 0
    }

    val watchScale: Flow<Float> = dataStore.data.map { preferences ->
        preferences[WATCH_SCALE_KEY] ?: 1.0f
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
            val jsonString = preferences[SEARCH_HISTORY_JSON_KEY]
            val currentList = if (jsonString != null) {
                try {
                    Json.decodeFromString<List<String>>(jsonString)
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                preferences[SEARCH_HISTORY_KEY]?.toList() ?: emptyList()
            }

            // Put new query at the top
            val newList = listOf(query) + currentList.filter { it != query }
            preferences[SEARCH_HISTORY_JSON_KEY] = Json.encodeToString(newList.take(20)) // Limit history
        }
    }

    suspend fun clearSearchHistory() {
        dataStore.edit { preferences ->
            preferences.remove(SEARCH_HISTORY_JSON_KEY)
            preferences.remove(SEARCH_HISTORY_KEY)
        }
    }

    suspend fun updateViewMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[VIEW_MODE_KEY] = mode
        }
    }

    suspend fun setPlaybackDelay(delay: Int) {
        dataStore.edit { preferences ->
            preferences[PLAYBACK_DELAY_KEY] = delay
        }
    }

    suspend fun setFadeInDuration(duration: Int) {
        dataStore.edit { preferences ->
            preferences[FADE_IN_DURATION_KEY] = duration
        }
    }

    suspend fun setWatchScale(scale: Float) {
        dataStore.edit { preferences ->
            preferences[WATCH_SCALE_KEY] = scale
        }
        // Use commit() to ensure synchronous save, avoiding loss if app is killed/restarted immediately
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().putFloat("watch_scale", scale).commit()
    }

    @Serializable
    data class SettingsData(
        val lyricsFontSize: Float,
        val lyricsFontFamily: String,
        val lyricsBlurIntensity: Float,
        val showPlatformTag: Boolean,
        val viewMode: String = "List",
        val playbackDelay: Int = 0,
        val fadeInDuration: Int = 0
    )

    suspend fun getAllSettings(): SettingsData {
        val preferences = dataStore.data.first()
        return SettingsData(
            lyricsFontSize = preferences[LYRICS_FONT_SIZE_KEY] ?: 28f,
            lyricsFontFamily = preferences[LYRICS_FONT_FAMILY_KEY] ?: "Default",
            lyricsBlurIntensity = preferences[LYRICS_BLUR_INTENSITY_KEY] ?: 10f,
            showPlatformTag = preferences[SHOW_PLATFORM_TAG_KEY] ?: true,
            viewMode = preferences[VIEW_MODE_KEY] ?: "List",
            playbackDelay = preferences[PLAYBACK_DELAY_KEY] ?: 0,
            fadeInDuration = preferences[FADE_IN_DURATION_KEY] ?: 0
        )
    }

    suspend fun restoreSettings(settings: SettingsData) {
        dataStore.edit { preferences ->
            preferences[LYRICS_FONT_SIZE_KEY] = settings.lyricsFontSize
            preferences[LYRICS_FONT_FAMILY_KEY] = settings.lyricsFontFamily
            preferences[LYRICS_BLUR_INTENSITY_KEY] = settings.lyricsBlurIntensity
            preferences[SHOW_PLATFORM_TAG_KEY] = settings.showPlatformTag
            preferences[PLAYBACK_DELAY_KEY] = settings.playbackDelay
            preferences[FADE_IN_DURATION_KEY] = settings.fadeInDuration
        }
    }
}
