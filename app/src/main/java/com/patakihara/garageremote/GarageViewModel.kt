package com.patakihara.garageremote

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class GarageViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = GarageRepository(app)
    private val locationRepo = LocationRepository(app)

    private val locationPermGranted = MutableStateFlow(false)

    val currentLocation = locationPermGranted
        .flatMapLatest { granted -> if (granted) locationRepo.locationUpdates() else emptyFlow() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val rawGarages = repo.garages.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    // Sorted nearest-first when location is available; original order otherwise.
    val garages = combine(rawGarages, currentLocation) { list, loc ->
        if (loc == null) list
        else list.sortedBy { distanceTo(it, loc) ?: Float.MAX_VALUE }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setLocationPermission(granted: Boolean) {
        locationPermGranted.value = granted
    }

    fun distanceTo(garage: Garage, location: Location?): Float? {
        val lat = garage.latitude ?: return null
        val lng = garage.longitude ?: return null
        location ?: return null
        return FloatArray(1).also {
            Location.distanceBetween(location.latitude, location.longitude, lat, lng, it)
        }[0]
    }

    fun add(garage: Garage) = viewModelScope.launch { repo.save(rawGarages.value + garage) }

    fun update(garage: Garage) = viewModelScope.launch {
        repo.save(rawGarages.value.map { if (it.id == garage.id) garage else it })
    }

    fun delete(garage: Garage) = viewModelScope.launch {
        repo.save(rawGarages.value.filter { it.id != garage.id })
    }

    fun exportJson(): String = Json.encodeToString(rawGarages.value)

    fun importJson(json: String) = viewModelScope.launch {
        try { repo.save(Json.decodeFromString(json)) } catch (_: Exception) {}
    }
}
