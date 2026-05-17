package com.patakihara.garageremote

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Garage(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phoneNumber: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
