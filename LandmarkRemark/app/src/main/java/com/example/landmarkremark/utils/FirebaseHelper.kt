package com.example.landmarkremark.utils

import android.text.BoringLayout
import com.example.landmarkremark.User
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object FirebaseHelper {
    private val db = Firebase.firestore
    private const val USER_COLLECTIONS = "users"
    private const val USERNAME = "username"
    private const val PASSWORD = "password"
    private const val NAME = "name"
    private fun isUserExist(id: String): Boolean {
        return db.collection(USER_COLLECTIONS).document(id)
            .get()
            .result.exists()
    }

    suspend fun login(username: String, password: String): User? {
        return withContext(Dispatchers.IO) {
            val data = db.collection(USER_COLLECTIONS)
                .whereEqualTo(USERNAME, username)
                .whereEqualTo(PASSWORD, password)
                .get().await().documents.firstOrNull()
            data?.let {
                val id = it.id
                val username = it.getString(USERNAME) ?: ""
                val password = it.getString(PASSWORD) ?: ""
                val name = it.getString(NAME) ?: ""
                User(id, name, username, password)
            }
        }
    }

}