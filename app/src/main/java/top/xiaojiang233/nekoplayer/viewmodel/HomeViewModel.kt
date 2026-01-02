package top.xiaojiang233.nekoplayer.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import top.xiaojiang233.nekoplayer.data.model.OnlineSong
import top.xiaojiang233.nekoplayer.data.repository.SongRepository

class HomeViewModel : ViewModel() {

    private val songRepository = SongRepository

    private val _localSongs = MutableStateFlow<List<OnlineSong>>(emptyList())
    val localSongs: StateFlow<List<OnlineSong>> = _localSongs

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
}
