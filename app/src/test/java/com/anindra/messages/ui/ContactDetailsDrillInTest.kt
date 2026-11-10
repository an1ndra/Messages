package com.anindra.messages.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A row that both toggles and drills in has to say so (#421 follow-up).
 *
 * The "Notification profile" row is a switch and, while the chat has a channel,
 * a way into Android's page for it. Nothing on screen said that: the row looked
 * exactly like the Bubbles switch beside it, which only toggles. The reporter
 * pointed at Android Settings, which marks the difference with a chevron and a
 * hairline before the switch.
 *
 * The affordance must follow the same boolean as the click, or it would promise
 * a page that does not exist while the channel is off.
 */
class ContactDetailsDrillInTest {
    private val screen: String by lazy { locate("ui/ContactDetailsScreen.kt") }

    @Test
    fun theRowTakesADrillInFlagThatDefaultsOff() {
        // Default-off keeps the switch-only rows (Bubbles, and any future one)
        // from advertising a page they do not have.
        assertTrue(
            "DetailCardRow must accept a drillIn flag",
            Regex("""(?:private|internal|public)?\s*fun DetailCardRow\([\s\S]{0,400}?drillIn: Boolean = false""")
                .containsMatchIn(screen),
        )
    }

    @Test
    fun theChevronAndHairlineAreDrawnOnlyForADrillInRow() {
        val affordance = screen.substringAfter("if (drillIn) {").substringBefore("trailing?.invoke()")
        assertTrue(
            "the drill-in row must show a chevron",
            affordance.contains("KeyboardArrowRight"),
        )
        assertTrue(
            "the chevron must be a hairline-separated sibling of the switch",
            Regex("""KeyboardArrowRight[\s\S]{0,600}?height\(24\.dp\)[\s\S]{0,400}?outlineVariant""")
                .containsMatchIn(affordance),
        )
        assertTrue(
            "the divider must be a hairline, not a bar",
            Regex("""width\(1\.dp\)[\s\S]{0,120}?height\(24\.dp\)""").containsMatchIn(affordance),
        )
        assertTrue(
            "the chevron is decoration: the row itself is the label",
            affordance.contains("contentDescription = null"),
        )
    }

    @Test
    fun theNotificationRowShowsItOnlyWhileTheChannelExists() {
        val row = screen.substringAfter("R.string.contact_notification_profile")
            .substringBefore("contact_bubble_conversation")
        assertTrue(
            "the flag must follow the channel state, the same boolean that gates onClick",
            row.contains("drillIn = channelOn"),
        )
        assertTrue(
            "and the click must still be gated on it",
            Regex("""onClick = if \(channelOn\)""").containsMatchIn(row),
        )
    }

    @Test
    fun theBubblesRowDoesNotAdvertiseADrillIn() {
        // It is a plain switch: a chevron there would open nothing.
        assertEquals(
            "only the notification row may claim to open a page",
            1,
            Regex("""drillIn = """).findAll(screen).count(),
        )
        val bubbles = screen.substringAfter("R.string.contact_bubble_conversation")
            .substringBefore("contact_block_report")
        assertFalse(
            "the bubbles row must stay a plain switch",
            bubbles.contains("drillIn"),
        )
    }

    private fun locate(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "app/src/main/java/com/anindra/messages/$relative")
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        error("could not locate $relative")
    }
}
