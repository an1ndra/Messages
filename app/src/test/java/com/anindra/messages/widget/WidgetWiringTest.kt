package com.anindra.messages.widget

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Taps on the widgets are Intents, and a widget's RemoteViews cannot be dumped
 * from a device, so the wiring is asserted from the source and the end-to-end
 * routing on a device is covered by scripts/test-widgets.sh.
 */
class WidgetWiringTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private fun src(path: String): String = File(main, path).readText()

    private fun body(path: String, funName: String): String {
        val text = src(path)
        val start = text.indexOf("fun $funName(")
        assertTrue("$funName is not declared in $path", start >= 0)
        val end = text.indexOf("\n}", start)
        assertTrue("$funName has no body end in $path", end > start)
        return text.substring(start, end)
    }

    private val ui = "java/com/anindra/messages/widget/WidgetUi.kt"
    private val unread = "java/com/anindra/messages/widget/UnreadWidget.kt"

    /** The spec's "title → app inbox": tapping Messages must land on the inbox. */
    @Test
    fun theInboxTitleOpensTheInbox() {
        val header = body(ui, "WidgetListHeader")
        assertTrue(
            "the header title must launch the inbox",
            header.contains("WidgetIntents.inbox(")
        )
    }

    /** A locked widget opens the inbox, not whatever chat the app was left in. */
    @Test
    fun theLockedCardOpensTheInbox() {
        val card = body(ui, "WidgetLockedCard")
        assertTrue("the locked card must launch the inbox", card.contains("WidgetIntents.inbox("))
        assertTrue("the locked card must not bare-launch the app", !card.contains("WidgetIntents.openApp("))
    }

    /** An empty inbox still offers a way to start a chat. */
    @Test
    fun theEmptyStateStillOffersCompose() {
        val card = body(ui, "MessagesListCard")
        val empty = card.indexOf("snapshot.list.isEmpty() ->")
        val otherwise = card.indexOf("else ->", empty)
        assertTrue("no empty-state branch", empty > 0 && otherwise > empty)
        assertTrue(
            "the empty state must keep the header with its compose button",
            card.substring(empty, otherwise).contains("WidgetListHeader()")
        )
    }

    /** Two conversations can share an address (a group copies its primary's), so rows route by id. */
    @Test
    fun rowsRouteByConversationId() {
        for (path in listOf(ui, unread)) {
            val text = src(path)
            assertTrue("$path must route rows by conversation id", text.contains("item.id"))
            assertTrue("$path must not route rows by address", !text.contains("WidgetIntents.conversation(LocalContext.current, item.address"))
        }
    }

    /** MainActivity has to turn that id back into the chat. */
    @Test
    fun theActivityOpensARoutedConversationById() {
        val activity = src("java/com/anindra/messages/MainActivity.kt")
        assertTrue(
            "MainActivity must read the widget's conversation id",
            activity.contains("WidgetIntents.EXTRA_OPEN_CONVERSATION_ID")
        )
        assertTrue(
            "MainActivity must open the chat the id names",
            activity.contains("pendingOpenConversationId")
        )
    }

    /**
     * The launcher's picker draws `previewLayout` from the provider info (API 31+)
     * and falls back to the app icon without it, which is what the picker showed.
     */
    @Test
    fun bothProvidersDeclareAPickerPreview() {
        val providers = mapOf(
            "res/xml/widget_messages_list_info.xml" to "widget_messages_list_preview",
            "res/xml/widget_unread_info.xml" to "widget_unread_preview"
        )
        for ((info, layoutName) in providers) {
            val text = src(info)
            assertTrue(
                "$info must declare android:previewLayout=\"@layout/$layoutName\"",
                text.contains("android:previewLayout=\"@layout/$layoutName\"")
            )
            assertTrue(
                "$info names a preview layout that does not exist",
                File(main, "res/layout/$layoutName.xml").isFile
            )
        }
    }

    /**
     * The picker preview is drawn by the launcher, so its colours have to come
     * from the host's theme or they come out black on a dark launcher — the
     * snippet bubble did, as a layer-list whose `<item android:alpha>` the host
     * ignored.
     */
    @Test
    fun pickerPreviewShapesPaintFromTheHostTheme() {
        val previews = File(main, "res/drawable")
            .listFiles { f -> f.name.startsWith("widget_preview_") && f.extension == "xml" }
            .orEmpty()
        assertTrue("no widget_preview_* drawables found", previews.isNotEmpty())
        for (file in previews) {
            val xml = file.readText()
            assertTrue(
                "${file.name} layers an alpha over a solid colour, which the launcher flattens",
                !xml.contains("android:alpha=")
            )
            val solid = Regex("""<solid\s+android:color="([^"]+)"""").find(xml)
                ?: continue
            assertTrue(
                "${file.name} paints '${solid.groupValues[1]}' instead of a theme attribute",
                solid.groupValues[1].startsWith("?android:attr/")
            )
        }
    }

    /**
     * The inbox shows the contact photo or a silhouette, never initials, so the
     * widget must draw the same picture instead of text.
     */
    @Test
    fun theAvatarDrawsTheInboxPictureNotInitials() {
        val avatar = body(ui, "WidgetAvatar")
        assertTrue(
            "the widget avatar must draw the resolved picture",
            avatar.contains("item.avatar") && avatar.contains("ImageProvider(")
        )
        assertTrue("the widget avatar must not draw initials", !avatar.contains("item.initials"))
        assertTrue(
            "the widget avatar must fall back to the person silhouette",
            avatar.contains("R.drawable.ic_person_placeholder")
        )
    }
}
