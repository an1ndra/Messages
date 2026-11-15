package com.anindra.messages.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import java.util.Locale

/**
 * A location shared in a text.
 *
 * The link is a `geo:` URI rather than a picture of a map: it survives being
 * forwarded, it opens in whatever maps app the recipient has, and it costs one
 * SMS instead of an MMS. Nothing here touches the network — the point of the
 * feature is that a pinned map would not work on the phone that needs it.
 */
object LocationShare {

    /** Six decimals is about 10 cm, which is finer than any phone's fix. */
    private const val DECIMALS = 6

    fun geoLink(latitude: Double, longitude: Double): String =
        "geo:" + String.format(Locale.ROOT, "%.${DECIMALS}f,%.${DECIMALS}f", latitude, longitude)

    /** The same link with a human label, which is what most people will read. */
    fun geoLink(latitude: Double, longitude: Double, label: String): String {
        val clean = label.trim()
        if (clean.isEmpty()) return geoLink(latitude, longitude)
        // Encode the separators a query cannot carry. URLEncoder would turn
        // spaces into '+', which a geo: parser reads as a literal plus.
        val encoded = clean
            .replace("%", "%25")
            .replace(" ", "%20")
            .replace("?", "%3F")
            .replace("&", "%26")
            .replace("#", "%23")
        return geoLink(latitude, longitude) + "?q=" + encoded
    }

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * The last fix the device already has, if any.
     *
     * Deliberately the last known location and not a request: a single text is
     * not a reason to make the GPS spin for thirty seconds, and a stale fix that
     * the user can see and edit beats a button that appears to do nothing. The
     * dialog shows the coordinates it filled in, so a stale one is obvious.
     */
    @SuppressLint("MissingPermission")
    fun lastKnown(context: Context): Pair<Double, Double>? {
        if (!hasPermission(context)) return null
        return try {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return null
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            val fixes: List<Location> = providers.mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            val newest = fixes.maxByOrNull { it.time } ?: return null
            newest.latitude to newest.longitude
        } catch (_: Exception) {
            null
        }
    }
}
