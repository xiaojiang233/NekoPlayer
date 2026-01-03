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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HomeViewModel : ViewModel() {

    private val songRepository = SongRepository
    private val playlistRepository = PlaylistRepository
    private val settingsRepository = SettingsRepository

    private val _localSongs = MutableStateFlow<List<OnlineSong>>(emptyList())
    val localSongs: StateFlow<List<OnlineSong>> = _localSongs

    val playlists: StateFlow<List<Playlist>> = playlistRepository.playlists

    val viewMode: StateFlow<ViewMode> = settingsRepository.viewMode
        .map { if (it == "Grid") ViewMode.Grid else ViewMode.List }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ViewMode.List)

    enum class ViewMode {
        List, Grid
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
            uris.forEach { songRepository.addLocalSong(it) }
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
}

