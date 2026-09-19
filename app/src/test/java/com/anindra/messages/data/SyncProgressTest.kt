package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncProgressTest {
    @Test
    fun readSpansZeroToReadEnd() {
        assertEquals(0f, SyncProgress.read(0, 100), 0.0001f)
        assertEquals(SyncProgress.READ_END / 2f, SyncProgress.read(50, 100), 0.0001f)
        assertEquals(SyncProgress.READ_END, SyncProgress.read(100, 100), 0.0001f)
    }

    @Test
    fun readHandlesEmptyOrNegativeTotals() {
        assertEquals(0f, SyncProgress.read(0, 0), 0.0001f)
        assertEquals(0f, SyncProgress.read(5, -1), 0.0001f)
        assertEquals(SyncProgress.READ_END, SyncProgress.read(200, 100), 0.0001f)
    }

    @Test
    fun resolveSpansReadEndToResolveEnd() {
        assertEquals(SyncProgress.READ_END, SyncProgress.resolve(0, 100), 0.0001f)
        assertEquals(
            (SyncProgress.READ_END + SyncProgress.RESOLVE_END) / 2f,
            SyncProgress.resolve(50, 100), 0.0001f
        )
        assertEquals(SyncProgress.RESOLVE_END, SyncProgress.resolve(100, 100), 0.0001f)
        assertEquals(SyncProgress.READ_END, SyncProgress.resolve(0, 0), 0.0001f)
    }

    @Test
    fun importSpansResolveEndToImportEnd() {
        assertEquals(SyncProgress.RESOLVE_END, SyncProgress.import(0, 100), 0.0001f)
        assertEquals(
            (SyncProgress.RESOLVE_END + SyncProgress.IMPORT_END) / 2f,
            SyncProgress.import(50, 100), 0.0001f
        )
        assertEquals(SyncProgress.IMPORT_END, SyncProgress.import(100, 100), 0.0001f)
        assertEquals(SyncProgress.RESOLVE_END, SyncProgress.import(0, 0), 0.0001f)
    }

    @Test
    fun phasesNeverMoveBackwards() {
        assertTrue(SyncProgress.read(100, 100) <= SyncProgress.resolve(0, 100))
        assertTrue(SyncProgress.resolve(100, 100) <= SyncProgress.import(0, 100))
        assertTrue(SyncProgress.read(25, 100) < SyncProgress.read(75, 100))
        assertTrue(SyncProgress.resolve(25, 100) < SyncProgress.resolve(75, 100))
        assertTrue(SyncProgress.import(25, 100) < SyncProgress.import(75, 100))
    }
}
