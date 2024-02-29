package com.example.landmarkremark

data class User(val id: String, val name: String, val username: String, val password: String)
fun User.firstLetter() = this.name.get(0).toString()
