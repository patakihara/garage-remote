package com.patakihara.garageremote

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class GarageViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = GarageRepository(app)

    val garages = repo.garages.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    fun add(garage: Garage) = viewModelScope.launch {
        repo.save(garages.value + garage)
    }

    fun update(garage: Garage) = viewModelScope.launch {
        repo.save(garages.value.map { if (it.id == garage.id) garage else it })
    }

    fun delete(garage: Garage) = viewModelScope.launch {
        repo.save(garages.value.filter { it.id != garage.id })
    }

    fun exportJson(): String = Json.encodeToString(garages.value)

    fun importJson(json: String) = viewModelScope.launch {
        try {
            repo.save(Json.decodeFromString(json))
        } catch (_: Exception) {}
    }
}
