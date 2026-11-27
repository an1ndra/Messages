package com.anindra.messages.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArchiveInboundChoiceTest {

    private fun source(path: String): String {
        val candidates = listOf(path, "../$path", "../../$path")
        return candidates.firstNotNullOfOrNull { File(it).takeIf { f -> f.exists() }?.readText() }
            ?: error("$path not found")
    }

    private val settings by lazy { source("app/src/main/java/com/anindra/messages/data/SettingsStore.kt") }
    private val repository by lazy { source("app/src/main/java/com/anindra/messages/data/Repository.kt") }
    private val viewModel by lazy { source("app/src/main/java/com/anindra/messages/MainActivity.kt") }
    private val dialog by lazy { source("app/src/main/java/com/anindra/messages/ui/ArchiveInboundChoiceDialog.kt") }

    @Test
    fun theTwoFactsAreStoredTogether() {
        assertTrue(
            settings.contains("KEY_ARCHIVE_INBOUND_CHOICE_ASKED = \"archive_inbound_choice_asked\"")
        )
        assertTrue(
            settings.contains("KEY_KEEP_ARCHIVED_ON_INBOUND = \"keep_archived_on_inbound\"")
        )
        val method = settings.substringAfter("fun saveArchiveInboundChoice")
            .substringBefore("\n    }")
        assertTrue(method.contains("putBoolean(KEY_ARCHIVE_INBOUND_CHOICE_ASKED, true)"))
        assertTrue(method.contains("putBoolean(KEY_KEEP_ARCHIVED_ON_INBOUND, keepArchived)"))
    }

    @Test
    fun theRepositoryReadsTheLockedChoiceBeforeUnarchiving() {
        val method = repository.substringAfter("private fun unarchiveForInbound")
            .substringBefore("\n    }")
        assertTrue(method.contains("if (settings.keepArchivedOnInbound) return"))
        assertTrue(method.contains("UPDATE conversations SET archived=0 WHERE id=? AND archived=1"))
    }

    @Test
    fun theFirstArchiveAsksAndOnlyTheFirstOneAsks() {
        val method = viewModel.substringAfter("fun archiveConversation")
            .substringBefore("fun unarchiveConversation")
        assertTrue(method.contains("if (!settings.archiveInboundChoiceAsked)"))
        assertTrue(method.contains("showArchiveInboundChoice.value = true"))
    }

    @Test
    fun EitherChoiceClosesThePromptAndPersistsTheAnswer() {
        val method = viewModel.substringAfter("fun saveArchiveInboundChoice")
            .substringBefore("\n    }")
        assertTrue(method.contains("settings.saveArchiveInboundChoice(keepArchived)"))
        assertTrue(method.contains("showArchiveInboundChoice.value = false"))

        val host = viewModel.substringAfter("if (vm.showArchiveInboundChoice.value)")
            .substringBefore("// Single back dispatcher")
        assertTrue(host.contains("ArchiveInboundChoiceDialog("))
        assertTrue(host.contains("onBringToInbox = { vm.saveArchiveInboundChoice(false) }"))
        assertTrue(host.contains("onKeepArchived = { vm.saveArchiveInboundChoice(true) }"))
    }

    @Test
    fun theDialogOffersBothAnswers() {
        assertTrue(dialog.contains("onBringToInbox: () -> Unit"))
        assertTrue(dialog.contains("onKeepArchived: () -> Unit"))
        assertTrue(dialog.contains("R.string.archive_inbound_choice_inbox"))
        assertTrue(dialog.contains("R.string.archive_inbound_choice_archive"))
    }

    @Test
    fun theChatMenuUsesTheSameOneTimeArchivePath() {
        val chat = source("app/src/main/java/com/anindra/messages/ui/ChatScreen.kt")
        assertTrue(chat.contains("onArchive = {\n                    vm.archiveConversation(conversationId)"))
    }
}
