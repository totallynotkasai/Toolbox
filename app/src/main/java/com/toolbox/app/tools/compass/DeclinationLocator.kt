package com.toolbox.app.tools.compass

import android.content.Context
import android.hardware.GeomagneticField
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** What came of looking up the declination where the phone is. */
sealed interface DeclinationLookup {
    data class Found(val declination: Float) : DeclinationLookup
    /** Location is switched off for the whole phone, and there's no earlier position to use. */
    data object LocationOff : DeclinationLookup
    /** No position could be had: none was known and a fresh one didn't come in time. */
    data object NoFix : DeclinationLookup
}

/** A last known position younger than this is used as it is; an older one only if no fresh one comes. */
private const val FRESH_MILLIS = 24 * 60 * 60 * 1000L

/**
 * Works out the magnetic declination where the phone is, from its approximate position and Android's built-in
 * World Magnetic Model ([GeomagneticField]). Nothing goes over the internet, and the position isn't kept: only the
 * declination is returned. Needs ACCESS_COARSE_LOCATION.
 */
suspend fun lookUpDeclination(context: Context): DeclinationLookup {
    val manager = context.getSystemService(LocationManager::class.java) ?: return DeclinationLookup.NoFix
    val last = lastKnown(manager)
    val ageMillis = last?.let { (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000 }
    val location = if (last != null && ageMillis != null && ageMillis < FRESH_MILLIS) last
    else currentFix(context, manager) ?: last
    if (location == null) {
        return if (LocationManagerCompat.isLocationEnabled(manager)) DeclinationLookup.NoFix else DeclinationLookup.LocationOff
    }
    val altitude = if (location.hasAltitude()) location.altitude.toFloat() else 0f
    val field = GeomagneticField(location.latitude.toFloat(), location.longitude.toFloat(), altitude, System.currentTimeMillis())
    return DeclinationLookup.Found(field.declination)
}

/** The newest position any provider still has. Providers this app may not use are skipped. */
private fun lastKnown(manager: LocationManager): Location? = manager.allProviders.mapNotNull { provider ->
    try {
        manager.getLastKnownLocation(provider)
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}.maxByOrNull { it.elapsedRealtimeNanos }

/**
 * Asks once for a fresh approximate position. Uses the fused provider where there is one (Android 12+), otherwise the
 * network one, otherwise GPS. LocationManagerCompat gives up after 30 seconds and passes null.
 */
private suspend fun currentFix(context: Context, manager: LocationManager): Location? {
    val provider = listOfNotNull(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) LocationManager.FUSED_PROVIDER else null,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
    ).firstOrNull { p -> runCatching { manager.isProviderEnabled(p) }.getOrDefault(false) } ?: return null
    return suspendCancellableCoroutine { cont ->
        val cancel = CancellationSignal()
        cont.invokeOnCancellation { cancel.cancel() }
        try {
            LocationManagerCompat.getCurrentLocation(manager, provider, cancel, ContextCompat.getMainExecutor(context)) {
                if (cont.isActive) cont.resume(it)
            }
        } catch (_: SecurityException) {
            // Before Android 12, approximate location can't use GPS.
            if (cont.isActive) cont.resume(null)
        } catch (_: IllegalArgumentException) {
            if (cont.isActive) cont.resume(null)
        }
    }
}
