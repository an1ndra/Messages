package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SimIconDrawableTest {
    private val main = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main") }
        .firstOrNull { it.isDirectory }
        ?: error("app/src/main not found")

    private val icons = listOf("ic_sim_vector")

    private fun drawable(name: String) = File(main, "res/drawable/$name.xml").readText()

    private fun pathData(xml: String): List<String> =
        Regex("android:pathData=\"([^\"]+)\"").findAll(xml).map { it.groupValues[1] }.toList()

    private fun colours(xml: String): List<String> =
        Regex("#[0-9A-Fa-f]{6,8}").findAll(xml).map { it.value }.toList()

    @Test
    fun simIconIsTintable() {
        icons.forEach { name ->
            val xml = drawable(name)
            assertTrue("$name must be a filled vector", xml.contains("android:fillColor=\"#FF000000\""))
            assertEquals(
                "$name must only use the tint placeholder colour",
                listOf("#FF000000"),
                colours(xml).distinct()
            )
        }
    }

    @Test
    fun simIconIsTwentyFourDp() {
        icons.forEach { name ->
            val xml = drawable(name)
            assertTrue(xml.contains("android:width=\"24dp\""))
            assertTrue(xml.contains("android:height=\"24dp\""))
            assertTrue(xml.contains("android:viewportWidth=\"24\""))
            assertTrue(xml.contains("android:viewportHeight=\"24\""))
        }
    }

    @Test
    fun simIconHasSimCardShape() {
        icons.forEach { name ->
            val xml = drawable(name)
            val data = pathData(xml)
            assertEquals("$name should have one path", 1, data.size)
            assertTrue(
                "$name card should have angled cut at top-right",
                data[0].contains("L9.835,1.173 L3.338,6.669")
            )
        }
    }

    @Test
    fun oldIconsAreRemoved() {
        listOf("ic_sim_1", "ic_sim_2", "ic_dual_sim").forEach { name ->
            val file = File(main, "res/drawable/$name.xml")
            assertTrue("$name should be deleted", !file.exists())
        }
    }
}
