package com.jh270.toolbox

import com.jh270.toolbox.ui.SoftWrapOffsetMapping
import com.jh270.toolbox.ui.logicalLineStartOffsets
import com.jh270.toolbox.ui.softWrap
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorTextTest {
    @Test
    fun wrapInsertsZeroWidthAfterEachCharacter() {
        val wrapped = softWrap("a\nb")
        assertEquals("a\u200B\nb\u200B", wrapped.text)
        assertEquals(0, wrapped.map[0])
        assertEquals(2, wrapped.map[1])
        assertEquals(3, wrapped.map[2])
        assertEquals(5, wrapped.map[3])
    }

    @Test
    fun lineStartsMapOntoWrappedText() {
        val text = "ab\ncd"
        val wrapped = softWrap(text)
        val starts = logicalLineStartOffsets(text)
        assertEquals(2, starts.size)
        assertEquals(0, starts[0])
        assertEquals(3, starts[1])
        assertEquals(0, wrapped.map[starts[0]])
        assertEquals(wrapped.text.indexOf('c'), wrapped.map[starts[1]])
    }

    @Test
    fun offsetMappingSkipsZeroWidthSpace() {
        val wrapped = softWrap("ab")
        val mapping = SoftWrapOffsetMapping(wrapped.map)
        assertEquals(0, mapping.originalToTransformed(0))
        assertEquals(2, mapping.originalToTransformed(1))
        assertEquals(4, mapping.originalToTransformed(2))
        assertEquals(0, mapping.transformedToOriginal(0))
        assertEquals(0, mapping.transformedToOriginal(1))
        assertEquals(1, mapping.transformedToOriginal(2))
        assertEquals(1, mapping.transformedToOriginal(3))
        assertEquals(2, mapping.transformedToOriginal(4))
    }
}
