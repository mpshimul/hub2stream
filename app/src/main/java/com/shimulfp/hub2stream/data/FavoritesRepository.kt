package com.shimulfp.hub2stream.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shimulfp.hub2stream.models.FavoriteItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

private val Context.favoritesDataStore: DataStore<Preferences> by preferencesDataStore(name = "favorites")

class FavoritesRepository(private val context: Context) {

    companion object {
        private val KEY_ITEMS = stringPreferencesKey("items")
        private const val MAX_ITEMS = 50
    }

    val items: Flow<List<FavoriteItem>> = context.favoritesDataStore.data
        .map { preferences ->
            val json = preferences[KEY_ITEMS] ?: return@map emptyList()
            try {
                Json.decodeFromString<List<FavoriteItem>>(json)
                    .sortedByDescending { it.timestamp }
                    .take(MAX_ITEMS)
            } catch (e: Exception) {
                emptyList()
            }
        }

    suspend fun addOrUpdateItem(item: FavoriteItem) {
        context.favoritesDataStore.edit { preferences ->
            val existingJson = preferences[KEY_ITEMS] ?: "[]"
            val currentList = try {
                Json.decodeFromString<List<FavoriteItem>>(existingJson).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }

            // Check if item already exists
            val existingIndex = currentList.indexOfFirst {
                it.contentId == item.contentId
            }

            if (existingIndex >= 0) {
                // Update timestamp and move to beginning
                currentList[existingIndex] = item.copy(timestamp = System.currentTimeMillis())
                currentList.removeAt(existingIndex)
                currentList.add(0, item)
            } else {
                // Insert at beginning
                currentList.add(0, item)
            }

            // Trim to max size
            while (currentList.size > MAX_ITEMS) {
                currentList.removeAt(currentList.lastIndex)
            }

            preferences[KEY_ITEMS] = Json.encodeToString(currentList)
        }
    }

    suspend fun removeItem(contentId: String) {
        context.favoritesDataStore.edit { preferences ->
            val existingJson = preferences[KEY_ITEMS] ?: "[]"
            val currentList = try {
                Json.decodeFromString<List<FavoriteItem>>(existingJson).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            val newList = currentList.filterNot { it.contentId == contentId }
            preferences[KEY_ITEMS] = Json.encodeToString(newList)
        }
    }

    suspend fun isFavorite(contentId: String): Boolean {
        val json = context.favoritesDataStore.data.map { preferences ->
            preferences[KEY_ITEMS]
        }
        val currentJson = json.toString() // Get the first value
        val currentList = try {
            Json.decodeFromString<List<FavoriteItem>>(currentJson.removeSurrounding("\""))
        } catch (e: Exception) {
            emptyList()
        }
        return currentList.any { it.contentId == contentId }
    }

    suspend fun toggleItem(item: FavoriteItem): Boolean {
        val isFav = isFavorite(item.contentId)
        if (isFav) {
            removeItem(item.contentId)
            return false
        } else {
            addOrUpdateItem(item)
            return true
        }
    }

    suspend fun clearAll() {
        context.favoritesDataStore.edit { preferences ->
            preferences[KEY_ITEMS] = "[]"
        }
    }
}