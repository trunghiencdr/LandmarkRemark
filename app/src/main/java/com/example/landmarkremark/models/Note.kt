package com.example.landmarkremark.models

data class Note(
    val id: String,
    val long: Double,
    val lat: Double,
    val description: String,
    val userId: String,
    val name: String
)
