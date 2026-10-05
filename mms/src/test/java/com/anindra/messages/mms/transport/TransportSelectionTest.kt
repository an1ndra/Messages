package com.anindra.messages.mms.transport

import com.anindra.messages.mms.ScriptedTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The selection is a setting, so these pin which of the two overrides wins and
 * what the default is -- the two things that go wrong silently when a
 * dual-SIM device picks the wrong carrier's path.
 */
class TransportSelectionTest {

    private val system = ScriptedTransport(id = TransportChoice.SYSTEM.id)
    private val direct = ScriptedTransport(id = TransportChoice.DIRECT.id)
    private val transports = listOf(system, direct)

    @Test
    fun theDefaultIsThePlatformPath() {
        assertEquals(TransportChoice.SYSTEM, TransportChoice.DEFAULT)
        assertEquals(TransportChoice.SYSTEM, TransportSelection().choiceFor(1))
    }

    @Test
    fun anExplicitOverrideReplacesTheDefault() {
        val selection = TransportSelection(default = TransportChoice.DIRECT)
        assertEquals(TransportChoice.DIRECT, selection.choiceFor(1))
        assertEquals(TransportChoice.DIRECT, selection.choiceFor(7))
    }

    @Test
    fun aPerSubscriptionPinBeatsTheGlobalOverride() {
        val selection = TransportSelection(
            default = TransportChoice.DIRECT,
            overrides = mapOf(2 to TransportChoice.SYSTEM),
        )
        assertEquals(TransportChoice.SYSTEM, selection.choiceFor(2))
        assertEquals(TransportChoice.DIRECT, selection.choiceFor(3))
    }

    @Test
    fun aRegistryHandsBackTheTransportItsChoiceNames() {
        val registry = TransportRegistry(transports, TransportSelection(default = TransportChoice.DIRECT))
        assertSame(direct, registry.assign(1)?.transport)
        assertEquals(TransportChoice.DIRECT, registry.assign(1)?.choice)
    }

    @Test
    fun aRegistryHonoursAPerSubscriptionPin() {
        val registry = TransportRegistry(
            transports,
            TransportSelection(default = TransportChoice.DIRECT, overrides = mapOf(4 to TransportChoice.SYSTEM)),
        )
        assertSame(system, registry.assign(4)?.transport)
        assertSame(direct, registry.assign(5)?.transport)
    }

    @Test
    fun aBindingCarriesWhatItsTransportDeclaredItOwns() {
        val platform = ScriptedTransport(
            id = TransportChoice.SYSTEM.id,
            reportsThroughPendingIntent = true,
            ownsNotificationRow = true,
        )
        val listener = ScriptedTransport(id = TransportChoice.DIRECT.id)
        val registry = TransportRegistry(listOf(platform, listener))

        val platformBinding = registry.assign(1)!!
        assertTrue(platformBinding.reportsThroughPendingIntent)
        assertTrue(platformBinding.ownsNotificationRow)

        val listenerBinding = TransportRegistry(listOf(listener), TransportSelection(default = TransportChoice.DIRECT))
            .assign(1)!!
        assertTrue(!listenerBinding.reportsThroughPendingIntent)
        assertTrue(!listenerBinding.ownsNotificationRow)
    }

    @Test
    fun anUnregisteredChoiceHasNoBinding() {
        val registry = TransportRegistry(listOf(system), TransportSelection(default = TransportChoice.DIRECT))
        assertNull(registry.assign(1))
    }

    @Test
    fun aTransportIdThatIsNotAChoiceIsRefused() {
        val failure = runCatching {
            TransportRegistry(listOf(ScriptedTransport(id = "carrier-magic")))
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun aChoiceIsNamedByItsOwnId() {
        assertEquals(TransportChoice.DIRECT, TransportChoice.of(TransportChoice.DIRECT.id))
        assertEquals(TransportChoice.SYSTEM, TransportChoice.of(" System "))
        assertNull(TransportChoice.of("telepathy"))
    }
}