package com.anindra.messages.sms

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The app stores "whichever SIM holds the default SMS role" as a negative id and
 * passes it down through `SmsSender.sendMms` -> `Mms.send` ->
 * `SmsManagerMmsPlatform.managerFor`, where it has to be read as the default
 * SIM rather than handed to the platform as a subscription id.
 *
 * The constant and the rule live in different modules, so nothing but a test
 * here keeps them in step.
 */
class DefaultSimSendWiringTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("repository root not found")
    }

    @Test
    fun theStoredDefaultIsANegativeSubscriptionId() {
        val settingsStore = File(
            root, "app/src/main/java/com/anindra/messages/data/SettingsStore.kt"
        ).readText()
        assertTrue(
            "SettingsStore must keep storing the default SMS SIM as a negative id; " +
                "the manager resolves a non-positive id to the default SIM",
            Regex("DEFAULTS_SIM_SUBSCRIPTION_ID\\s*=\\s*-1").containsMatchIn(settingsStore),
        )
    }

    @Test
    fun theTransportResolvesThatIdToTheDefaultSmsManager() {
        val transport = File(
            root, "mms/src/main/java/com/anindra/messages/mms/transport/SystemMmsTransport.kt"
        ).readText()
        assertTrue(
            "a non-positive subscription id must not reach getSmsManagerForSubscriptionId",
            Regex("""fun usesDefaultSmsManager\(subscriptionId: Int\)\s*=\s*subscriptionId\s*<=\s*0""")
                .containsMatchIn(transport),
        )
    }
}