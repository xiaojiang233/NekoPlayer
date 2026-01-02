package top.xiaojiang233.nekoplayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import top.xiaojiang233.nekoplayer.data.repository.SongRepository
import top.xiaojiang233.nekoplayer.data.repository.SettingsRepository

class SettingsViewModel : ViewModel() {

    private val songRepository = SongRepository
    private val settingsRepository = SettingsRepository

    val lyricsFontSize: StateFlow<Float> = settingsRepository.lyricsFontSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 28f)

    val lyricsFontFamily: StateFlow<String> = settingsRepository.lyricsFontFamily
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Default")

    val lyricsBlurIntensity: StateFlow<Float> = settingsRepository.lyricsBlurIntensity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10f)

    fun clearCache() {
        viewModelScope.launch {
            songRepository.clearCache()
        }
    }

    fun setLyricsFontSize(size: Float) {
        viewModelScope.launch {
            settingsRepository.setLyricsFontSize(size)
        }
    }

    fun setLyricsFontFamily(family: String) {
        viewModelScope.launch {
            settingsRepository.setLyricsFontFamily(family)
        }
    }

    fun setLyricsBlurIntensity(intensity: Float) {
        viewModelScope.launch {
            settingsRepository.setLyricsBlurIntensity(intensity)
        }
    }
}
