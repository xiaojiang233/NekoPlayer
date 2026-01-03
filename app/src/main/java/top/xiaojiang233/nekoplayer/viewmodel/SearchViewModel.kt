package top.xiaojiang233.nekoplayer.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import top.xiaojiang233.nekoplayer.data.model.OnlineSong
import top.xiaojiang233.nekoplayer.data.network.RetrofitInstance
import top.xiaojiang233.nekoplayer.data.repository.SongRepository
import top.xiaojiang233.nekoplayer.data.repository.SettingsRepository

data class SongGroup(
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val songs: List<OnlineSong>
)

class SearchViewModel : ViewModel() {

    private val songRepository = SongRepository
    private val settingsRepository = SettingsRepository

    val downloadState = songRepository.downloadState

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedPlatforms = MutableStateFlow<Set<String>>(emptySet())
    val selectedPlatforms: StateFlow<Set<String>> = _selectedPlatforms

    val searchHistory: StateFlow<List<String>> = settingsRepository.searchHistory
        .map { it.toList().reversed() } // Show recent first
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchResults = MutableStateFlow<List<OnlineSong>>(emptyList())
    val searchResults: StateFlow<List<OnlineSong>> = _searchResults

    val groupedSearchResults: StateFlow<List<SongGroup>> = combine(_searchResults, _selectedPlatforms) { songs, platforms ->
        val filteredSongs = if (platforms.isEmpty()) songs else songs.filter { it.platform.lowercase() in platforms }
        filteredSongs.groupBy { it.title to it.artist }
            .map { (key, groupSongs) ->
                SongGroup(
                    title = key.first,
                    artist = key.second,
                    coverUrl = groupSongs.firstOrNull()?.coverUrl,
                    songs = groupSongs.distinctBy { it.platform }
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var currentPage = 1
    private var isLastPage = false

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun togglePlatform(platform: String) {
        val current = _selectedPlatforms.value
        if (current.contains(platform)) {
            _selectedPlatforms.value = current - platform
        } else {
            _selectedPlatforms.value = current + platform
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            settingsRepository.clearSearchHistory()
        }
    }

    fun searchSongs(reset: Boolean = true) {
        if (_searchQuery.value.isBlank()) return
        if (isLoading.value) return
        if (!reset && isLastPage) return

        viewModelScope.launch {
            if (reset) {
                settingsRepository.addSearchHistory(_searchQuery.value)
            }
            _isLoading.value = true
            if (reset) {
                currentPage = 1
                isLastPage = false
                _searchResults.value = emptyList()
            }

            try {
                val response = RetrofitInstance.musicApiService.searchSongs(
                    keyword = _searchQuery.value,
                    page = currentPage
                )
                if (response.code == 200) {
                    Log.d("SearchViewModel", "Search successful. Found ${response.data.results.size} songs.")
                    val newSongs = response.data.results
                    if (newSongs.isEmpty()) {
                        isLastPage = true
                    } else {
                        _searchResults.value += newSongs
                        currentPage++
                    }
                } else {
                    Log.e("SearchViewModel", "API Error: Code = ${response.code}, Message = ${response.message}")
                    if (reset) _searchResults.value = emptyList()
                }
            } catch (e: Exception) {
                if (reset) _searchResults.value = emptyList()
                Log.e("SearchViewModel", "Search failed with exception", e)
            }
            _isLoading.value = false
        }
    }

    fun loadMore() {
        searchSongs(reset = false)
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
