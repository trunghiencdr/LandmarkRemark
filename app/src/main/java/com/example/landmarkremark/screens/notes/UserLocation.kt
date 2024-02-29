package com.example.landmarkremark.screens.notes


import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.example.landmarkremark.R
import com.example.landmarkremark.databinding.ActivityUserLocationBinding
import com.example.landmarkremark.databinding.NoteViewBinding
import com.example.landmarkremark.models.Note
import com.example.landmarkremark.models.User
import com.example.landmarkremark.screens.auth.LoginActivity
import com.example.landmarkremark.screens.auth.LoginActivity.Companion.IS_EXPIRED
import com.example.landmarkremark.screens.notes.components.SearchResultAdapter
import com.example.landmarkremark.utils.AppDelegate
import com.example.landmarkremark.utils.FirebaseHelper
import com.example.landmarkremark.utils.LocationPermissionHelper
import com.example.landmarkremark.utils.SharedReferencesHelper
import com.example.landmarkremark.utils.focusPoint
import com.example.landmarkremark.utils.getQueryTextChangeStateFlow
import com.example.landmarkremark.utils.instantSearch
import com.example.landmarkremark.utils.showInputDialog
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.dsl.generated.interpolate
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.delegates.listeners.OnMapLoadedListener
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference


/**
 * Tracks the user location on screen, simulates a navigation session.
 */
class UserLocation : AppCompatActivity(), OnMapClickListener, OnMoveListener,
    OnIndicatorPositionChangedListener {
    private lateinit var locationPermissionHelper: LocationPermissionHelper
    private val noteViews = hashMapOf<String, View>()
    private lateinit var binding: ActivityUserLocationBinding
    private lateinit var currentLocation: Point
    private var currentUser: User? = null
    private var searchResult = MutableLiveData<ArrayList<Note>>(arrayListOf())
    private lateinit var adapter: SearchResultAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserLocationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize location permission helper
        locationPermissionHelper = LocationPermissionHelper(WeakReference(this))

        // Check user session and request location permission
        checkUserSession()
        locationPermissionHelper.checkPermissions { onMapReady() }
    }

    // Function to add a note view to the map
    private fun addNoteViewToMap(note: Note) {
        addNoteView(
            Point.fromLngLat(note.long, note.lat),
            note.id,
            note.name,
            note.description,
            currentUser?.id != note.userId
        )
    }

    // Function to delete a note view from the map
    private fun deleteNoteViewInMap(noteId: String) {
        noteViews[noteId]?.let {
            binding.mapView.viewAnnotationManager.removeViewAnnotation(it)
            noteViews.remove(noteId)
        }
    }

    // Function to observe search query changes
    private fun observerSearchQuery() {
        lifecycleScope.launch {
            binding.searchBar.getQueryTextChangeStateFlow().instantSearch().collect { result ->
                searchResult.postValue(result)
            }
        }
    }

    // Function to check user session
    private fun checkUserSession() {
        currentUser = SharedReferencesHelper.getUser()
        if (currentUser == null) {
            AppDelegate.navigateAndFinish(this, LoginActivity::class.java)
        } else {
            lifecycleScope.launch {
                val newUser = FirebaseHelper.login(currentUser!!.username, currentUser!!.password)
                if (newUser == null) {
                    val intent = Intent(this@UserLocation, LoginActivity::class.java).apply {
                        putExtra(IS_EXPIRED, true)
                    }
                    startActivity(intent)
                    finish()
                }
            }
        }
    }

    // Function to initialize map when ready
    private fun onMapReady() {
        initMapbox()
        initAdapter()
        observerSearchQuery()
        observerSearchResult()
        observerSearchAction()
    }

    // Function to initialize adapter
    private fun initAdapter() {
        adapter = SearchResultAdapter(searchResult.value!!)
        binding.searchResult.adapter = adapter
    }

    // Function to observe search action
    private fun observerSearchAction() {
        binding.searchBar.setOnCloseListener {
            binding.searchResult.visibility = View.GONE
            false
        }
        binding.searchBar.setOnSearchClickListener {
            binding.searchResult.visibility = View.VISIBLE
        }
    }

    // Function to initialize Mapbox
    private fun initMapbox() {
        binding.mapView.mapboxMap.apply {
            addOnMapClickListener(this@UserLocation)
            loadStyle(Style.MAPBOX_STREETS) {
                initLocationComponent()
                setupGesturesListener()
            }
        }
        binding.mapView.mapboxMap.addOnMapLoadedListener(OnMapLoadedListener {
            FirebaseHelper.onChange(onAdded = ::addNoteViewToMap, onDeleted = ::deleteNoteViewInMap)
        })
    }

    // function to observer search result and update ui
    private fun observerSearchResult() {
        searchResult.observe(this, Observer {
            adapter.setDataChange(it)
        })
    }

    // function to handle add note for current location
    fun handleAddNoteAction(view: View) {
        handleAddNoteAction(currentLocation, true)
    }

    // function to focus use's location
    fun handleFocusCurrentLocation(view: View) {
        binding.mapView.focusPoint(currentLocation)
    }

    // function to create menu of note screen
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_note_screen, menu)
        return true
    }

    // function to handle form add note
    private fun handleAddNoteAction(forLocation: Point, isCurrent: Boolean = false) {
        val saveLocation = Point.fromLngLat(forLocation.longitude(), forLocation.latitude())
        val title = if (isCurrent) "Add note in your location" else "Add note"
        showInputDialog(
            this, binding.root, title
        ) { noteDescription ->
            currentUser?.let {
                saveToFirebase(point = saveLocation, it.id, it.name, noteDescription)
            }
        }
    }

    // function to save note data into firebase
    private fun saveToFirebase(
        point: Point, userId: String, nameOfUser: String, noteDescription: String
    ) {
        lifecycleScope.launch {
            FirebaseHelper.addNote(point, userId, nameOfUser, noteDescription)
        }
    }

    @SuppressLint("MissingPermission")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_logout -> {
                SharedReferencesHelper.removeUser()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                return true
            }

            R.id.action_show_info -> {
                val dialog = Dialog(this)
                dialog.setContentView(R.layout.user_info)
                dialog.show()
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun setupGesturesListener() {
        binding.mapView.gestures.addOnMoveListener(this)
    }

    // function to redraw ui and enable current location tracking in mapbox
    private fun initLocationComponent() {
        val locationComponentPlugin = binding.mapView.location
        locationComponentPlugin.updateSettings {
            puckBearing = PuckBearing.COURSE
            puckBearingEnabled = true
            enabled = true
            locationPuck = LocationPuck2D(
                bearingImage = ImageHolder.from(R.drawable.ic_location_on),
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
        locationComponentPlugin.addOnIndicatorPositionChangedListener(this)
    }

    // function to un-tracking camera to current location
    private fun onCameraTrackingDismissed() {
        stopFocusCurrent()
    }

    private fun stopFocusCurrent() { // when user move then un-focus camera
        binding.mapView.apply {
            location.removeOnIndicatorPositionChangedListener(this@UserLocation)
            gestures.removeOnMoveListener(this@UserLocation)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopFocusCurrent()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        locationPermissionHelper.onRequestPermissionsResult( // request permission for mapbox
            requestCode, permissions, grantResults
        )
    }

    // function add note in any-place clicked
    override fun onMapClick(point: Point): Boolean { // show input note description dialog
        handleAddNoteAction(point)
        return true
    }

    // add note view to map box
    @SuppressLint("SetTextI18n")
    private fun addNoteView(
        point: Point,
        noteId: String,
        fullName: String,
        noteDescription: String,
        hideClose: Boolean = false
    ) {
        // add noteView to viewAnnotationManager to remove when needed
        val viewAnnotation =
            binding.mapView.viewAnnotationManager.addViewAnnotation(resId = R.layout.note_view,
                options = viewAnnotationOptions {
                    geometry(point)
                    allowOverlap(true)
                    ignoreCameraPadding(true)
                    allowOverlapWithPuck(true)
                })
        noteViews[noteId] = viewAnnotation // save view refs and noteId to remove note view
        NoteViewBinding.bind(viewAnnotation).apply {// binding data to noteView
            avatar.text = if (fullName.isNotEmpty()) fullName[0].toString() else "NnN"
            username.text = fullName
            root.setOnLongClickListener {
                removeNoteInDB(noteId)
                true
            }
            close.visibility = if (hideClose) View.GONE else View.VISIBLE
            close.setOnClickListener {
//                root.visibility = View.GONE
                removeNoteInDB(noteId)
            }
            note.text = noteDescription
        }
    }

    // remove note data when user click close button in noteview
    private fun removeNoteInDB(noteId: String) {
        lifecycleScope.launch {// remove note in firebase
            FirebaseHelper.removeNote(noteId)
        }
    }

    override fun onMove(detector: MoveGestureDetector): Boolean {
        return false
    }

    override fun onMoveBegin(detector: MoveGestureDetector) {
        onCameraTrackingDismissed()
    }

    override fun onMoveEnd(detector: MoveGestureDetector) {
    }

    override fun onIndicatorPositionChanged(point: Point) {
        // camera to current location when open app
        currentLocation = point
        binding.mapView.focusPoint(point)
    }
}


