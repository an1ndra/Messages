package com.anindra.messages.mms.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * One held MMS network. Closing it drops this holder's reference.
 *
 * [handle] is opaque on purpose: in production it is an `android.net.Network`,
 * which cannot be constructed outside the framework, so the type is `Any?` and
 * the orchestration can be tested without one. A null handle means the lease is
 * unbound and must not be used.
 */
interface MmsNetworkLease : Closeable {
    val handle: Any?
}

/**
 * Source of MMS-capable networks. `acquire` returning null means "not right
 * now" — it never means "here is some other network instead".
 */
interface MmsNetworkGate : Closeable {
    fun acquire(timeoutMillis: Long): MmsNetworkLease?
}

/**
 * Requests and ref-counts a cellular network with the MMS capability.
 *
 * Binding to `NET_CAPABILITY_MMS` generally requires being the default SMS app
 * on modern Android; no permission grants it to a third-party app. That is a
 * platform constraint rather than a defect here, and it is why an app that is
 * not the default SMS handler simply sees null from [acquire] and retries later
 * instead of getting a network it should not use.
 *
 * When no MMS network can be bound this returns null rather than degrading to
 * Wi-Fi or to a generic cellular network. Sending an MMSC transaction over a
 * network the carrier never provisioned it for produces an opaque HTTP failure
 * from the far side, which is far harder to diagnose than "no network".
 */
class MmsNetworkBinding(context: Context) : MmsNetworkGate {
    private val connectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private val lock = Any()

    private var registered: NetworkCallback? = null
    private var bound: Network? = null
    private var references = 0

    private val request: NetworkRequest = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
        .build()

    override fun acquire(timeoutMillis: Long): MmsNetworkLease? {
        synchronized(lock) {
            if (bound != null) {
                references++
                return Lease()
            }
        }
        // The wait happens outside `lock`: the platform delivers onAvailable on
        // its own thread, and that callback takes the same monitor.
        val latch = CountDownLatch(1)
        val callback = object : NetworkCallback() {
            override fun onAvailable(network: Network) {
                synchronized(lock) {
                    if (bound == null) {
                        bound = network
                        references = 1
                    }
                }
                latch.countDown()
            }

            override fun onUnavailable() = latch.countDown()
        }
        val manager = connectivityManager ?: return null
        // requestNetwork is void; a SecurityException is the only refusal it
        // signals, and a caller that holds no MMS permission gets nothing at all.
        val accepted = try {
            manager.requestNetwork(request, callback, timeoutMillis.toInt())
            true
        } catch (_: SecurityException) {
            false
        }
        if (!accepted) return null
        val arrived = latch.await(timeoutMillis + SETTLE_GRACE_MS, TimeUnit.MILLISECONDS)
        synchronized(lock) {
            registered = callback
            if (!arrived || bound == null) {
                unregisterLocked()
                return null
            }
            return Lease()
        }
    }

    override fun close() = synchronized(lock) { unregisterLocked() }

    private fun unregisterLocked() {
        registered?.let { callback ->
            try {
                connectivityManager?.unregisterNetworkCallback(callback)
            } catch (_: IllegalArgumentException) {
                // Already torn down by the platform when the network dropped.
            }
        }
        registered = null
        bound = null
        references = 0
    }

    private inner class Lease : MmsNetworkLease {
        override val handle: Any?
            get() = synchronized(lock) { bound }

        override fun close() = synchronized(lock) {
            if (references > 0 && --references == 0) unregisterLocked()
        }
    }

    private companion object {
        const val SETTLE_GRACE_MS = 500L
    }
}