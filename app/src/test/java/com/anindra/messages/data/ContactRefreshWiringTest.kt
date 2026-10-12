package com.anindra.messages.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * #317: a contact added or renamed in the phone book only showed in the list
 * after an app resume that cleared a 5-minute refresh gate -- 20 minutes to two
 * hours for the reporter. The provider is watched for SMS/MMS, but nothing
 * watched Contacts, so an edit made while Messages was in front was invisible.
 */
class ContactRefreshWiringTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private val repo: String by lazy {
        File(main, "java/com/anindra/messages/data/Repository.kt").readText()
    }

    private val mainActivity: String by lazy {
        File(main, "java/com/anindra/messages/MainActivity.kt").readText()
    }

    @Test
    fun theContactsProviderIsObserved() {
        assertTrue(
            "the Contacts provider must be watched, or a phone-book edit is " +
                "invisible until the app is resumed",
            repo.contains("fun observeContactChanges()")
        )
        assertTrue(
            "the observer must cover Contact edits",
            repo.substring(
                repo.indexOf("fun observeContactChanges()"),
                repo.indexOf("\n    private fun ", repo.indexOf("fun observeContactChanges()"))
            ).contains("ContactsContract.Contacts.CONTENT_URI")
        )
    }

    @Test
    fun aContactChangeDropsTheCacheBeforeResolving() {
        val start = repo.indexOf("contactRefreshRunnable")
        assertTrue("contactRefreshRunnable not found", start > 0)
        val body = repo.substring(start, repo.indexOf("observeContactChanges", start))
        assertTrue(
            "the TTL cache must be dropped first, or an edit inside the TTL is " +
                "invisible",
            body.contains("invalidateContactNames()")
        )
        assertTrue(
            "and the stored names must then be rewritten",
            body.contains("refreshContactNames()")
        )
    }

    @Test
    fun theActivityRegistersTheObserverRegardlessOfTheResumeGate() {
        val gate = mainActivity.indexOf("now - lastResumeTime > 5 * 60_000L")
        assertTrue("resume gate not found", gate > 0)
        val after = mainActivity.substring(gate)
        val rel = after.indexOf("observeContactChanges()")
        assertTrue("observeContactChanges() not called from onResume", rel > 0)
        assertTrue(
            "the contacts observer must sit outside the 5-minute gate, which is " +
                "exactly what delayed the refresh in #317",
            rel > after.indexOf("lastResumeTime = now")
        )
    }
}
