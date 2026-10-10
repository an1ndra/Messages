package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A restored backup must survive the reconcile that follows it.
 *
 * The bug this covers was self-inflicted and total. `mergeDatabase` copied each
 * backup row's `sys_id` across, but a provider id only means something on the
 * device that issued it. Restoring a backup made on another phone therefore left
 * rows whose ids matched nothing locally, and the next sync pruned every one of
 * them as "deleted outside the app" -- destroying the import the user had just
 * waited for. A real 68k-message restore went in and came straight back out,
 * leaving 5 messages.
 */
class ProviderIdAdoptionTest {

    @Test
    fun idsFromAnotherDeviceAreDropped() {
        // The provider here holds 10, 11, 12. The backup carries 9001, 9002.
        val adopted = LegacyBackupSchema.adoptProviderIds(
            carried = listOf(9001L, 9002L),
            liveProviderIds = setOf(10L, 11L, 12L)
        )
        assertTrue("no foreign id may be adopted", adopted.isEmpty())
    }

    @Test
    fun idsFromThisDeviceAreKept() {
        // Restoring a backup taken on the same phone: keeping the ids stops the
        // mirror from inserting those messages into the provider a second time.
        val adopted = LegacyBackupSchema.adoptProviderIds(
            carried = listOf(10L, 11L, 12L),
            liveProviderIds = setOf(10L, 11L, 12L, 20L)
        )
        assertEquals(setOf(10L, 11L, 12L), adopted)
    }

    @Test
    fun aMixedBackupKeepsOnlyTheOnesThisDeviceHas() {
        val adopted = LegacyBackupSchema.adoptProviderIds(
            carried = listOf(10L, 9001L, 12L, 9002L),
            liveProviderIds = setOf(10L, 11L, 12L)
        )
        assertEquals(setOf(10L, 12L), adopted)
    }

    @Test
    fun zeroAndUnknownIdsAreNeverAdopted() {
        val adopted = LegacyBackupSchema.adoptProviderIds(
            carried = listOf(0L, -1L, 5L),
            liveProviderIds = setOf(10L)
        )
        assertTrue("0 and negative ids are not provider rows", adopted.isEmpty())
    }

    @Test
    fun anUnreadableProviderDropsEveryCarriedId() {
        // If the provider cannot be read, nothing can be confirmed as ours. The
        // rows become local-only, which is safe: they are never pruned, and the
        // mirror will give them fresh ids.
        val adopted = LegacyBackupSchema.adoptProviderIds(
            carried = listOf(10L, 11L),
            liveProviderIds = emptySet()
        )
        assertTrue(adopted.isEmpty())
    }

    @Test
    fun theMergeNoLongerWritesACarriedIdBlindly() {
        // A guard on the query itself: the row must be written with an id that has
        // been checked against this device, not whatever the backup said.
        val repo = generateSequence(java.io.File("").absoluteFile) { it.parentFile }
            .map { java.io.File(it, "app/src/main") }
            .firstOrNull { java.io.File(it, "java").isDirectory }
            ?: return
        val src = java.io.File(repo, "java/com/anindra/messages/data/Repository.kt").readText()
        val start = src.indexOf("private fun mergeDatabase")
        assertTrue("mergeDatabase not found", start > 0)
        val body = src.substring(start, src.indexOf("\n    private fun ", start + 1))
        assertTrue(
            "the merge should consult the live provider ids",
            body.contains("liveProviderIds")
        )
        assertTrue(
            "the merge should not adopt a carried id unchecked",
            body.contains("if (carried in liveProviderIds) carried else 0L")
        )
    }
}
