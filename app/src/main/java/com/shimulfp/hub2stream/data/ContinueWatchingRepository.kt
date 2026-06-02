package com.shimulfp.hub2stream.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shimulfp.hub2stream.models.ContinueWatchingItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "continue_watching")

class ContinueWatchingRepository(private val context: Context) {

    companion object {
        private val KEY_ITEMS = stringPreferencesKey("items")
        private const val MAX_ITEMS = 20
    }

    val items: Flow<List<ContinueWatchingItem>> = context.dataStore.data
        .map { preferences ->
            val json = preferences[KEY_ITEMS] ?: return@map emptyList()
            try {
                Json.decodeFromString<List<ContinueWatchingItem>>(json)
                    .sortedByDescending { it.timestamp }
                    .take(MAX_ITEMS)
            } catch (e: Exception) {
                emptyList()
            }
        }

    suspend fun addOrUpdateItem(item: ContinueWatchingItem) {
        context.dataStore.edit { preferences ->
            val existingJson = preferences[KEY_ITEMS] ?: "[]"
            val currentList = try {
                Json.decodeFromString<List<ContinueWatchingItem>>(existingJson).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            // For series, remove only the EXACT same episode (not all episodes of the series)
            // For movies, remove only the exact match
            val indicesToRemove = currentList.mapIndexedNotNull { index, existing ->
                when {
                    item.type == "series" && existing.slug == item.slug && existing.type == "series" &&
                        existing.seasonNumber == item.seasonNumber && existing.episodeNumber == item.episodeNumber -> index
                    item.type == "movie" && existing.slug == item.slug && existing.type == "movie" -> index
                    else -> null
                }
            }
            // Remove in reverse order to avoid index shifting issues
            indicesToRemove.sortedDescending().forEach { index ->
                currentList.removeAt(index)
            }
            // Insert at the beginning
            currentList.add(0, item)
            // Trim to max size
            while (currentList.size > MAX_ITEMS) {
                currentList.removeAt(currentList.lastIndex)
            }
            preferences[KEY_ITEMS] = Json.encodeToString(currentList)
        }
    }

    suspend fun removeItem(slug: String, type: String, seasonNumber: Int = 0, episodeNumber: Int = 0) {
        context.dataStore.edit { preferences ->
            val existingJson = preferences[KEY_ITEMS] ?: "[]"
            val currentList = try {
                Json.decodeFromString<List<ContinueWatchingItem>>(existingJson).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            // For series with no specific episode, remove all episodes of that series
            // Otherwise, remove only the specific episode/movie
            val newList = currentList.filterNot { item ->
                when {
                    type == "series" && seasonNumber == 0 && episodeNumber == 0 ->
                        item.slug == slug && item.type == "series"
                    type == "series" ->
                        item.slug == slug && item.type == "series" &&
                        item.seasonNumber == seasonNumber && item.episodeNumber == episodeNumber
                    type == "movie" ->
                        item.slug == slug && item.type == "movie"
                    else -> false
                }
            }
            preferences[KEY_ITEMS] = Json.encodeToString(newList)
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences[KEY_ITEMS] = "[]"
        }
    }
}