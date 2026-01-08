package top.xiaojiang233.nekoplayer.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import top.xiaojiang233.nekoplayer.data.model.OnlineSong
import top.xiaojiang233.nekoplayer.data.model.Playlist
import top.xiaojiang233.nekoplayer.data.repository.PlaylistRepository
import top.xiaojiang233.nekoplayer.data.repository.SongRepository
import top.xiaojiang233.nekoplayer.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HomeViewModel : ViewModel() {

    private val songRepository = SongRepository
    private val playlistRepository = PlaylistRepository
    private val settingsRepository = SettingsRepository

    private val _localSongs = MutableStateFlow<List<OnlineSong>>(emptyList())
    val localSongs: StateFlow<List<OnlineSong>> = _localSongs

    private val _showLocalMusicSelection = MutableStateFlow(false)
    val showLocalMusicSelection: StateFlow<Boolean> = _showLocalMusicSelection

    private val _showWatchScaleSelection = MutableStateFlow(false)
    val showWatchScaleSelection: StateFlow<Boolean> = _showWatchScaleSelection

    private val _availableLocalSongs = MutableStateFlow<List<OnlineSong>>(emptyList())
    val availableLocalSongs: StateFlow<List<OnlineSong>> = _availableLocalSongs

    val playlists: StateFlow<List<Playlist>> = playlistRepository.playlists

    val viewMode: StateFlow<ViewMode> = settingsRepository.viewMode
        .map { if (it == "Grid") ViewMode.Grid else ViewMode.List }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ViewMode.List)

    enum class ViewMode {
        List, Grid
    }

    init {

        // Monitor download state changes and refresh based on count of downloaded items logic to avoid spam
        viewModelScope.launch {
            songRepository.downloadState
                .map { it.values.count { state -> state is SongRepository.DownloadState.Downloaded } }
                .distinctUntilChanged()
                .collect {
                    loadLocalSongs()
                }
        }
    }

    fun toggleViewMode() {
        val newMode = if (viewMode.value == ViewMode.List) "Grid" else "List"
        viewModelScope.launch {
            settingsRepository.updateViewMode(newMode)
        }
    }

    fun loadLocalSongs() {
        viewModelScope.launch {
            _localSongs.value = songRepository.getLocalSongs()
        }
    }

    fun addLocalSong(uri: Uri) {
        viewModelScope.launch {
            songRepository.addLocalSong(uri)
            loadLocalSongs() // Refresh the list
        }
    }

    fun deleteSong(song: OnlineSong) {
        viewModelScope.launch {
            songRepository.deleteSong(song)
            loadLocalSongs() // Refresh the list
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name)
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlist)
        }
    }

    fun renamePlaylist(playlist: Playlist, newName: String) {
        viewModelScope.launch {
            playlistRepository.renamePlaylist(playlist, newName)
        }
    }

    fun addSongsToPlaylist(playlist: Playlist, songs: List<OnlineSong>) {
        viewModelScope.launch {
            playlistRepository.addSongsToPlaylist(playlist, songs)
        }
    }

    fun removeSongFromPlaylist(playlist: Playlist, songId: String) {
        viewModelScope.launch {
            playlistRepository.removeSongFromPlaylist(playlist, songId)
        }
    }

    fun importSongs(uris: List<Uri>) {
        viewModelScope.launch {
            uris.forEach {
                songRepository.addLocalSong(it)
            }
            loadLocalSongs()
        }
    }

    fun updateLocalSongsOrder(songs: List<OnlineSong>) {
        viewModelScope.launch {
            songRepository.updateLocalSongsOrder(songs)
            _localSongs.value = songs
        }
    }

    fun updatePlaylistSongsOrder(playlist: Playlist, songs: List<OnlineSong>) {
        viewModelScope.launch {
            playlistRepository.updateSongsOrder(playlist, songs.map { it.id })
        }
    }

    fun updatePlaylistsOrder(playlists: List<Playlist>) {
        viewModelScope.launch {
            playlistRepository.updatePlaylistsOrder(playlists)
        }
    }

    fun showLocalMusicSelection() {
        viewModelScope.launch {
            _availableLocalSongs.value = songRepository.getAllMediaStoreMusic()
            _showLocalMusicSelection.value = true
        }
    }

    fun showWatchScaleSelection() {
        _showWatchScaleSelection.value = true
    }

    fun hideWatchScaleSelection() {
        _showWatchScaleSelection.value = false
    }

    fun setWatchScale(scale: Float) {
        // Must save synchronously because the app might restart immediately after this call
        val context = top.xiaojiang233.nekoplayer.NekoPlayerApplication.getAppContext()
        context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putFloat("watch_scale", scale)
            .putBoolean("is_scale_set", true)
            .commit()

        viewModelScope.launch {
            settingsRepository.setWatchScale(scale)
        }
    }

    fun hideLocalMusicSelection() {
        if (_showLocalMusicSelection.value) {
            viewModelScope.launch {
                // Check if it's first launch
                val context = top.xiaojiang233.nekoplayer.NekoPlayerApplication.getAppContext()
                val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                val isFirstLaunch = prefs.getBoolean("is_first_launch", true)

                if (isFirstLaunch) {
                    // User dismissed without selecting anything - hide all songs
                    val allSongs = songRepository.getAllMediaStoreMusic()
                    songRepository.setHiddenSongs(allSongs.map { it.id }.toSet())

                    // Mark as non-first launch
                    prefs.edit().putBoolean("is_first_launch", false).apply()
                }
            }
        }
        _showLocalMusicSelection.value = false
    }

    fun addLocalSongs(selectedSongs: List<OnlineSong>) {
        viewModelScope.launch {
            // Get all MediaStore songs
            val allSongs = songRepository.getAllMediaStoreMusic()

            // Songs NOT selected should be hidden
            val unselectedSongIds = allSongs
                .filter { it !in selectedSongs }
                .map { it.id }
                .toSet()

            // Update hidden songs list
            songRepository.setHiddenSongs(unselectedSongIds)

            // Now load local songs (which will filter out hidden ones)
            loadLocalSongs()
            _showLocalMusicSelection.value = false

            // Mark as non-first launch now that user has made a choice
            val context = top.xiaojiang233.nekoplayer.NekoPlayerApplication.getAppContext()
            val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putBoolean("is_first_launch", false).apply()
        }
    }
}
