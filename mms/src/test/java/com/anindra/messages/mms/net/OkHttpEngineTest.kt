package com.anindra.messages.mms.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The proxyless case, proved on a real OkHttp client rather than by inspection.
 *
 * The reference bug is a `ProxySelector` that dereferences its proxy
 * unconditionally, so a proxyless retrieve dies before the request goes out. A
 * proxyless MMSC has to reach `newCall` with no proxy configured at all.
 */
class OkHttpEngineTest {
    private val mmsc = ApnProfile("mms", "https://mms.example.net/mms", null, null, "mms")

    @Test
    fun aProxylessProfileProducesAClientWithNoProxy() {
        val profile = ApnProfile("mms", "https://mms.example.net/mms", null, null, "mms")

        assertNull("a proxyless MMSC is the normal case", profile.proxySpec())

        val client = OkHttpEngine(network = null).buildClient(profile.proxySpec())

        // The reference bug is a ProxySelector that dereferences its proxy
        // unconditionally; leaving OkHttp's proxy unset is what lets a proxyless
        // retrieve reach the network at all.
        assertNull(client.proxy)
        // OkHttp always has a socket factory; binding one from a network is the
        // only thing that could make this null.
        assertNotNull(client.socketFactory)
    }

    @Test
    fun aProxiedProfileBuildsAnHttpProxy() {
        val spec = ApnProfile("mms", "http://mms.example.net/mms", "proxy.example.net", 8080, "mms").proxySpec()

        val client = OkHttpEngine(network = null).buildClient(spec)

        assertEquals(java.net.Proxy.Type.HTTP, client.proxy!!.type())
        val address = client.proxy!!.address() as java.net.InetSocketAddress
        assertEquals("proxy.example.net", address.hostString)
        assertEquals(8080, address.port)
    }

    @Test
    fun theTimeoutsComeFromTheCarrierSocketTimeout() {
        val client = OkHttpEngine(network = null, connectTimeoutMillis = 1_500L, readTimeoutMillis = 2_500L)
            .buildClient(null)

        assertEquals(1_500L, client.connectTimeoutMillis.toLong())
        assertEquals(2_500L, client.readTimeoutMillis.toLong())
    }
}