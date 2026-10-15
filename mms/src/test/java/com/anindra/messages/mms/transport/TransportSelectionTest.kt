package com.anindra.messages.mms.transport

import com.anindra.messages.mms.ScriptedTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The registry maps a transport to the choice its id names, so these pin that it
 * hands the platform transport back and refuses one it cannot name at all.
 */
class TransportSelectionTest {

    private val system = ScriptedTransport(id = TransportChoice.SYSTEM.id)

    @Test
    fun thePlatformPathIsTheOnlyChoiceAndTheDefault() {
        assertEquals(TransportChoice.SYSTEM, TransportChoice.DEFAULT)
        assertEquals(TransportChoice.SYSTEM, TransportSelection().choiceFor(1))
    }

    @Test
    fun aRegistryHandsBackTheTransportItsChoiceNames() {
        val registry = TransportRegistry(listOf(system))
        assertSame(system, registry.assign(1)?.transport)
        assertEquals(TransportChoice.SYSTEM, registry.assign(1)?.choice)
    }

    @Test
    fun anUnregisteredChoiceHasNoBinding() {
        val registry = TransportRegistry(emptyList())
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
    fun aBindingCarriesWhatItsTransportDeclaredItOwns() {
        val platform = ScriptedTransport(
            id = TransportChoice.SYSTEM.id,
            reportsThroughPendingIntent = true,
            ownsNotificationRow = true,
        )
        val binding = TransportRegistry(listOf(platform)).assign(1)!!

        assertTrue(binding.reportsThroughPendingIntent)
        assertTrue(binding.ownsNotificationRow)
    }

    @Test
    fun aChoiceIsNamedByItsOwnId() {
        assertEquals(TransportChoice.SYSTEM, TransportChoice.of(TransportChoice.SYSTEM.id))
        assertEquals(TransportChoice.SYSTEM, TransportChoice.of(" System "))
        assertNull(TransportChoice.of("telepathy"))
    }
}
