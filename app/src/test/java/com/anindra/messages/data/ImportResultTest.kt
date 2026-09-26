package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportResultTest {

    @Test
    fun successCarriesMergedCount() {
        val result: Repository.ImportResult = Repository.ImportResult.Success(5)
        assertTrue(result is Repository.ImportResult.Success)
        assertEquals(5, (result as Repository.ImportResult.Success).merged)
    }

    @Test
    fun replaceImportSuccessHasNullMergedCount() {
        assertNull(Repository.ImportResult.Success().merged)
    }

    @Test
    fun errorCarriesMessage() {
        val result: Repository.ImportResult = Repository.ImportResult.Error("bad pin")
        assertEquals("bad pin", (result as Repository.ImportResult.Error).message)
    }

    @Test
    fun sealedHierarchyIsExhaustive() {
        val describe: (Repository.ImportResult) -> String = { result ->
            when (result) {
                is Repository.ImportResult.Success -> "ok:${result.merged}"
                is Repository.ImportResult.Error -> "error:${result.message}"
            }
        }
        assertEquals("ok:null", describe(Repository.ImportResult.Success()))
        assertEquals("error:nope", describe(Repository.ImportResult.Error("nope")))
    }
}
