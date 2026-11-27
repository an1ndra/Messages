package com.anindra.messages.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ScheduledRowVisibilityTest {

    private val settings = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main/java/com/anindra/messages/ui/SettingsScreen.kt") }
        .firstOrNull { it.isFile }
        ?.readText()
        ?: error("SettingsScreen.kt not found")

    @Test
    fun theGeneralSettingsRowOnlyExistsWhenThereIsSomethingToManage() {
        val row = settings.indexOf("title = stringResource(R.string.settings_scheduled_title)")
        assertTrue("General settings has no scheduled row", row >= 0)
        val before = settings.substring(maxOf(0, row - 300), row)
        assertTrue(
            "the scheduled row must be inside an isNotEmpty guard",
            before.contains("if (scheduledAll.isNotEmpty())")
        )
    }

    @Test
    fun theVisibilityComesFromTheLiveScheduledMessagesFlow() {
        assertTrue(
            "visibility is not read from the scheduled messages flow",
            settings.contains("vm.scheduledMessages().collectAsState(initial = emptyList())")
        )
    }
}
