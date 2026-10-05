package com.anindra.messages.ui.previews

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Previews must not reach a release build.
 *
 * `@Preview` comes from `ui-tooling-preview`, which is an `implementation`
 * dependency, so a preview written into `src/main` compiles into every variant
 * and only the renderer is stripped. Keeping them in `src/debug` is what makes
 * them development-only, and that is a property of where the file lives rather
 * than of anything the compiler enforces -- so it is checked here.
 */
class PreviewPlacementTest {

    private val app = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app") }
        .firstOrNull { File(it, "src").isDirectory }
        ?: error("app/src not found")

    private fun kotlinFiles(dir: File): List<File> =
        dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun noPreviewAnnotationLivesInTheMainSourceSet() {
        val offenders = kotlinFiles(File(app, "src/main"))
            .filter { it.readText().contains("@Preview") }
            .map { it.relativeTo(app).path }
        assertEquals(
            "these would compile into release builds: $offenders",
            emptyList<String>(),
            offenders
        )
    }

    @Test
    fun thePreviewsLiveInTheDebugSourceSet() {
        val previews = kotlinFiles(File(app, "src/debug"))
            .filter { it.readText().contains("@Preview") }
        assertTrue("no previews found under src/debug", previews.isNotEmpty())
    }

    @Test
    fun everyPreviewFileIsKotlinSource() {
        val debug = File(app, "src/debug")
        assertTrue("src/debug does not exist", debug.isDirectory)
        assertTrue(kotlinFiles(debug).all { it.extension == "kt" })
    }
}