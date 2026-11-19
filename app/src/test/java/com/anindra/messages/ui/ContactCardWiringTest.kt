package com.anindra.messages.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Contact tile sends a card, and the chat draws it as a card.
 *
 * Picking a contact used to paste its vCard into the body of an SMS, so the
 * receiving phone showed text. The tile has to write the card to a file, send it
 * as the contact kind (which the send path declares `text/x-vcard`), and the
 * bubble has to stop drawing the raw properties.
 */
class ContactCardWiringTest {

    private fun source(relative: String): String {
        val app = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app") }
            .firstOrNull { File(it, "src").isDirectory }
            ?: error("app/src not found")
        return File(app, "src/main/java/com/anindra/messages/$relative").readText()
    }

    private val chat: String by lazy { source("ui/ChatScreen.kt") }

    @Test
    fun theContactTileSendsACardFile() {
        val launcher = chat.substringAfter("val contactLauncher").substringBefore("fun launchOrExplain")
        assertTrue(
            "the picked contact must be written out as a card file",
            launcher.contains("ContactShare.writeCard(")
        )
        assertTrue(
            "and sent as the contact kind, which is what declares text/x-vcard",
            launcher.contains("vm.sendMediaMessage(conversationId, card, \"\", MmsSupport.KIND_CONTACT)")
        )
    }

    @Test
    fun aContactThatCannotBeReadExplainsItself() {
        val launcher = chat.substringAfter("val contactLauncher").substringBefore("fun launchOrExplain")
        assertTrue(
            "a denied contacts permission used to make the tile do nothing at all",
            launcher.contains("R.string.chat_contact_unavailable")
        )
    }

    @Test
    fun theChatDrawsAContactAsACard() {
        assertTrue(
            "a contact row must render the card, not the raw vCard body",
            chat.contains("msg.mediaType == MmsSupport.KIND_CONTACT")
        )
        assertTrue(chat.contains("ContactBubble("))
    }

    @Test
    fun tappingACardOffersToKeepIt() {
        assertTrue(
            "a card is only worth sending if the receiver can keep it",
            chat.contains("ContactCardDialog(")
        )
        val dialog = source("ui/ContactCardDialog.kt")
        assertTrue("add to contacts", dialog.contains("R.string.chat_add_to_contacts"))
        assertTrue("save the file", dialog.contains("R.string.chat_save_contact"))
        assertTrue(
            "and the save writes the card through the attachment saver",
            chat.contains("vm.saveMessageImage(card.id)")
        )
        assertTrue(
            "the add action is built from the card, guarded for a missing app",
            chat.contains("ContactActions.addIntent(name, number)")
        )
    }

    @Test
    fun theCardBubbleShowsTheNameAndNumber() {
        val bubble = source("ui/ContactBubble.kt")
        assertTrue("the name", bubble.contains("name"))
        assertTrue("the number", bubble.contains("number"))
        assertTrue(
            "read back from the attachment, so it survives the cache file",
            bubble.contains("ContactShare.readCard(")
        )
        assertFalse(
            "no raw card properties in the bubble",
            bubble.contains("VERSION:3.0") || bubble.contains("TEL;TYPE")
        )
    }
}
