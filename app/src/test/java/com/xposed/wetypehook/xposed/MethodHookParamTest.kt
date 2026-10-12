package com.xposed.wetypehook.xposed

import org.junit.Assert.*
import org.junit.Test

class MethodHookParamTest {
    private val method = String::class.java.getMethod("substring", Int::class.javaPrimitiveType)

    @Test
    fun readingArgumentsDoesNotCopyTheFrameworkList() {
        val param = MethodHookParam(method, "host", listOf(1, null, "value"))
        repeat(10_000) {
            assertEquals(1, param[0])
            assertNull(param[1])
            assertEquals("value", param.argumentOrNull(2))
        }
        assertNull(param.changedArgs)
        assertEquals(3, param.argumentCount)
        assertNull(param.argumentOrNull(-1))
        assertNull(param.argumentOrNull(3))
    }

    @Test
    fun writesCopyOnceAndPreserveTheOriginalArguments() {
        val original = listOf<Any?>(1, "value")
        val param = MethodHookParam(method, "host", original)
        param[0] = 2
        val changed = param.changedArgs
        param[1] = null
        assertSame(changed, param.changedArgs)
        assertEquals(listOf(1, "value"), original)
        assertEquals(2, param[0])
        assertNull(param[1])
        assertArrayEquals(arrayOf(2, null), changed)
    }

    @Test
    fun arrayCompatibilityAccessIsMutableAndUsesTheSameCopy() {
        val param = MethodHookParam(method, "host", listOf(1))
        val args = param.args
        args[0] = 3
        assertSame(args, param.args)
        assertSame(args, param.changedArgs)
        assertEquals(3, param[0])
    }

    @Test
    fun replacingArgumentsUpdatesReadsAndBounds() {
        val param = MethodHookParam(method, "host", listOf(1))
        val replacement = arrayOf<Any?>(null, 4)
        param.args = replacement
        assertSame(replacement, param.changedArgs)
        assertEquals(2, param.argumentCount)
        assertNull(param[0])
        assertEquals(4, param.argumentOrNull(1))
        assertNull(param.argumentOrNull(2))
    }

    @Test
    fun originalResultDoesNotShortCircuitButExplicitNullDoes() {
        val param = MethodHookParam(method, "host", emptyList(), "original")
        assertFalse(param.resultWasSet)
        assertEquals("original", param.result)
        param.result = null
        assertTrue(param.resultWasSet)
        assertNull(param.result)
        assertNull(param.changedArgs)
    }

    @Test
    fun staticHooksHaveANonNullReceiverWithoutCopyingEmptyArguments() {
        val param = MethodHookParam(method, null, emptyList())
        assertNotNull(param.thisObject)
        assertEquals(0, param.argumentCount)
        assertNull(param.argumentOrNull(0))
        assertNull(param.changedArgs)
    }
}
