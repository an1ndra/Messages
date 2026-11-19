package com.anindra.messages.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The inbox used to keep its skeleton (and the progress bar above it) while an
 * import ran. After an update the persisted "first import done" flag is already
 * true, so gating the skeleton on that flag alone skipped it and left an empty,
 * black screen while the re-import worked through thousands of threads.
 */
class InboxSkeletonTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private fun src(path: String) = File(main, path).readText()

    @Test
    fun aSyncInFlightKeepsTheSkeletonOnBothScreens() {
        listOf(
            "java/com/anindra/messages/ui/ConversationsScreen.kt",
            "java/com/anindra/messages/ui/legacy/LegacyConversationsScreen.kt"
        ).forEach { path ->
            val screen = src(path)
            assertTrue(
                "$path: the skeleton must survive a running import",
                Regex("""val loaded = minSkeletonShown && syncProgress == null""").containsMatchIn(screen)
            )
            assertTrue(
                "$path: and the progress bar is still wired to the same state",
                screen.contains("syncProgress?.let")
            )
        }
    }

    @Test
    fun theFlagAloneIsNoLongerTreatedAsLoaded() {
        val screen = src("java/com/anindra/messages/ui/ConversationsScreen.kt")
        assertTrue(
            "the persisted flag alone must not hide the loading state",
            !Regex("""val loaded = minSkeletonShown && \(syncDone \|\| !readSmsAllowed\)""")
                .containsMatchIn(screen)
        )
    }
}
