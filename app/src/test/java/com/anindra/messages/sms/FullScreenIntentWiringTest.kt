package com.anindra.messages.sms

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FullScreenIntentWiringTest {

    private fun source(name: String): String {
        val f = File("src/main/java/com/anindra/messages/sms/$name")
        assertTrue("missing source $name", f.exists())
        return f.readText()
    }

    private fun manifest(): String = File("src/main/AndroidManifest.xml").readText()

    private fun res(name: String): String {
        val f = File("src/main/res/values/$name")
        assertTrue("missing resource $name", f.exists())
        return f.readText()
    }

    @Test
    fun manifestDeclaresFullScreenIntentPermission() {
        assertTrue(
            manifest().contains("android.permission.USE_FULL_SCREEN_INTENT")
        )
    }

    @Test
    fun manifestRegistersTheTrampoline() {
        val m = manifest()
        assertTrue(m.contains(".sms.FullScreenSmsActivity"))
        assertTrue(m.contains("android:excludeFromRecents=\"true\""))
        assertTrue(m.contains("android:noHistory=\"true\""))
    }

    @Test
    fun showAttachesAFullScreenIntent() {
        assertTrue(
            Regex("""setFullScreenIntent\(.*\)""").containsMatchIn(source("SmsSupport.kt"))
        )
    }

    @Test
    fun wakeIsGatedOnTheKeyguard() {
        val s = source("SmsSupport.kt")
        assertTrue(Regex("""shouldWake\(.*keyguardLocked\(context\)""").containsMatchIn(s))
    }

    @Test
    fun channelDemotionRecoveryDeletesBeforeRecreating() {
        val s = source("SmsSupport.kt")
        val idx = s.indexOf("private fun recoverDemotedChannel")
        assertTrue("recoverDemotedChannel missing", idx >= 0)
        val body = s.substring(idx, s.indexOf("private fun", idx + 10))
        assertTrue(body.contains("getNotificationChannel"))
        assertTrue(body.contains("IMPORTANCE_DEFAULT"))
        val guard = body.indexOf("if (")
        val check = body.indexOf("existing.importance >=")
        val del = body.indexOf("deleteNotificationChannel")
        assertTrue(guard >= 0 && check >= 0 && del >= 0)
        assertTrue("must test importance before deleting", check < del)
    }

    @Test
    fun fullScreenThemeShowsOverTheKeyguard() {
        val t = res("themes.xml")
        assertTrue(Regex("""name="Theme\.Messages\.FullScreen"""").containsMatchIn(t))
        assertTrue(t.contains("android:windowIsTranslucent"))
        assertTrue(t.contains("android:windowBackground"))
    }

    @Test
    fun trampolineTurnsTheScreenOnAndChecksTheKeyguard() {
        val s = source("FullScreenSmsActivity.kt")
        assertTrue(s.contains("setTurnScreenOn(true)"))
        assertTrue(s.contains("setShowWhenLocked(true)"))
        assertTrue(s.contains("isKeyguardLocked"))
        assertTrue(s.contains("open_conversation_address"))
    }

    @Test
    fun diagnosticsReportsTheWakeChain() {
        val d = File("src/main/java/com/anindra/messages/diagnostics/DiagnosticsReport.kt").readText()
        assertTrue(d.contains("Full-screen intent:"))
        assertTrue(d.contains("Channel importance:"))
        assertTrue(d.contains("Keyguard locked:"))
        assertTrue(d.contains("Wake screen for new messages:"))
    }
}