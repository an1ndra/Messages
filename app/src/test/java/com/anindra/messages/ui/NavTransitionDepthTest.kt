package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `AnimatedContent` picks its slide direction from `routeDepth`. A route absent
 * from that map falls back to 0, so opening it from any nested screen reads as
 * `to < from` and slides in backwards — the page still opens, it just animates
 * as though you were going up a level. That is invisible in review and only
 * shows up on screen, so the map is checked against the nav map here instead.
 */
class NavTransitionDepthTest {

    private val main = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main") }
        .firstOrNull { it.isDirectory }
        ?: error("app/src/main not found")

    private val source = File(main, "java/com/anindra/messages/MainActivity.kt").readText()

    private val routeDepth: Map<String, Int>
        get() {
            val block = source.substringAfter("val routeDepth = ").substringBefore(")")
            return Regex("\"([a-z-]+)\"\\s+to\\s+(\\d+)").findAll(block)
                .associate { it.groupValues[1] to it.groupValues[2].toInt() }
        }

    /** Route branches of the AnimatedContent body, ignoring theme-mode `when`s. */
    private val navRoutes: Set<String>
        get() {
            val body = source.substringAfter("AnimatedContent(")
            return Regex("\"([a-z-]+)\"\\s*->").findAll(body)
                .map { it.groupValues[1] }
                .filter { it !in NON_ROUTE_WHEN_KEYS }
                .toSet()
        }

    @Test
    fun everyRouteHasADeepness() {
        val missing = navRoutes - routeDepth.keys
        assertEquals(
            "routes with no routeDepth entry; they animate as if going back",
            emptyList<String>(),
            missing.toList().sorted(),
        )
    }

    @Test
    fun routeDepthCoversWhatTheTransitionReads() {
        // "list" is rendered by an `if`, not a branch, but AnimatedContent still
        // transitions into it, so it needs a depth like any other route.
        assertTrue("list must have a depth", routeDepth.containsKey("list"))
        assertTrue("nav routes were found", navRoutes.isNotEmpty())
    }

    @Test
    fun settingsSubScreensAreDeeperThanTheirParent() {
        val children = mapOf(
            "advanced" to listOf("accessibility", "auto-delete", "links", "mms-check", "notif-settings"),
            "details" to listOf("add-people"),
        )
        for ((parent, kids) in children) {
            for (kid in kids) {
                assertTrue(
                    "$parent is not in routeDepth",
                    routeDepth.containsKey(parent),
                )
                assertTrue(
                    "$kid is not in routeDepth",
                    routeDepth.containsKey(kid),
                )
                assertTrue(
                    "$kid should be deeper than $parent",
                    routeDepth.getValue(kid) > routeDepth.getValue(parent),
                )
            }
        }
    }

    private companion object {
        /** `when` branches on these are theme/intent modes, not navigation routes. */
        val NON_ROUTE_WHEN_KEYS = setOf("dark", "light", "system")
    }
}