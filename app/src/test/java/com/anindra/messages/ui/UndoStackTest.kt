package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UndoStackTest {

    @Test
    fun undoPopsTheMostRecentActionFirst() {
        val stack = UndoStack<String>()
        listOf("first", "second", "third").forEach { stack.push(it) }

        assertEquals("third", stack.pop())
        assertEquals("second", stack.pop())
        assertEquals("first", stack.pop())
    }

    @Test
    fun peekShowsTheNewestActionWithoutConsumingIt() {
        val stack = UndoStack<Int>()
        stack.push(1)
        stack.push(2)

        assertEquals(2, stack.peek())
        assertEquals(2, stack.peek())
        assertEquals(2, stack.pop())
        assertEquals(1, stack.peek())
    }

    @Test
    fun clearingEndsTheWholeUndoWindow() {
        val stack = UndoStack<Int>()
        stack.push(1)
        stack.push(2)
        stack.clear()

        assertNull(stack.peek())
        assertNull(stack.pop())
    }

    @Test
    fun poppingAnEmptyStackIsHarmless() {
        assertNull(UndoStack<Int>().pop())
    }
}
