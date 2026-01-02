package top.xiaojiang233.nekoplayer.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import top.xiaojiang233.nekoplayer.NekoPlayerApplication

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object SettingsRepository {
    private val context = NekoPlayerApplication.getAppContext()
    private val dataStore = context.dataStore

    private val LYRICS_FONT_SIZE_KEY = floatPreferencesKey("lyrics_font_size")
    private val LYRICS_FONT_FAMILY_KEY = stringPreferencesKey("lyrics_font_family")
    private val LYRICS_BLUR_INTENSITY_KEY = floatPreferencesKey("lyrics_blur_intensity")

    val lyricsFontSize: Flow<Float> = dataStore.data.map { preferences ->
        preferences[LYRICS_FONT_SIZE_KEY] ?: 28f
    }

    val lyricsFontFamily: Flow<String> = dataStore.data.map { preferences ->
        preferences[LYRICS_FONT_FAMILY_KEY] ?: "Default"
    }

    val lyricsBlurIntensity: Flow<Float> = dataStore.data.map { preferences ->
        preferences[LYRICS_BLUR_INTENSITY_KEY] ?: 10f
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
}
