package com.jh270.toolbox

import com.jh270.toolbox.ui.SourceKind
import com.jh270.toolbox.ui.sourceSpans
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceHighlightTest {
    @Test
    fun hashCommentRunsToEndOfLine() {
        val text = "keep # faded\nval x = 1"
        val comment = sourceSpans(text).first { it.kind == SourceKind.COMMENT }
        assertEquals("# faded", text.substring(comment.start, comment.end))
    }

    @Test
    fun fullwidthHashIsAComment() {
        val text = "＃注释"
        val spans = sourceSpans(text)
        assertEquals(1, spans.size)
        assertEquals(SourceKind.COMMENT, spans.single().kind)
        assertEquals(text, text.substring(spans.single().start, spans.single().end))
    }

    @Test
    fun hashInsideStringIsNotAComment() {
        val text = "echo \"# not comment\""
        val spans = sourceSpans(text)
        assertTrue(spans.none { it.kind == SourceKind.COMMENT })
        assertTrue(spans.any { it.kind == SourceKind.STRING })
    }

    @Test
    fun keywordsAndNumbersAreMarked() {
        val text = "if (count == 12) return"
        val spans = sourceSpans(text)
        assertTrue(spans.any { it.kind == SourceKind.KEYWORD && text.substring(it.start, it.end) == "if" })
        assertTrue(spans.any { it.kind == SourceKind.KEYWORD && text.substring(it.start, it.end) == "return" })
        assertTrue(spans.any { it.kind == SourceKind.NUMBER && text.substring(it.start, it.end) == "12" })
    }
}
