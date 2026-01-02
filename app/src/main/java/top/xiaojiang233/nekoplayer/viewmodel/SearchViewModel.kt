package top.xiaojiang233.nekoplayer.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import top.xiaojiang233.nekoplayer.data.model.OnlineSong
import top.xiaojiang233.nekoplayer.data.network.RetrofitInstance
import top.xiaojiang233.nekoplayer.data.repository.SongRepository

class SearchViewModel : ViewModel() {

    private val songRepository = SongRepository

    val downloadState = songRepository.downloadState

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResults = MutableStateFlow<List<OnlineSong>>(emptyList())
    val searchResults: StateFlow<List<OnlineSong>> = _searchResults

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun searchSongs() {
        if (_searchQuery.value.isBlank()) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = RetrofitInstance.musicApiService.searchSongs(keyword = _searchQuery.value)
                if (response.code == 200) {
                    Log.d("SearchViewModel", "Search successful. Found ${response.data.results.size} songs.")
                    _searchResults.value = response.data.results
                } else {
                    Log.e("SearchViewModel", "API Error: Code = ${response.code}, Message = ${response.message}")
                    _searchResults.value = emptyList()
                }
            } catch (e: Exception) {
                _searchResults.value = emptyList()
                Log.e("SearchViewModel", "Search failed with exception", e)
            }
            _isLoading.value = false
        }
    }

    fun downloadSong(song: OnlineSong) {
        viewModelScope.launch {
            songRepository.downloadSong(song)
        }
    }

    fun deleteSong(song: OnlineSong) {
        viewModelScope.launch {
            songRepository.deleteSong(song)
        }
    }
}
