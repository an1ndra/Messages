package com.anindra.messages.ui.previews

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every visual composable in `src/main` should be reachable from a preview.
 *
 * The point is not that each one has its own `@Preview` — several are shown
 * inside a larger preview because that is how a user sees them — but that no
 * composable is left with no preview referencing it at all. A component added
 * and never previewed is the case this catches.
 *
 * Screens that take an `AppViewModel` are exempt: Android Studio cannot
 * construct one, so previewing them would mean maintaining a fake that drifts
 * from the real ViewModel.
 */
class PreviewCoverageTest {

    private val app = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app") }
        .firstOrNull { File(it, "src").isDirectory }
        ?: error("app/src not found")

    private val previewSource: String by lazy {
        File(app, "src/debug").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
    }

    /** Screens the user reaches by navigating, which take an AppViewModel. */
    private val viewModelScreens = setOf(
        "AccessibilityScreen", "AddPeopleScreen", "AdvancedSettingsScreen",
        "AutoDeleteSettingsScreen", "ChatScreen", "ContactDetailsScreen",
        "ConversationsScreen", "InboxSettingsScreen", "LinkSettingsScreen",
        "NewChatScreen", "NotificationSettingsScreen", "ScheduledMessagesScreen",
        "SettingsScreen", "SpamBlockedScreen", "TrashScreen"
    )

    /** Composables that render no UI of their own. */
    private val notVisual = setOf(
        "MessagesTheme", "ProvideShimmer", "ClampToItemCount", "SettingsJumpEffect",
        "rememberChatRows", "rememberSentIndexAt", "rememberLinkedText",
        "fontScaleLabel", "fontLabel", "blockedKeywordsSubtitle", "mmsCheckSubtitle"
    )

    private fun composables(): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        File(app, "src/main").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val lines = file.readText().lines()
                for (index in lines.indices) {
                    val line = lines[index]
                    val m = Regex("^\\s*(?:internal |private |public )?fun ([A-Z]\\w*)\\(").find(line)
                        ?: continue
                    val name = m.groupValues[1]
                    if (name in viewModelScreens || name in notVisual) continue
                    // Collect the parameter list to confirm it is previewable.
                    val decl = StringBuilder()
                    var i = index
                    while (i < lines.size && decl.length < 4000) {
                        decl.appendLine(lines[i])
                        if (lines[i].trimEnd().endsWith("{")) break
                        i++
                    }
                    if (Regex("\\b(?:vm|viewModel)\\s*:\\s*\\S*ViewModel").containsMatchIn(decl)) {
                        continue
                    }
                    out += name to "${file.name}:${index + 1}"
                }
            }
        return out
    }

    @Test
    fun everyVisualComposableIsReferencedBySomePreview() {
        val uncovered = composables()
            .filter { (name, _) ->
                !Regex("\\b" + Regex.escape(name) + "\\b").containsMatchIn(previewSource)
            }
            .map { (name, where) -> "$where $name" }
        assertEquals(
            "these composables have no preview referencing them:\n  " + uncovered.joinToString("\n  "),
            emptyList<String>(),
            uncovered
        )
    }

    @Test
    fun thereArePreviewsToCheck() {
        // Guards the test above: if the previews were deleted or moved out of
        // the debug source set, the coverage test would otherwise pass vacuously.
        assertTrue(
            "no @Preview found under src/debug",
            previewSource.contains("@Preview")
        )
        assertTrue(
            "previews should be annotated with @PreviewLightDark",
            previewSource.contains("@PreviewLightDark")
        )
    }

    @Test
    fun lightAndDarkAreBothCovered() {
        // @PreviewLightDark sets uiMode, and the theme's "system" branch is what
        // follows it. If a preview passed an explicit light/dark mode instead,
        // the annotation would stop having any effect.
        assertTrue(
            "MessagesTheme should be left on \"system\" so @PreviewLightDark drives it",
            previewSource.contains("mode = \"system\"")
        )
    }
}
