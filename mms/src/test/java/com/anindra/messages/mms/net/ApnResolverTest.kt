package com.anindra.messages.mms.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApnResolverTest {
    @Test
    fun anMmsRowIsSelected() {
        val rows = listOf(
            ApnRow("internet", "http://no.mmsc.internet", null, null, "default"),
            ApnRow("mms", "http://mmsc.example.net/servlets/mms", null, null, "mms"),
        )

        val profile = ApnRowSelector.select(rows)

        assertEquals("mms", profile?.apnName)
        assertEquals("http://mmsc.example.net/servlets/mms", profile?.mmscUrl)
        assertEquals("mms", profile?.type)
    }

    @Test
    fun aWildcardRowIsTreatedAsMms() {
        val rows = listOf(ApnRow("any", "http://mmsc.example.net/mms", null, null, "*"))

        assertEquals("http://mmsc.example.net/mms", ApnRowSelector.select(rows)?.mmscUrl)
    }

    @Test
    fun aCommaSeparatedTypeListMatchesOnTheMmsEntry() {
        assertTrue(ApnRowSelector.isMmsType("default,mms"))
        assertTrue(ApnRowSelector.isMmsType("MMS"))
        assertTrue(ApnRowSelector.isMmsType(" default , mms "))
    }

    @Test
    fun aDataOnlyRowIsNotSelectedEvenWhenItCarriesAnMmsc() {
        val rows = listOf(
            ApnRow("internet", "http://stale.example.net", null, null, "default"),
            ApnRow("ims", null, null, null, "ims"),
        )

        assertNull(ApnRowSelector.select(rows))
    }

    @Test
    fun noRowsIsNoApn() {
        assertNull(ApnRowSelector.select(emptyList()))
    }

    @Test
    fun aRowWithNoTypeIsNotSelected() {
        assertFalse(ApnRowSelector.isMmsType(null))
        assertFalse(ApnRowSelector.isMmsType(""))
        assertNull(ApnRowSelector.select(listOf(ApnRow("x", "http://mmsc.example.net", null, null, null))))
    }

    @Test
    fun theProfileCarriesTheProxyColumns() {
        val profile = ApnRowSelector.select(
            listOf(ApnRow("mms", "http://mmsc.example.net", "proxy.example.net", 8080, "mms"))
        )

        assertEquals(ProxySpec("proxy.example.net", 8080), profile?.proxySpec())
    }

    @Test
    fun aBlankProxyColumnMeansNoProxy() {
        val profile = ApnRowSelector.select(
            listOf(ApnRow("mms", "http://mmsc.example.net", "  ", 80, "mms"))
        )

        assertNull("a proxyless MMSC is the normal case", profile?.proxySpec())
    }

    @Test
    fun aProxyWithNoPortDefaultsToEighty() {
        val profile = ApnRowSelector.select(
            listOf(ApnRow("mms", "http://mmsc.example.net", "proxy.example.net", 0, "mms"))
        )

        assertEquals(ProxySpec("proxy.example.net", 80), profile?.proxySpec())
    }

    @Test
    fun anEndpointWithAProxyIsProxied() {
        val profile = ApnRowSelector.select(
            listOf(ApnRow("mms", "http://mmsc.example.net/mms", "proxy.example.net", 8080, "mms"))
        )

        val endpoint = requireNotNull(profile).endpoint()

        assertTrue(endpoint is MmscEndpoint.Proxied)
        assertEquals(ProxySpec("proxy.example.net", 8080), (endpoint as MmscEndpoint.Proxied).proxy)
    }

    @Test
    fun anEndpointWithNoProxyIsDirect() {
        val profile = ApnRowSelector.select(listOf(ApnRow("mms", "https://mmsc.example.net/mms", null, null, "mms")))

        val endpoint = requireNotNull(profile).endpoint()

        assertTrue(endpoint is MmscEndpoint.Direct)
    }

    @Test
    fun noMmscMeansNoEndpoint() {
        assertNull(ApnProfile("mms", null, null, null, "mms").endpoint())
        assertNull(ApnProfile("mms", "   ", null, null, "mms").endpoint())
        assertNull(ApnProfile("mms", "mmsc.example.net", null, null, "mms").endpoint())
    }

    @Test
    fun anEndpointGetsAnExplicitRootPath() {
        assertEquals("http://mmsc.example.net/", ApnProfile("mms", "http://mmsc.example.net", null, null, "mms").endpoint()?.url)
    }

    @Test
    fun aPortAndQuerySurviveNormalisation() {
        val endpoint = ApnProfile("mms", "http://mmsc.example.net:8080/mms?a=1", null, null, "mms").endpoint()

        assertEquals("http://mmsc.example.net:8080/mms?a=1", endpoint?.url)
    }

    @Test
    fun theCacheQueriesTheTableOncePerSubscription() {
        var queries = 0
        val resolver = CachingApnResolver({ subscriptionId ->
            queries++
            ApnProfile("mms$subscriptionId", "http://mmsc.example.net", null, null, "mms")
        }, { -1 })

        repeat(5) { resolver.resolve(3) }

        assertEquals(1, queries)
        assertEquals("mms3", resolver.resolve(3)?.apnName)
    }

    @Test
    fun subscriptionsAreCachedSeparately() {
        var queries = 0
        val resolver = CachingApnResolver({ subscriptionId ->
            queries++
            ApnProfile("mms$subscriptionId", "http://mmsc.example.net", null, null, "mms")
        }, { -1 })

        resolver.resolve(1)
        resolver.resolve(2)
        resolver.resolve(1)

        assertEquals(2, queries)
        assertEquals("mms1", resolver.resolve(1)?.apnName)
        assertEquals("mms2", resolver.resolve(2)?.apnName)
    }

    @Test
    fun aMissingApnIsCachedToo() {
        var queries = 0
        val resolver = CachingApnResolver({ queries++; null }, { -1 })

        repeat(3) { assertNull(resolver.resolve(5)) }

        assertEquals("a missing APN must not be re-queried every attempt", 1, queries)
    }

    @Test
    fun invalidatingOneSubscriptionLeavesTheOthers() {
        val resolver = CachingApnResolver({ ApnProfile("mms", "http://mmsc.example.net", null, null, "mms") }, { -1 })
        resolver.resolve(1)
        resolver.resolve(2)

        resolver.invalidate(1)

        assertEquals("mms", resolver.resolve(2)?.apnName)
        assertEquals("still a valid profile", "mms", resolver.resolve(1)?.apnName)
    }

    @Test
    fun invalidatingEverythingForcesAFreshRead() {
        var queries = 0
        val resolver = CachingApnResolver({ queries++; ApnProfile("mms", "http://a", null, null, "mms") }, { -1 })
        resolver.resolve(1)

        resolver.invalidate()

        resolver.resolve(1)
        assertEquals(2, queries)
    }

    @Test
    fun aSubscriptionChangePicksUpTheNewApn() {
        val bySubscription = mutableMapOf(1 to "old", 2 to "new")
        val resolver = CachingApnResolver({ id -> ApnProfile(bySubscription[id], "http://m", null, null, "mms") }, { -1 })
        assertEquals("old", resolver.resolve(1)?.apnName)

        bySubscription[1] = "reprovisioned"
        resolver.invalidate()

        assertEquals("reprovisioned", resolver.resolve(1)?.apnName)
    }

    @Test
    fun aNonPositiveSubscriptionIdFallsBackToTheDefaultSmsSubscription() {
        val resolver = CachingApnResolver({ id -> ApnProfile("sub$id", "http://m", null, null, "mms") }, { 99 })

        assertEquals("sub99", resolver.resolve(-1)?.apnName)
        assertEquals("sub99", resolver.resolve(0)?.apnName)
        assertEquals("sub99", resolver.resolve(99)?.apnName)
    }

    @Test
    fun ipv4LeadingZerosAreStrippedPerOctet() {
        assertEquals("http://10.4.0.1/mms", normalizeMmscUrl("http://010.004.000.001/mms"))
        assertEquals("http://0.0.0.0/mms", normalizeMmscUrl("http://000.000.000.000/mms"))
    }

    @Test
    fun nonIpv4HostsAreUntouched() {
        assertEquals("https://mms.corp.example/mms", normalizeMmscUrl("https://mms.corp.example/mms"))
        assertEquals("http://mmsc.example.net/mms", normalizeMmscUrl("HTTP://MMSC.EXAMPLE.NET/mms"))
    }

    @Test
    fun aNonHttpSchemeIsRejected() {
        assertNull(normalizeMmscUrl("mms://mmsc.example.net"))
        assertNull(normalizeMmscUrl("ftp://mmsc.example.net"))
        assertNull(normalizeMmscUrl("mmsc.example.net/mms"))
        assertNull(normalizeMmscUrl(""))
    }
}