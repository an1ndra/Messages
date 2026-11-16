package com.anindra.messages.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager

/**
 * Where the device last was.
 *
 * A geo link is a plain text message, so nothing here needs the network: the
 * fix comes from the providers the system already polled, which is why the
 * answer can be null on a phone that has not been outside yet.
 */
internal object LocationShare {

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Want a fix in [lastKnown]: the freshest provider wins. */
    fun lastKnown(context: Context): Pair<Double, Double>? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        return providers
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }
            ?.let { location ->
                val (lat, lng) = location.latitude to location.longitude
                if (ContactCard.isUsable(lat, lng)) lat to lng else null
            }
    }
}
