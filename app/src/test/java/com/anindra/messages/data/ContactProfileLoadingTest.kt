package com.anindra.messages.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The contact lists behind the forward picker, the new-chat screen and the
 * scheduled-messages avatars are all fed from one load in `MainActivity`.
 *
 * It used to load *only* `ENTERPRISE_CONTENT_URI` from API 34, which returns
 * work-profile contacts exclusively — so on a phone with no work profile every
 * personal contact disappeared and the picker looked empty or wrong. The
 * `work` flag and the enterprise `contact_id` projection show the intent was
 * always personal *plus* work, so the personal profile has to be queried
 * unconditionally and the enterprise one added on top.
 *
 * The load is inline in a ViewModel init, so this asserts on the source the way
 * [com.anindra.messages.ui.NavTransitionDepthTest] does.
 */
class ContactProfileLoadingTest {

    private val main = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main") }
        .firstOrNull { it.isDirectory }
        ?: error("app/src/main not found")

    private val source = File(main, "java/com/anindra/messages/MainActivity.kt").readText()

    /** The ViewModel init that populates `contacts`. */
    private val contactLoading: String
        get() = source.substringAfter("val contacts = ").substringBefore("contacts.value = out")

    @Test
    fun thePersonalProfileIsQueried() {
        assertTrue(
            "personal Phone.CONTENT_URI is never queried",
            contactLoading.contains("CommonDataKinds.Phone.CONTENT_URI, false")
        )
    }

    @Test
    fun theWorkProfileIsAddedNotSubstituted() {
        // Order matters: personal first, then the SDK-guarded enterprise add.
        val personal = contactLoading.indexOf("CommonDataKinds.Phone.CONTENT_URI, false")
        val enterprise = contactLoading.indexOf("EnterpriseContacts.phoneUri()")
        assertTrue("work profile is not loaded", enterprise > 0)
        assertTrue(
            "work profile must be loaded after the personal one, not instead of it",
            enterprise > personal
        )
    }

    @Test
    fun thePersonalProfileIsNotTheElseBranchOfTheApiGuard() {
        // The original shape: enterprise when supported, personal only otherwise.
        assertFalse(
            "personal profile is still gated behind an else",
            Regex("""isSupported\([^)]*\)\)\s*\{[^}]*\}\s*else\s*\{\s*load\(""").containsMatchIn(contactLoading)
        )
    }

    @Test
    fun bothProfilesShareOneDedupeSet() {
        // A number saved in both profiles must be listed once, so the seen-set
        // has to live outside the per-uri load rather than inside it.
        assertTrue(
            "dedupe set is declared per-load instead of across both profiles",
            contactLoading.indexOf("val seen = mutableSetOf<String>()") <
                contactLoading.indexOf("fun load(uri:")
        )
    }

    @Test
    fun oneProfileFailingStillLeavesTheOther() {
        // Losing READ_CONTACTS must not also discard the work profile.
        val tryCount = Regex("""try\s*\{""").findAll(contactLoading).count()
        assertTrue("expected an independent guarded load per profile, found $tryCount", tryCount >= 2)
    }
}