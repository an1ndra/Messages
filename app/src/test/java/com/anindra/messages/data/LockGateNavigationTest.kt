package com.anindra.messages.data

import com.anindra.messages.i18n.LocaleCatalog
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #354 round 7: unlocking a bubble showed an empty conversation.
 *
 * The gate answers "no" by making the whole content subtree leave composition.
 * Everything the current destination is made of — the conversation id, its
 * search query, the details target — was remembered *inside* that subtree, so
 * it was discarded, while the route itself lives on the Activity. After the
 * prompt the window recomposed on `chat` with no conversation to show.
 *
 * The destination therefore has to live at Activity scope, like `navRoute`.
 * These cases pin the declaration site, because that placement is the fix: a
 * `remember` that looks equivalent behaves differently when the gate closes.
 */
class LockGateNavigationTest {

    private fun activity(): String = File(
        LocaleCatalog.repoRoot(),
        "app/src/main/java/com/anindra/messages/MainActivity.kt"
    ).readText()

    private fun assertActivityScoped(source: String, name: String) {
        assertTrue(
            "$name is remembered inside the gated composition; a re-lock discards it",
            Regex("private\\s+var\\s+$name\\s+by\\s+androidx\\.compose\\.runtime\\.mutableStateOf")
                .containsMatchIn(source)
        )
    }

    @Test
    fun theOpenConversationSurvivesTheGateLeavingComposition() {
        val source = activity()
        assertActivityScoped(source, "chatId")
        assertActivityScoped(source, "chatSearchQuery")
        assertFalse(
            "chatId is still remembered inside the gated content",
            source.contains("var chatId by remember")
        )
    }

    @Test
    fun theOtherDestinationsSurviveAsWell() {
        val source = activity()
        assertActivityScoped(source, "detailsId")
        assertActivityScoped(source, "addPeopleReturn")
    }
}
