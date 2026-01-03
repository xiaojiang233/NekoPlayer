package top.xiaojiang233.nekoplayer.viewmodel

import android.os.Environment
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.xiaojiang233.nekoplayer.NekoPlayerApplication
import top.xiaojiang233.nekoplayer.data.model.OnlineSong
import top.xiaojiang233.nekoplayer.data.model.Playlist
import top.xiaojiang233.nekoplayer.data.repository.PlaylistRepository

import top.xiaojiang233.nekoplayer.data.repository.SongRepository
import top.xiaojiang233.nekoplayer.data.repository.SettingsRepository
import java.io.File

@Serializable
data class BackupData(
    val songs: List<OnlineSong>,
    val playlists: List<Playlist>,
    val settings: SettingsRepository.SettingsData
)

class SettingsViewModel : ViewModel() {

    private val songRepository = SongRepository
    private val settingsRepository = SettingsRepository
    private val playlistRepository = PlaylistRepository
    private val context = NekoPlayerApplication.getAppContext()
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    val lyricsFontSize: StateFlow<Float> = settingsRepository.lyricsFontSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 28f)

    val lyricsFontFamily: StateFlow<String> = settingsRepository.lyricsFontFamily
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Default")

    val lyricsBlurIntensity: StateFlow<Float> = settingsRepository.lyricsBlurIntensity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10f)

    val showPlatformTag: StateFlow<Boolean> = settingsRepository.showPlatformTag
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

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

    fun setShowPlatformTag(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowPlatformTag(show)
        }
    }

    fun exportConfiguration() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val songs = songRepository.getLocalSongs()
                val playlists = playlistRepository.getAllPlaylists()
                val settings = settingsRepository.getAllSettings()

                val backupData = BackupData(songs, playlists, settings)
                val jsonString = json.encodeToString(backupData)

                val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "NekoMusic")
                if (!downloadDir.exists()) downloadDir.mkdirs()
                val backupFile = File(downloadDir, "backup.json")
                backupFile.writeText(jsonString)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Configuration exported to ${backupFile.absolutePath}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun importConfiguration() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "NekoMusic")
                val backupFile = File(downloadDir, "backup.json")

                if (!backupFile.exists()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Backup file not found", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val jsonString = backupFile.readText()
                val backupData = json.decodeFromString<BackupData>(jsonString)

                songRepository.saveSongs(backupData.songs)
                playlistRepository.savePlaylists(backupData.playlists)
                settingsRepository.restoreSettings(backupData.settings)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Configuration imported successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
