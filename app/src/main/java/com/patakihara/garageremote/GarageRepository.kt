package com.patakihara.garageremote

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "garages")

class GarageRepository(private val context: Context) {
    private val key = stringPreferencesKey("garages_list")

    val garages: Flow<List<Garage>> = context.dataStore.data.map { prefs ->
        val raw = prefs[key] ?: return@map emptyList()
        try {
            Json.decodeFromString(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun save(garages: List<Garage>) {
        context.dataStore.edit { it[key] = Json.encodeToString(garages) }
    }
}
