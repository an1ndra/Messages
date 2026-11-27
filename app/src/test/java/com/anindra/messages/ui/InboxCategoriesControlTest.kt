package com.anindra.messages.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class InboxCategoriesControlTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private fun src(name: String) = File(main, name).readText()

    @Test
    fun theCategoryTabsHaveAStoredSwitchThatDefaultsOn() {
        val store = src("java/com/anindra/messages/data/SettingsStore.kt")
        assertTrue(
            "no stored categories switch",
            store.contains("KEY_INBOX_CATEGORIES_ENABLED = \"inbox_categories_enabled\"")
        )
        assertTrue(
            "the switch must not change current installs",
            store.contains("prefs.getBoolean(KEY_INBOX_CATEGORIES_ENABLED, true)")
        )
    }

    @Test
    fun turningTheSwitchOffAlsoDropsAnyActiveFilter() {
        val inbox = src("java/com/anindra/messages/ui/InboxSettingsScreen.kt")
        assertTrue("no switch row", inbox.contains("R.string.settings_inbox_categories_title"))
        assertTrue(
            "an invisible filter would keep narrowing the list",
            inbox.contains("if (!on) vm.inboxCategory = null")
        )
    }

    @Test
    fun theInboxHidesBothThePullAndTheChipsWhenTheSwitchIsOff() {
        val list = src("java/com/anindra/messages/ui/ConversationsScreen.kt")
        assertTrue(
            "the pull would still reveal tabs",
            list.contains("!vm.settings.inboxCategoriesEnabled")
        )
        assertTrue(
            "the chip row is not gated",
            list.contains("visible = vm.settings.inboxCategoriesEnabled &&")
        )
    }

    @Test
    fun categoriesAndRulesIsANavigationRouteSoItAnimates() {
        val activity = src("java/com/anindra/messages/MainActivity.kt")
        assertTrue(
            "the row does not navigate",
            activity.contains("onOpenRules = { navRoute = \"inbox-rules\" }")
        )
        assertTrue(
            "the route has no screen",
            activity.contains("\"inbox-rules\" -> InboxCategoryRulesScreen(")
        )
        assertTrue(
            "back does not return to Inbox settings",
            activity.contains("\"inbox-rules\" -> navRoute = \"inbox\"")
        )
    }
}
