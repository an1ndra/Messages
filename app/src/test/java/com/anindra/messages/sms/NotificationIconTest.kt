package com.anindra.messages.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NotificationIconTest {
    private val main = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main") }
        .firstOrNull { it.isDirectory }
        ?: error("app/src/main not found")

    private fun drawable(name: String) = File(main, "res/drawable/$name.xml").readText()

    private fun pathData(xml: String): List<String> =
        Regex("android:pathData=\"([^\"]+)\"").findAll(xml).map { it.groupValues[1] }.toList()

    private fun attr(xml: String, name: String): Double =
        Regex("android:$name=\"(-?[0-9.]+)\"").find(xml)!!.groupValues[1].toDouble()

    private fun fillRatio(xml: String): Double {
        val coords = pathData(xml)
            .flatMap { Regex("-?[0-9]+(\\.[0-9]+)?").findAll(it).map { m -> m.value.toDouble() }.toList() }
        val scale = attr(xml, "scaleX")
        val translate = attr(xml, "translateX")
        val min = coords.min() * scale + translate
        val max = coords.max() * scale + translate
        return (max - min) / attr(xml, "viewportWidth")
    }

    @Test
    fun notificationReusesTheLauncherBubbleArtwork() {
        val bubble = pathData(drawable("ic_launcher_foreground")).first()
        val combined = pathData(drawable("ic_stat_message")).single()
        assertTrue(combined.startsWith(bubble))
    }

    @Test
    fun notificationLinesAreCutOutSoTheySurviveTinting() {
        val xml = drawable("ic_stat_message")
        assertTrue(xml.contains("android:fillType=\"evenOdd\""))
        assertFalse(xml.contains("strokeColor"))
        val subpaths = Regex("M").findAll(pathData(xml).single()).count()
        assertEquals(4, subpaths)
    }

    @Test
    fun notificationGlyphFillsTheStatusBarCanvas() {
        assertTrue(fillRatio(drawable("ic_stat_message")) >= 0.85)
    }

    @Test
    fun launcherGlyphStaysInsideTheAdaptiveSafeZone() {
        assertTrue(fillRatio(drawable("ic_launcher_foreground")) <= 0.6)
    }

    @Test
    fun notificationViewportIsTwentyFour() {
        val xml = drawable("ic_stat_message")
        assertEquals(24.0, attr(xml, "viewportWidth"), 0.0)
        assertEquals(24.0, attr(xml, "viewportHeight"), 0.0)
        assertTrue(xml.contains("android:width=\"24dp\""))
        assertTrue(xml.contains("android:height=\"24dp\""))
    }

    @Test
    fun notificationsUseTheEnlargedIcon() {
        val source = File(main, "java/com/anindra/messages/sms/SmsSupport.kt").readText()
        assertEquals(2, Regex("setSmallIcon\\(R\\.drawable\\.ic_stat_message\\)").findAll(source).count())
        assertFalse(source.contains("setSmallIcon(R.drawable.ic_launcher_foreground)"))
    }
}
