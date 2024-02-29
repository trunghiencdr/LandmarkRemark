package com.example.landmarkremark.utils

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import com.example.landmarkremark.R
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.viewport.DEFAULT_FOLLOW_PUCK_VIEWPORT_STATE_ZOOM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn


fun showToast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_LONG)
}

fun showAlert(context: Context, title: String, message: String) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle(title)
    builder.setMessage(message)
    builder.setPositiveButton("OK") { dialog, _ ->
        dialog.dismiss()
    }
    val dialog: AlertDialog = builder.create()
    dialog.show()
}

fun showInputDialog(
    context: Context,
    viewGroup: ViewGroup,
    title: String,
    onSubmit: (input: String) -> Unit
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle(title)
    val viewInflated: View = LayoutInflater.from(context)
        .inflate(R.layout.note_input, viewGroup, false)
    val note = viewInflated.findViewById<EditText>(R.id.input_note_title)
    builder.apply {
        setView(viewInflated)
        setPositiveButton(
            android.R.string.ok
        ) { dialog, _ ->
            val input = note.text.toString()
            if (input.isEmpty()) {
                showAlert(
                    context, context.resources.getString(R.string.error),
                    context.resources.getString(R.string.note_required)
                )
            } else {
                dialog.dismiss()
                onSubmit(input)
            }

        }
        setNegativeButton(
            android.R.string.cancel
        ) { dialog, _ -> dialog.cancel() }
        show()
    }

}

fun SearchView.getQueryTextChangeStateFlow(): StateFlow<String> {
    val query = MutableStateFlow("")
    setOnQueryTextListener(object : SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(query: String?): Boolean {
            return true
        }

        override fun onQueryTextChange(newText: String): Boolean {
            query.value = newText
            return true
        }
    })
    return query
}

fun MapView.focusPoint(it: Point, zoom: Double = DEFAULT_FOLLOW_PUCK_VIEWPORT_STATE_ZOOM) {
    this.mapboxMap.setCamera(CameraOptions.Builder().zoom(zoom).center(it).build())
    this.gestures.focalPoint = this.mapboxMap.pixelForCoordinate(it)
}

fun QueryDocumentSnapshot.toString(field: String): String =
    this.getString(field) ?: Constants.DEFAULT_STRING_VALUE

fun QueryDocumentSnapshot.toDouble(field: String): Double =
    this.getDouble(field) ?: Constants.DEFAULT_DOUBLE_VALUE

suspend fun StateFlow<String>.instantSearch(delay: Long = 300) =
    this.debounce(delay)
        .filter { query -> query.isNotEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            FirebaseHelper.searchNoteByNameOfUserAndNoteDescription(query)
                .catch { emitAll(flowOf(arrayListOf())) }
        }
        .flowOn(Dispatchers.Default)  // Perform search in the background


