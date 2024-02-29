package com.example.landmarkremark.utils

import com.example.landmarkremark.R
import com.example.landmarkremark.models.Note
import com.example.landmarkremark.models.User
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.mapbox.geojson.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object FirebaseHelper {
    private val db = Firebase.firestore
    private const val USER_COLLECTIONS = "users"
    private const val NOTES_COLLECTIONS = "notes"
    private const val USERNAME = "username"
    private const val PASSWORD = "password"
    private const val NAME = "name"
    private const val NOTE_ID = "id"
    private const val NOTE_DESCRIPTION = "description"
    private const val NOTE_LONGITUDE = "longitude"
    private const val NOTE_LATITUDE = "latitude"
    private const val NOTE_USER_ID = "user_id"
    private const val NOTE_NAME_OF_USER = "name_of_user"
    private val userCollection = db.collection(USER_COLLECTIONS)
    private val notesCollection = db.collection(NOTES_COLLECTIONS)

    suspend fun isUserExist(id: String, result: (Boolean?) -> Unit) {
        try {
            val existed = db.collection(USER_COLLECTIONS).document(id).get().await().exists()
            result(existed)
        } catch (e: Exception) {
            result(null)
        }
    }

    suspend fun login(username: String, password: String): User? {
        return withContext(Dispatchers.IO) {
            val data = userCollection.whereEqualTo(USERNAME, username)
                .whereEqualTo(PASSWORD, password).get().await().documents.firstOrNull()
            data?.let {
                val id = it.id
                val username = it.getString(USERNAME) ?: ""
                val password = it.getString(PASSWORD) ?: ""
                val name = it.getString(NAME) ?: ""
                User(id, name, username, password)
            }
        }
    }

    suspend fun register(
        name: String,
        username: String,
        password: String,
        onSuccess: (userId: String) -> Unit,
        onFailed: (errorMessage: String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            val user = hashMapOf(
                NAME to name,
                USERNAME to username,
                PASSWORD to password
            )
            try {
                val result = userCollection
                    .whereEqualTo(USERNAME, username)
                    .get()
                    .await()
                if (result.isEmpty) { // when username have yet in db
                    val newUser = userCollection
                        .add(user)
                        .await()
                    onSuccess(newUser.id)
                } else { // when username has existed
                    onFailed(StringProvider.getString(R.string.username_existed))
                }
            } catch (e: Exception) {
                onFailed(e.localizedMessage)
            }
        }
    }

    suspend fun addNote(
        point: Point, userId: String, nameOfUser: String, noteDescription: String
    ) {
        val convertNote = hashMapOf(
            NOTE_USER_ID to userId,
            NOTE_NAME_OF_USER to nameOfUser,
            NOTE_LONGITUDE to point.longitude(),
            NOTE_LATITUDE to point.latitude(),
            NOTE_DESCRIPTION to noteDescription,
        )
        withContext(Dispatchers.IO) {
            notesCollection.add(convertNote)
        }
    }

    suspend fun removeNote(noteId: String) {
        withContext(Dispatchers.IO) {
            try {
                notesCollection.document(noteId).delete()
            } catch (e: Exception) {
            }
        }
    }

    suspend fun getNotes() = flow {
        try {
            val notes = notesCollection.get().await()
            notes.forEach { note ->
                emit(note.toNote())
            }
        } catch (e: Exception) {
            listOf<Note>()
        }
    }

    private fun QueryDocumentSnapshot.toNote() = Note(
        this.id,
        this.toDouble(NOTE_LONGITUDE),
        this.toDouble(NOTE_LATITUDE),
        this.toString(NOTE_DESCRIPTION),
        this.toString(NOTE_USER_ID),
        this.toString(NOTE_NAME_OF_USER)
    )


    fun onChange(
        onAdded: (Note) -> (Unit),
        onDeleted: (String) -> Unit,
        onError: ((FirebaseFirestoreException) -> Unit)? = null
    ) {
       notesCollection
           .addSnapshotListener { snapshots, e ->
            if (e != null) {
                onError?.let { it.invoke(e) }
                return@addSnapshotListener
            }

            for (dc in snapshots!!.documentChanges) {
                val document = dc.document
                when (dc.type) {
                    DocumentChange.Type.ADDED -> {
                        onAdded(document.toNote())
                    }

                    DocumentChange.Type.REMOVED -> {
                        onDeleted(document.id)
                    }

                    else -> {}
                }
            }
        }
    }

    suspend fun searchNoteByNameOfUserAndNoteDescription(query: String): Flow<ArrayList<Note>> =
        flow {
            val noteRefs = db.collection(NOTES_COLLECTIONS).get().await()
            val filtered = noteRefs.filter { noteRef ->
                noteRef.getString(NOTE_NAME_OF_USER)?.contains(query) == true || noteRef.getString(
                    NOTE_DESCRIPTION
                )?.contains(query) == true
            }
            emit(
                ArrayList(filtered.map {
                    it.toNote()
                })
            )
        }
}