package com.patakihara.garageremote

import android.content.Context

const val MAX_TILES = 4
private const val PREFS = "tile_prefs"

data class TileAssignment(val garageId: String, val name: String, val phoneNumber: String)

fun getTileAssignment(context: Context, slot: Int): TileAssignment? {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val id = p.getString("${slot}_id", null) ?: return null
    return TileAssignment(
        garageId = id,
        name = p.getString("${slot}_name", "") ?: "",
        phoneNumber = p.getString("${slot}_phone", "") ?: "",
    )
}

fun setTileAssignment(context: Context, slot: Int, garage: Garage?) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
        if (garage == null) {
            remove("${slot}_id"); remove("${slot}_name"); remove("${slot}_phone")
        } else {
            putString("${slot}_id", garage.id)
            putString("${slot}_name", garage.name)
            putString("${slot}_phone", garage.phoneNumber)
        }
        apply()
    }
}

fun getGarageSlot(context: Context, garageId: String): Int? =
    (1..MAX_TILES).firstOrNull {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("${it}_id", null) == garageId
    }

fun nextAvailableSlot(context: Context): Int? =
    (1..MAX_TILES).firstOrNull {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("${it}_id", null) == null
    }

fun clearTileForGarage(context: Context, garageId: String) {
    getGarageSlot(context, garageId)?.let { setTileAssignment(context, it, null) }
}
