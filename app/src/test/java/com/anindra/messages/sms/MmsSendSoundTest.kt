package com.anindra.messages.sms

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MmsSendSoundTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private fun src(name: String) = File(main, name).readText()

    @Test
    fun anAttachmentSendPlaysTheSameSentToneAsAText() {
        val activity = src("java/com/anindra/messages/MainActivity.kt")
        val start = activity.indexOf("fun sendMediaAlbum(")
        assertTrue("sendMediaAlbum is missing", start >= 0)
        val body = activity.substring(start, start + 3000)
        assertTrue(
            "an MMS attachment send has no sent sound",
            body.contains("NotificationHelper.playSentSound(getApplication(), \"mms\")")
        )
    }

    @Test
    fun theSentToneKeepsItsSwitchAndReportsItsSource() {
        val support = src("java/com/anindra/messages/sms/SmsSupport.kt")
        assertTrue(
            "playSentSound no longer takes a source",
            support.contains("fun playSentSound(context: Context, source: String = \"sms\")")
        )
        assertTrue(
            "the send-sound switch no longer gates playback",
            support.contains("if (!app.repository.settings.sendSoundEnabled) return")
        )
        assertTrue(
            "the played path is not reported as text for the device test",
            support.contains("Log.i(\"SentSound\", \"played source=\$source\")")
        )
    }
}
