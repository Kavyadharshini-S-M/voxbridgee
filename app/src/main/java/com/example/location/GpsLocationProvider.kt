package com.example.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Provides offline GPS coordinate resolution via FusedLocationProviderClient
 * for automatic attachment to tactical distress alerts and emergency dispatches.
 */
class GpsLocationProvider(private val context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context.applicationContext)
    }

    private val _lastLocationString = MutableStateFlow<String?>("GPS Acquiring...")
    val lastLocationString: StateFlow<String?> = _lastLocationString.asStateFlow()

    private val _lastCoordinates = MutableStateFlow<Pair<Double, Double>?>(null)
    val lastCoordinates: StateFlow<Pair<Double, Double>?> = _lastCoordinates.asStateFlow()

    init {
        // Initial quick background acquisition
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    val formatted = formatGpsCoordinates(loc.latitude, loc.longitude)
                    _lastLocationString.value = formatted
                    _lastCoordinates.value = Pair(loc.latitude, loc.longitude)
                }
            }
        } catch (e: SecurityException) {
            Log.w("GpsLocationProvider", "Location permission missing: ${e.message}")
        } catch (e: Exception) {
            Log.w("GpsLocationProvider", "Initial location lookup notice: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocationString(): String? {
        return try {
            val location = getLastKnownLocation() ?: fetchFreshLocation()
            if (location != null) {
                val formatted = formatGpsCoordinates(location.latitude, location.longitude)
                _lastLocationString.value = formatted
                _lastCoordinates.value = Pair(location.latitude, location.longitude)
                formatted
            } else {
                _lastLocationString.value
            }
        } catch (e: SecurityException) {
            Log.w("GpsLocationProvider", "GPS query denied: ${e.message}")
            _lastLocationString.value
        } catch (e: Exception) {
            Log.w("GpsLocationProvider", "GPS query error: ${e.message}")
            _lastLocationString.value
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastKnownLocation(): Location? = suspendCancellableCoroutine { continuation ->
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { loc ->
                    if (continuation.isActive) continuation.resume(loc)
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(null)
                }
        } catch (e: Throwable) {
            if (continuation.isActive) continuation.resume(null)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchFreshLocation(): Location? = suspendCancellableCoroutine { continuation ->
        try {
            val cts = CancellationTokenSource()
            continuation.invokeOnCancellation { cts.cancel() }
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (continuation.isActive) continuation.resume(loc)
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(null)
                }
        } catch (e: Throwable) {
            if (continuation.isActive) continuation.resume(null)
        }
    }

    companion object {
        fun formatGpsCoordinates(lat: Double, lng: Double): String {
            val latDir = if (lat >= 0) "N" else "S"
            val lngDir = if (lng >= 0) "E" else "W"
            return String.format(Locale.US, "%.4f° %s, %.4f° %s", Math.abs(lat), latDir, Math.abs(lng), lngDir)
        }
    }
}
