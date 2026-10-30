package com.anindra.messages.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The avatar in the app's own header (and the empty state) should be the user's
 * device profile picture when they have set one, falling back to the themed
 * silhouette otherwise — the screenshot in the report showed the silhouette even
 * though the device had a profile photo.
 */
class OwnerAvatarTest {

    private val app: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app") }
            .firstOrNull { File(it, "src").isDirectory }
            ?: error("app/src not found")
    }

    private fun read(rel: String) = File(app, rel).readText()

    private val components get() = read("src/main/java/com/anindra/messages/ui/Components.kt")
    private val list get() = read("src/main/java/com/anindra/messages/ui/ConversationsScreen.kt")

    @Test
    fun itReadsTheProfileRowNotAPhoneLookup() {
        assertTrue(
            "the owner's photo lives on ContactsContract.Profile",
            components.contains("ContactsContract.Profile.CONTENT_URI")
        )
        assertTrue(
            "and it is read through the same PHOTO_URI column",
            components.contains("CommonDataKinds.Photo.PHOTO_URI")
        )
    }

    @Test
    fun itFallsBackToTheSilhouette() {
        assertTrue(
            "no profile photo must keep the current default",
            components.contains("PersonAvatar(\n            stringResource(R.string.conversations_me),")
        )
    }

    @Test
    fun bothOwnerAvatarsUseIt() {
        assertTrue(
            "the header avatar",
            list.contains("OwnerAvatar(\n                                size = 32.dp,")
        )
        assertTrue(
            "and the empty-state avatar",
            list.contains("OwnerAvatar(\n                        size = 72.dp,")
        )
    }

    @Test
    fun theEmptystateKeepsItsOwnColours() {
        assertTrue(
            "the fallback keeps primaryContainer there, not primary",
            list.contains("backgroundColor = MaterialTheme.colorScheme.primaryContainer")
        )
    }
}
