package com.example.location

import kotlin.math.*

/**
 * GpsDistanceUtils: Comprehensive 100% offline geographical calculations.
 * Features:
 * 1. Haversine straight-line distance calculation between two GPS coordinates.
 * 2. 8-point compass bearing & directional arrow (e.g. "Northeast ↗").
 * 3. Relative distance formatting for emergency field workers.
 * 4. Signal RSSI (dBm) estimated distance model for BLE/Wi-Fi mesh links.
 */
object GpsDistanceUtils {

    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculates the great-circle distance between two points using the Haversine formula.
     */
    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Calculates compass bearing from (lat1, lon1) pointing to (lat2, lon2).
     */
    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): String {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)

        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
        val bearingDeg = (Math.toDegrees(atan2(y, x)) + 360) % 360

        return when {
            bearingDeg in 22.5..67.5 -> "Northeast ↗"
            bearingDeg in 67.5..112.5 -> "East ➔"
            bearingDeg in 112.5..157.5 -> "Southeast ↘"
            bearingDeg in 157.5..202.5 -> "South ↓"
            bearingDeg in 202.5..247.5 -> "Southwest ↙"
            bearingDeg in 247.5..292.5 -> "West ⬅"
            bearingDeg in 292.5..337.5 -> "Northwest ↖"
            else -> "North ↑"
        }
    }

    /**
     * Formats distance in meters into layman human-readable units.
     */
    fun formatDistance(meters: Double): String {
        return if (meters < 1000.0) {
            "${meters.roundToInt()} m"
        } else {
            String.format(java.util.Locale.US, "%.1f km", meters / 1000.0)
        }
    }

    /**
     * Extracts latitude and longitude from embedded message tags like "[GPS: 12.9716, 77.5946]".
     */
    fun parseGpsCoordinates(text: String): Pair<Double, Double>? {
        val regex = Regex("""\[GPS:\s*([0-9.-]+),\s*([0-9.-]+)\]""")
        val match = regex.find(text) ?: return null
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lon = match.groupValues[2].toDoubleOrNull() ?: return null
        return Pair(lat, lon)
    }

    /**
     * Estimates approximate distance in meters from Bluetooth/Wi-Fi RSSI (dBm).
     * Uses Log-distance Path Loss Model: d = 10 ^ ((Measured Power - RSSI) / (10 * N))
     */
    fun estimateRssiDistance(rssiDbm: Int): String {
        if (rssiDbm == 0 || rssiDbm < -100) return "Nearby"
        val measuredPower = -50 // RSSI at 1 meter
        val n = 2.4 // Path loss exponent in obstacle-heavy field environment
        val ratio = (measuredPower - rssiDbm) / (10.0 * n)
        val distMeters = 10.0.pow(ratio).coerceIn(1.0, 150.0)
        return if (distMeters < 10) {
            "~${distMeters.roundToInt()}m"
        } else {
            "~${(distMeters / 5).roundToInt() * 5}m"
        }
    }
}
