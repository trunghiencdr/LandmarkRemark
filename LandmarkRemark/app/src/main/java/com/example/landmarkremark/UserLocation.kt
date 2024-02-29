package com.example.landmarkremark


import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.SearchView.OnCloseListener
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.landmarkremark.databinding.ActivityUserLocationBinding
import com.example.landmarkremark.databinding.ItemCalloutViewBinding
import com.example.landmarkremark.utils.FirebaseHelper
import com.example.landmarkremark.utils.LocationPermissionHelper
import com.example.landmarkremark.utils.SharedReferencesHelper
import com.example.landmarkremark.utils.getQueryTextChangeStateFlow
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.dsl.generated.interpolate
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.LocationComponentConstants
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorBearingChangedListener
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.viewannotation.ViewAnnotationManager
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference


/**
 * Tracks the user location on screen, simulates a navigation session.
 */
class UserLocation : AppCompatActivity(), OnMapClickListener {
    private val TAG = this@UserLocation::class.java.simpleName
    private lateinit var locationPermissionHelper: LocationPermissionHelper
    private lateinit var viewAnnotationManager: ViewAnnotationManager
    private val viewAnnotationViews = mutableListOf<View>()
    private lateinit var mapboxMap: MapboxMap
    private lateinit var mapView: MapView
    private lateinit var binding: ActivityUserLocationBinding
    private var lastStyleUri = Style.MAPBOX_STREETS
    private lateinit var currentLocation: Point
    private var user: User? = null
    private var searchResult = MutableLiveData(listOf<String>())
    private lateinit var adapter: CustomAdapter

    companion object {
        const val SELECTED_ADD_COEF_PX = 25
        const val STARTUP_TEXT = "Click on a map to add a view annotation."
        const val ADD_VIEW_ANNOTATION_TEXT = "Add view annotations to re-frame map camera"
    }

    private val onIndicatorBearingChangedListener = OnIndicatorBearingChangedListener {
        mapView.mapboxMap.setCamera(CameraOptions.Builder().bearing(it).build())
    }

    private val onIndicatorPositionChangedListener = OnIndicatorPositionChangedListener {
        currentLocation = it
        mapView.mapboxMap.setCamera(CameraOptions.Builder().center(it).build())
        mapView.gestures.focalPoint = mapView.mapboxMap.pixelForCoordinate(it)
//        Toast.makeText(this, "location changed : ${it.latitude()} -  ${it.longitude()}", Toast.LENGTH_SHORT).show()
    }

    private val onMoveListener = object : OnMoveListener {
        override fun onMoveBegin(detector: MoveGestureDetector) {
            onCameraTrackingDismissed()
        }

        override fun onMove(detector: MoveGestureDetector): Boolean {

            return false
        }

        override fun onMoveEnd(detector: MoveGestureDetector) {}
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserLocationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        checkUserSession()
        mapView = binding.mapView
        mapboxMap = binding.mapView.getMapboxMap()
        binding.searchResult.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        adapter = CustomAdapter(searchResult.value!!)
        binding.searchResult.adapter = adapter
        viewAnnotationManager = binding.mapView.viewAnnotationManager
        locationPermissionHelper = LocationPermissionHelper(WeakReference(this))
        locationPermissionHelper.checkPermissions {
            onMapReady()
        }
        searchResult.observe(this, Observer {
            adapter.setDataChange(it)
        })
        observerSearch()
        binding.searchBar.setOnCloseListener (
          object: SearchView.OnCloseListener {
              override fun onClose(): Boolean {
                  binding.searchResult.visibility = View.GONE
                  return false
              }

          }
        )
        binding.searchBar.setOnSearchClickListener {
            binding.searchResult.visibility = View.VISIBLE

        }



    }

    private fun observerSearch(){
        GlobalScope.launch {
            binding.searchBar.getQueryTextChangeStateFlow().debounce(300)
                .filter { query -> query.isNotEmpty() }
                .distinctUntilChanged()
                .flatMapLatest {
                        query ->
                    dataFromNetwork(query)
                        .catch { emitAll(flowOf(emptyList<String>())) }
                }
                .flowOn(Dispatchers.Default)  // Perform search in the background
                .collect { result ->
                    Log.d("Test", result.toString())
                   searchResult.postValue(result)
                    // Update UI with search results
                    // Show the results in a dropdown list
//                    runOnUiThread {
//                        val popupMenu = PopupMenu(this@UserLocation, binding.searchBar)
//                        for (i in result) {
//                            popupMenu.getMenu().add(i)
//                        }
//                        popupMenu.show()
//                    }

                }
        }



    }

    private fun checkUserSession() {
        user = SharedReferencesHelper.getUser()
        Log.d("Test", "user not null: ${user == null}, ${user.toString()}")
        if (user == null) {
            startActivity(Intent(this@UserLocation, LoginActivity::class.java))
            finish()
        }
        GlobalScope.launch {
            user?.let {
                val newUser = FirebaseHelper.login(it.username, it.password)
                if (newUser == null) {
                    startActivity(Intent(this@UserLocation, LoginActivity::class.java).apply {
                        putExtra("isExpired", true)
                    })
                    finish()
                }
            }
        }
    }

    private suspend fun dataFromNetwork(query: String): Flow<List<String>> = flow {
        val db = Firebase.firestore
        val noteRefs = db.collectionGroup("notes").get().await()
        val filtered = noteRefs.filter { noteRef ->
            noteRef.getString("name_of_user")
                ?.contains(query) == true || noteRef.getString("description")
                ?.contains(query) == true
        }
        emit(filtered.map { it.getString("name_of_user") ?: "" })
    }.flowOn(Dispatchers.IO)


    private fun onMapReady() {
        binding.mapView.getMapboxMap().setCamera(
            CameraOptions.Builder()

                .zoom(20.0)
                .build()
        )
        val db = Firebase.firestore
        GlobalScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val userRefs = db.collection("users").get().await()
                    for (userRef in userRefs) {
                        val fullName = userRef.getString("name") ?: "NaN"
                        val noteRefs = userRef.reference.collection("notes").get().await()
                        for (noteRef in noteRefs) {
                            val long = noteRef.getDouble("longitude") ?: 0.0;
                            val lat = noteRef.getDouble("latitude") ?: 0.0;
                            val noteDescription = noteRef.getString("description") ?: "Unknown";
                            Log.d("test", "${user?.id} - ${userRef.id}")

                            addViewAnnotation(
                                Point.fromLngLat(long, lat),
                                noteRef.id,
                                fullName,
                                userRef.id,
                                noteDescription,
                                user?.id != userRef.id
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error getting documents: ", e)
                }
            }
        }

        val query = "a"
        GlobalScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val noteRefs = db.collectionGroup("notes").get().await()
                    val filtered = noteRefs.filter { noteRef ->
                        noteRef.getString("name_of_user")
                            ?.contains(query) == true || noteRef.getString("description")
                            ?.contains(query) == true
                    }
                    Log.w("Test", filtered.map {
                        it.getString("name_of_user")
                    }.toString())


                } catch (e: Exception) {
                    Log.w(TAG, "Error getting documents: ", e)
                }
            }
        }
//        db.collection("notes")
//            .get()
//            .addOnSuccessListener { allNotes ->
//                allNotes.documents.forEach {
//                    everyUserNotes ->
//                    everyUserNotes.reference.collection("userNotes")
//                        .get().addOnSuccessListener { noteOfUser ->
//                            noteOfUser.forEach {
//                                     userNote ->
//                                     val fullName = userNote.getString("name") ?: "Unknown"
//                                     val long = userNote.getDouble("longitude") ?: 0.0;
//                                     val lat = userNote.getDouble("latitude") ?: 0.0;
//                                     val note = userNote.getString("note") ?: "Unknown";
//                                     Log.d("test", "${user?.id} - ${it.reference.id}")
//                                 }
//
//                        }
//
//                }
//
//
//            }


        mapView.getMapboxMap().apply {
            addOnMapClickListener(this@UserLocation)
        }
        mapView.getMapboxMap().loadStyle(
            Style.MAPBOX_STREETS
        ) {
            lastStyleUri = it.styleURI
            initLocationComponent()
            setupGesturesListener()
        }
        binding.btnCurrentLocation.setOnClickListener {
            mapView.mapboxMap.setCamera(CameraOptions.Builder().center(currentLocation).build())
            mapView.gestures.focalPoint = mapView.mapboxMap.pixelForCoordinate(currentLocation)
        }
        binding.btnAddNote.setOnClickListener {
            handleAddNoteAction(currentLocation)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_pulsing_location_mode, menu)
        return true
    }


    private fun handleAddNoteAction(forLocation: Point) {
        val saveLocation = Point.fromLngLat(forLocation.longitude(), forLocation.latitude())
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Add Note")

        val viewInflated: View = LayoutInflater.from(this)
            .inflate(R.layout.note_input, binding.root, false)

        val note = viewInflated.findViewById<EditText>(R.id.input_note_title)
//        val descriptionInput = viewInflated.findViewById<EditText>(R.id.input_note_description)

        builder.setView(viewInflated)

        builder.setPositiveButton(
            android.R.string.ok
        ) { dialog, _ ->
            dialog.dismiss()
            val noteString = note.text.toString()
//            val noteDescription = descriptionInput.text.toString()
            user?.let {
                saveToFirebase(saveLocation, it, noteString)

//                addViewAnnotation(saveLocation, it.name, noteString)
            }

            // Here you can handle the inputs
        }

        builder.setNegativeButton(
            android.R.string.cancel
        ) { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    private fun saveToFirebase(point: Point, user: User, noteDescription: String) {
        val db = Firebase.firestore

        // Create a new note with longitude, latitude, and description
        val note = hashMapOf(
            "name_of_user" to user.name,
            "longitude" to point.longitude(),
            "latitude" to point.latitude(),
            "description" to noteDescription,
        )

        // Get a reference to the new note document
        val notesRef = db.collection("users").document(user.id).collection("notes").document()

        // Write the note to the database and handle success/failure
        notesRef.set(note).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "DocumentSnapshot successfully written!")
                addViewAnnotation(point, notesRef.id, user.name, user.id, noteDescription)
            } else {
                Log.w(TAG, "Error writing document", task.exception)
            }
        }
    }


    @SuppressLint("MissingPermission")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_map_style_change -> {
                loadNewStyle()
                return true
            }

            R.id.action_component_disable -> {
//                binding.mapView.location.enabled = false
                startActivity(Intent(this, LoginActivity::class.java))
                return true
            }

            R.id.action_component_enabled -> {
//                binding.mapView.location.enabled = true
                val dialog = Dialog(this)
                dialog.setContentView(R.layout.user_info)

//                val textView: TextView = dialog.findViewById(R.id.yourTextViewId)
//                textView.text = "Your custom text"

                dialog.show()
                return true
            }

            R.id.action_stop_pulsing -> {
                binding.mapView.location.pulsingEnabled = false
                return true
            }

            R.id.action_start_pulsing -> {
                binding.mapView.location.apply {
                    pulsingEnabled = true
                    pulsingMaxRadius = 10f * resources.displayMetrics.density
                }
                return true
            }

            R.id.action_pulsing_follow_accuracy_radius -> {
                binding.mapView.location.apply {
                    showAccuracyRing = true
                    pulsingEnabled = true
                    pulsingMaxRadius = LocationComponentConstants.PULSING_MAX_RADIUS_FOLLOW_ACCURACY
                }
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun loadNewStyle() {
        val styleUrl = if (lastStyleUri == Style.DARK) Style.LIGHT else Style.DARK
        mapboxMap.loadStyle(
            styleUrl
        ) { lastStyleUri = styleUrl }
    }


    private fun setupGesturesListener() {
        mapView.gestures.addOnMoveListener(onMoveListener)
    }

    private fun initLocationComponent() {
        val locationComponentPlugin = mapView.location
        locationComponentPlugin.updateSettings {
            puckBearing = PuckBearing.COURSE
            puckBearingEnabled = true
            enabled = true
            locationPuck = LocationPuck2D(
                bearingImage = ImageHolder.from(R.drawable.mapbox_user_puck_icon),
                shadowImage = ImageHolder.from(R.drawable.mapbox_user_icon_shadow),
                scaleExpression = interpolate {
                    linear()
                    zoom()
                    stop {
                        literal(0.0)
                        literal(0.6)
                    }
                    stop {
                        literal(20.0)
                        literal(1.0)
                    }
                }.toJson()
            )
        }
        locationComponentPlugin.addOnIndicatorPositionChangedListener(
            onIndicatorPositionChangedListener
        )
//        locationComponentPlugin.addOnIndicatorBearingChangedListener(onIndicatorBearingChangedListener)
    }

    private fun onCameraTrackingDismissed() {
        Toast.makeText(this, "onCameraTrackingDismissed", Toast.LENGTH_SHORT).show()
        mapView.location
            .removeOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
        mapView.location
            .removeOnIndicatorBearingChangedListener(onIndicatorBearingChangedListener)
        mapView.gestures.removeOnMoveListener(onMoveListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.location
            .removeOnIndicatorBearingChangedListener(onIndicatorBearingChangedListener)
        mapView.location
            .removeOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
        mapView.gestures.removeOnMoveListener(onMoveListener)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        locationPermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onMapClick(point: Point): Boolean {
        handleAddNoteAction(point)
        return true
    }


    @SuppressLint("SetTextI18n")
    private fun addViewAnnotation(
        point: Point,
        noteId: String,
        fullName: String,
        userId: String,
        noteDescription: String,
        hideClose: Boolean = false
    ) {
        val viewAnnotation = viewAnnotationManager.addViewAnnotation(
            resId = R.layout.item_callout_view,
            options = viewAnnotationOptions {
                geometry(point)
                allowOverlap(true)
                ignoreCameraPadding(true)
                allowOverlapWithPuck(true)
            }
        )
        viewAnnotationViews.add(viewAnnotation)
        ItemCalloutViewBinding.bind(viewAnnotation).apply {
            avatar.text = if (fullName.isNotEmpty()) fullName[0].toString() else "NnN"
            username.text = fullName
            root.setOnLongClickListener {
                viewAnnotationManager.removeViewAnnotation(viewAnnotation)
                viewAnnotationViews.remove(viewAnnotation)
                val db = Firebase.firestore
                GlobalScope.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            db.collection("users").document(userId)
                                .collection("notes").document(noteId)
                                .delete().await()
                            Log.d(TAG, "DocumentSnapshot successfully deleted!")
                        } catch (e: Exception) {
                            Log.w(TAG, "Error deleting document", e)
                        }
                    }
                }
                true
            }
            close.visibility = if (hideClose) View.GONE else View.VISIBLE
            close.setOnClickListener {
                viewAnnotationManager.removeViewAnnotation(viewAnnotation)
                viewAnnotationViews.remove(viewAnnotation)
                val db = Firebase.firestore
                GlobalScope.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            db.collection("users").document(userId)
                                .collection("notes").document(noteId)
                                .delete().await()
                            Log.d(TAG, "DocumentSnapshot successfully deleted!")
                        } catch (e: Exception) {
                            Log.w(TAG, "Error deleting document", e)
                        }
                    }
                }
            }
            note.text = noteDescription
        }
    }
}