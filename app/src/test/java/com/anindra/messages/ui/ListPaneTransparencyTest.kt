package com.anindra.messages.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * On a phone the conversation list is drawn underneath the route stack, so the
 * stack's "list" pane must stay transparent. It once painted an opaque surface
 * there and covered the whole inbox: the list was still composed and clickable,
 * but the screen was black.
 */
class ListPaneTransparencyTest {

    private val activity: String by lazy {
        val main = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
        File(main, "java/com/anindra/messages/MainActivity.kt").readText()
    }

    @Test
    fun theListPaneIsOnlyOpaqueInASplitWindow() {
        val branch = activity.substringAfter("""if (target == "list") {""")
        val splitPart = branch.substringBefore("} else {")
        val phonePart = branch.substringAfter("} else {").substringBefore("} else {")
        assertTrue(
            "the opaque pane and its empty-state card belong to the split window",
            splitPart.contains("if (twoPaneLayout)") &&
                splitPart.contains("background(MaterialTheme.colorScheme.surface)") &&
                splitPart.contains("R.string.two_pane_empty")
        )
        assertTrue(
            "a phone gets a transparent pane, or the list underneath is hidden",
            phonePart.contains("Box(Modifier.fillMaxSize())") &&
                !phonePart.contains("colorScheme.surface")
        )
    }
}
