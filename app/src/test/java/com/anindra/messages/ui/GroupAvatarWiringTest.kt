package com.anindra.messages.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A group's picture is chosen in one place and drawn in three.
 *
 * The contact page owns the picker, the app copies the bytes into its own files
 * (a picker grant dies with the process), and the list, the chat header and the
 * contact page all draw the same stored path. These pin the wiring so a screen
 * that was missed shows up in a unit test rather than as a group with a
 * generated avatar next to one with a photo.
 */
class GroupAvatarWiringTest {
    private val screen: String by lazy { locate("ui/ContactDetailsScreen.kt") }
    private val list: String by lazy { locate("ui/ConversationsScreen.kt") }
    private val chat: String by lazy { locate("ui/ChatScreen.kt") }
    private val main: String by lazy { locate("MainActivity.kt") }
    private val repo: String by lazy { locate("data/Repository.kt") }

    private fun locate(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "app/src/main/java/com/anindra/messages/$relative")
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        error("could not locate $relative")
    }

    @Test
    fun theContactPageOffersAPhotoPickerForAGroup() {
        assertTrue(
            "the contact page must open the system photo picker",
            screen.contains("ActivityResultContracts.PickVisualMedia()"),
        )
        val avatarBlock = screen.substringAfter("Box(contentAlignment = Alignment.BottomEnd)")
        assertTrue("only a group gets the affordance", avatarBlock.contains("if (isGroup)"))
        assertTrue("the affordance is labelled", avatarBlock.contains("contact_group_photo"))
        assertTrue(
            "the picked image must be handed to the app, not stored as a URI",
            screen.contains("vm.setGroupAvatar(conversationId, picked)"),
        )
    }

    @Test
    fun thePickedImageIsCopiedBeforeItIsStored() {
        val body = main.substringAfter("fun setGroupAvatar(").substringBefore("\n    }")
        assertTrue("the bytes must be copied into app files", body.contains("GroupAvatarStore.save("))
        assertTrue("the stored value is the copy", body.contains("repo.setGroupAvatar(conversationId, saved)"))
        assertTrue("the replaced copy is dropped", body.contains("GroupAvatarStore.delete(previous)"))
    }

    @Test
    fun everySurfaceDrawsTheGroupPicture() {
        assertTrue("the inbox row", list.contains("photoModel = convo.groupAvatar"))
        assertTrue("the chat header", chat.contains("photoModel = convo?.groupAvatar"))
        assertTrue("the contact page", screen.contains("photoModel = groupAvatar"))
    }

    @Test
    fun theAvatarIsStoredOnTheConversationAndReadBack() {
        assertTrue("the column is written", repo.contains("put(\"group_avatar\""))
        assertTrue("the column is read", repo.contains("SELECT group_avatar FROM conversations"))
        assertTrue("the list query carries it", repo.contains("c.group_avatar"))
    }
}
