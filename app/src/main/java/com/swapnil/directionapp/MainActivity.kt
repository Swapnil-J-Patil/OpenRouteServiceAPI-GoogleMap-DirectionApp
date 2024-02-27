package com.swapnil.directionapp


import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.DirectionsApi
import com.google.maps.GeoApiContext
import com.google.maps.android.PolyUtil
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.currentCameraPositionState
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.swapnil.directionapp.ui.theme.DirectionAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin


class MainActivity : ComponentActivity()  {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var locationRequired: Boolean = false
    private val permissions = arrayOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    override fun onResume() {
        super.onResume()
        if (locationRequired) {
            startLocationUpdates()
        }
    }
    val newRoutePoints = mutableListOf<LatLng>()

    override fun onPause() {
        super.onPause()
        locationCallback?.let {
            fusedLocationClient?.removeLocationUpdates(it)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        locationCallback?.let {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 100
            )
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(3000)
                .setMaxUpdateDelayMillis(100)
                .build()

            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                it,
                Looper.getMainLooper()
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapsInitializer.initialize(this, MapsInitializer.Renderer.LATEST) {

        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setContent {

            var currentLocation by remember { mutableStateOf(LatLng(0.toDouble(), 0.toDouble())) }
            val cameraPosition = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(
                    currentLocation, 20f
                )
            }
            val origin = LatLng(18.878460, 72.930399)//You can add your area location it's for camera position

            val cameraPositionStateNew = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(origin, 15f)
            }

            var cameraPositionState by remember {
                mutableStateOf(cameraPosition)
            }
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(p0: LocationResult) {
                    super.onLocationResult(p0)
                    for (location in p0.locations) {
                        currentLocation = LatLng(location.latitude, location.longitude)
                        // Get the accuracy radius from the location object
                        cameraPositionState = CameraPositionState(
                            position = CameraPosition.fromLatLngZoom(
                                currentLocation, 20f
                            )
                        )
                    }
                }
            }
            DirectionAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LocationScreen(this@MainActivity, currentLocation, cameraPositionStateNew)
                }
            }
        }
    }
    @Composable
    private fun LocationScreen(
        context: Context,
        currentLocation: LatLng,
        cameraPositionState: CameraPositionState
    ) {
        val launchMultiplePermissions =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
            { permissionMaps ->
                val areGranted = permissionMaps.values.reduce { acc, next -> acc && next }
                if (areGranted) {
                    locationRequired = true
                    startLocationUpdates()
                    Toast.makeText(context, "Permission Granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Permission Denied", Toast.LENGTH_SHORT).show()
                }
            }
        // Define a mutable list to store the route points
        val latLngList = remember { mutableStateOf<List<LatLng>>(emptyList()) }
        LaunchedEffect(currentLocation) {
            if (permissions.all {
                    ContextCompat.checkSelfPermission(
                        context,
                        it
                    ) == PackageManager.PERMISSION_GRANTED
                }) {
                //get location
                startLocationUpdates()
                //get route
                createRoute(currentLocation) { routePoints ->
                    val pointsList = mutableListOf<LatLng>()
                    for (i in routePoints.indices step 2) {
                        val lat = routePoints[i]
                        val lng = routePoints[i + 1]
                        pointsList.add(LatLng(lat, lng))
                    }
                    latLngList.value = pointsList
                }
            } else {
                launchMultiplePermissions.launch(permissions)
            }
        }
        val points = generateCirclePoints(currentLocation, 10.00)
        // Define a transparent sky blue color
        val transparentSkyBlue = Color(0x3F00BFFF)

        Box(modifier = Modifier.fillMaxSize()) {

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState
            ) {

                val destination=LatLng(18.879959, 72.932034)
                Marker(
                    state = MarkerState(
                        position = destination,
                    ),
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                    title = "Destination",
                    snippet = "Your destination is here!!!"
                )
                Marker(
                    state = MarkerState(
                        position = currentLocation,
                    ),
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN),
                    title = "current location",
                    snippet = "You are here!!!"
                )
                Polygon(
                    points = points,
                    fillColor = transparentSkyBlue,
                    strokeColor = Color.Blue,
                    strokeWidth = 5.0f
                )

                Polyline(
                    points = latLngList.value,
                    color = Color.Green,
                    width = 10f
                )
            }
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Your location is ${currentLocation.latitude} and ${currentLocation.longitude}",
                    color = Color.Black
                )
                Button(onClick = {

                    if (permissions.all {
                            ContextCompat.checkSelfPermission(
                                context,
                                it
                            ) == PackageManager.PERMISSION_GRANTED
                        }) {
                        //get location
                        startLocationUpdates()
                    } else {
                        launchMultiplePermissions.launch(permissions)
                    }
                }) {
                    Text(text = "Refresh Location")
                }
            }
        }
    }

    private fun generateCirclePoints(center: LatLng, radiusMeters: Double): List<LatLng> {
        val numPoints = 100
        val points = mutableListOf<LatLng>()
        val radiusAngle = 2 * PI / numPoints

        for (i in 0 until numPoints) {
            val theta = i * radiusAngle
            val x = center.longitude + radiusMeters / 111000 * cos(theta)
            val y = center.latitude + radiusMeters / 111000 * sin(theta)
            points.add(LatLng(y, x))
        }
        return points
    }
    private fun createRoute(
        startLocation: LatLng,
        callback: (List<Double>) -> Unit
    ) {
        val routePoints = mutableListOf<LatLng>()
        CoroutineScope(Dispatchers.IO).launch {
            val call = getRetrofit().create(ApiService::class.java)
                .getRoute(
                    "5b3ce3597851110001cf62480548dffe0e2a42f4b29193a1b731680e",
                    "${startLocation.longitude},${startLocation.latitude}",
                    "72.932034,18.879959"
                )
            if (call.isSuccessful) {
                drawRoute(call.body(), routePoints)
                val pointsList = routePoints.flatMap { listOf(it.latitude, it.longitude) }
                Log.d("route", "Route points as list: $pointsList")
                callback(pointsList)
            } else {
                Log.i("route", "KO")
            }
        }
    }

    private fun drawRoute(routeResponse: RouteResponse?, routePoints: MutableList<LatLng>) {
        routeResponse?.features?.firstOrNull()?.geometry?.coordinates?.forEach {
            val latLng = LatLng(it[1], it[0])
            routePoints.add(latLng)
        }
        Log.i("aris", "Drawn route points: $routePoints")
    }


    private fun getRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.openrouteservice.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}