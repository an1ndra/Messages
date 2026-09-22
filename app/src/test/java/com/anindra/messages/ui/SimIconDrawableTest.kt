package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SimIconDrawableTest {
    private val main = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main") }
        .firstOrNull { it.isDirectory }
        ?: error("app/src/main not found")

    private val icons = listOf("ic_sim_1", "ic_sim_2")

    private fun drawable(name: String) = File(main, "res/drawable/$name.xml").readText()

    private fun pathData(xml: String): List<String> =
        Regex("android:pathData=\"([^\"]+)\"").findAll(xml).map { it.groupValues[1] }.toList()

    private fun colours(xml: String): List<String> =
        Regex("#[0-9A-Fa-f]{6,8}").findAll(xml).map { it.value }.toList()

    private fun subpaths(xml: String): List<String> =
        pathData(xml).single().split("M").drop(1).map { it.trim() }

    @Test
    fun simIconsStayTintable() {
        icons.forEach { name ->
            val xml = drawable(name)
            assertTrue("$name must be a filled vector", xml.contains("android:fillColor=\"#FF000000\""))
            assertTrue("$name must punch its number out", xml.contains("evenOdd"))
            assertEquals(
                "$name must only use the tint placeholder colour",
                listOf("#FF000000"),
                colours(xml).distinct()
            )
        }
    }

    @Test
    fun simIconsAreTwentyFourDp() {
        icons.forEach { name ->
            val xml = drawable(name)
            assertTrue(xml.contains("android:width=\"24dp\""))
            assertTrue(xml.contains("android:height=\"24dp\""))
            assertTrue(xml.contains("android:viewportWidth=\"24\""))
            assertTrue(xml.contains("android:viewportHeight=\"24\""))
        }
    }

    @Test
    fun simIconsDrawOneCardWithANumber() {
        icons.forEach { name ->
            assertEquals("$name should be one multi-subpath glyph", 1, pathData(drawable(name)).size)
            assertEquals("$name should have card + number subpaths", 2, subpaths(drawable(name)).size)
        }
    }

    @Test
    fun cardHasTheAngledCutAtTheTopRight() {
        icons.forEach { name ->
            assertTrue(
                "$name card should cut the top-right corner",
                subpaths(drawable(name))[0].contains("L15.162,1.6 L17.658,4.102")
            )
        }
    }

    @Test
    fun simIconsShareTheCardAndDifferOnlyInTheNumber() {
        val one = subpaths(drawable("ic_sim_1"))
        val two = subpaths(drawable("ic_sim_2"))
        assertEquals(one.first(), two.first())
        assertNotEquals(one[1], two[1])
    }
}
