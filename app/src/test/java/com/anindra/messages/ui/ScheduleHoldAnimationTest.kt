package com.anindra.messages.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holding Send shrinks the circle, then the date picker opens. The gesture had
 * no feedback before: the press did nothing visible until a calendar appeared
 * under the finger.
 */
class ScheduleHoldAnimationTest {

    private val app: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app") }
            .firstOrNull { File(it, "src").isDirectory }
            ?: error("app/src not found")
    }

    private val chat get() = File(
        app, "src/main/java/com/anindra/messages/ui/ChatScreen.kt"
    ).readText()

    @Test
    fun holdingShrinksTheCircle() {
        assertTrue(
            "the send circle must scale down while held",
            chat.contains("targetValue = if (holding) 0.8f else 1f")
        )
        assertTrue(
            "and the scale must be applied to the button",
            chat.contains("scaleX = holdScale") && chat.contains("scaleY = holdScale")
        )
    }

    @Test
    fun theCalendarGrowsOutOfTheSendButtonsCorner() {
        assertTrue(
            "the picker must animate itself, not rely on a centred dialog",
            chat.contains("fun ScheduleDialog(")
        )
        assertTrue(
            "scaling from the bottom-right, where Send sits",
            chat.contains("transformOrigin = TransformOrigin(1f, 1f)")
        )
        assertTrue(
            "and it must start small rather than appear",
            chat.contains("targetValue = if (shown) 1f else 0.35f")
        )
    }

    @Test
    fun theShrinkStaysGentle() {
        assertTrue(
            "0.45 made the button nearly vanish; 0.8 reads as a press",
            chat.contains("targetValue = if (holding) 0.8f else 1f")
        )
    }

    @Test
    fun thePickerOpensOnlyAfterTheShrinkHasPlayed() {
        assertTrue(
            "the picker waits for the animation plus a beat",
            chat.contains("delay(Motion.DURATION_MEDIUM1.toLong() + 220L)")
        )
        assertTrue(
            "and only then opens the picker",
            chat.contains("android.util.Log.d(\"ScheduleHold\", \"shrunk, opening the picker\")")
        )
    }

    @Test
    fun theLongPressDrivesIt() {
        assertTrue(
            "hold-Send must set the holding state rather than open the picker directly",
            chat.contains("onLongClick = if (schedulingEnabled) ({ holding = true }) else null")
        )
    }

    @Test
    fun reduceMotionStillSkipsTheShrink() {
        assertTrue(
            "the scale goes through the shared spec, which snaps under reduce motion",
            chat.contains("animationSpec = motionTween(sendReduceMotion, Motion.DURATION_MEDIUM1)")
        )
    }
}
