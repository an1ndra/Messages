package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageBodyTest {

    @Test
    fun stripsATrailingNewline() {
        assertEquals("hello", MessageBody.normalize("hello\n"))
    }

    @Test
    fun stripsMultipleTrailingNewlines() {
        assertEquals("hello", MessageBody.normalize("hello\n\n\n"))
    }

    @Test
    fun stripsTrailingWhitespaceAndNewlines() {
        assertEquals("hello", MessageBody.normalize("hello \n \t\n"))
        assertEquals("hello", MessageBody.normalize("hello   "))
    }

    @Test
    fun preservesInteriorNewlines() {
        assertEquals("line1\nline2", MessageBody.normalize("line1\nline2\n"))
        assertEquals("a\n\nb", MessageBody.normalize("a\n\nb\n\n"))
    }

    @Test
    fun leavesCleanBodiesUntouched() {
        assertEquals("hello", MessageBody.normalize("hello"))
        assertEquals("line1\nline2", MessageBody.normalize("line1\nline2"))
    }

    @Test
    fun handlesEmptyAndWhitespaceOnly() {
        assertEquals("", MessageBody.normalize(""))
        assertEquals("", MessageBody.normalize("\n\n"))
        assertEquals("", MessageBody.normalize("   "))
    }
}
