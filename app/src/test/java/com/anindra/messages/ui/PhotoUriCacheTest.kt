package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoUriCacheTest {

    @Test
    fun cachesResolvedUri() {
        val cache = PhotoUriCache()
        assertNull(cache.get("+1555"))
        cache.put("+1555", "content://media/photo/1")
        assertEquals("content://media/photo/1", cache.get("+1555"))
    }

    @Test
    fun remembersNoPhotoAsBlank() {
        val cache = PhotoUriCache()
        cache.put("+1555", null)
        assertEquals("", cache.get("+1555"))
    }

    @Test
    fun evictsOldestBeyondMax() {
        val cache = PhotoUriCache(max = 2)
        cache.put("a", "1")
        cache.put("b", "2")
        cache.put("c", "3")
        assertNull(cache.get("a"))
        assertEquals("2", cache.get("b"))
        assertEquals("3", cache.get("c"))
    }
}
