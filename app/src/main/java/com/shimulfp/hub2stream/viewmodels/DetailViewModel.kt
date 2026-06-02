package com.shimulfp.hub2stream.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shimulfp.hub2stream.data.MovieRepository
import com.shimulfp.hub2stream.extractor.models.Movie
import com.shimulfp.hub2stream.extractor.models.Series
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class DetailUiState {
    object Loading : DetailUiState()
    data class MovieSuccess(val movie: Movie) : DetailUiState()
    data class SeriesSuccess(val series: Series) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}

class DetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = MovieRepository(application)
    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState

    fun loadMovie(slug: String) {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            android.util.Log.d("DetailViewModel", "=== loadMovie START === slug: $slug")
            try {
                val movie = repo.getMovieDetails(slug)
                android.util.Log.d("DetailViewModel", "Movie loaded - title: ${movie?.title}, streamUrl: '${movie?.streamUrl}'")
                if (movie != null) {
                    android.util.Log.d("DetailViewModel", "✅ Setting MovieSuccess state")
                    _uiState.value = DetailUiState.MovieSuccess(movie)
                    android.util.Log.d("DetailViewModel", "MovieSuccess state set - streamUrl.length=${movie.streamUrl.length}")
                } else {
                    android.util.Log.e("DetailViewModel", "❌ Movie is null, setting Error state")
                    _uiState.value = DetailUiState.Error("Movie not found")
                    android.util.Log.e("DetailViewModel", "Movie not found for slug: $slug")
                }
            } catch (e: Exception) {
                android.util.Log.e("DetailViewModel", "❌ Exception loading movie", e)
                _uiState.value = DetailUiState.Error(e.message ?: "Unknown error")
            }
            android.util.Log.d("DetailViewModel", "=== loadMovie END ===")
        }
    }

    fun loadSeries(slug: String) {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            try {
                val series = repo.getSeriesDetails(slug)
                if (series != null) _uiState.value = DetailUiState.SeriesSuccess(series)
                else _uiState.value = DetailUiState.Error("Series not found")
            } catch (e: Exception) { _uiState.value = DetailUiState.Error(e.message ?: "Unknown error") }
        }
    }
}