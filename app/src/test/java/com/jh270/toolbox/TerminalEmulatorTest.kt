package com.jh270.toolbox

import com.jh270.toolbox.ssh.TerminalEmulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalEmulatorTest {
    private fun plainText(lines: List<TerminalEmulator.TerminalLine>): String {
        return lines.joinToString("\n") { line ->
            line.runs.joinToString("") { it.text }.trimEnd()
        }.trimEnd('\n')
    }

    @Test
    fun basicTextAndColor() {
        val emu = TerminalEmulator(cols = 20, rows = 4)
        emu.feed("hello\r\nworld")
        val lines = emu.getLines()
        assertEquals("hello", plainText(lines).lines()[0])
        assertEquals("world", plainText(lines).lines()[1])
        val worldRun = lines[1].runs.first { it.text.startsWith("world") }
        assertTrue(!worldRun.style.bold)
        assertEquals(0xFFE6EDF3.toInt(), worldRun.style.fg)
    }

    @Test
    fun ansiColorParsing() {
        val emu = TerminalEmulator(cols = 20, rows = 4)
        emu.feed("\u001B[31mred\u001B[0m plain")
        val line = emu.getLines().first()
        val redRun = line.runs.first { it.text == "red" }
        assertEquals(0xFFCD3131.toInt(), redRun.style.fg)
        assertEquals(0xFFE6EDF3.toInt(), line.runs.last().style.fg)
    }

    @Test
    fun clearScreenAndScroll() {
        val emu = TerminalEmulator(cols = 10, rows = 3)
        emu.feed("one\r\ntwo\r\nthree\r\nfour")
        val text = plainText(emu.getLines())
        assertTrue(text.contains("two"))
        assertTrue(text.contains("three"))
        assertTrue(text.contains("four"))
        emu.feed("\u001B[2J")
        val after = plainText(emu.getLines())
        val screenLines = after.lines().takeLast(3)
        assertTrue(screenLines.all { it.isBlank() })
    }

    @Test
    fun cursorPositionAndEraseLine() {
        val emu = TerminalEmulator(cols = 10, rows = 2)
        emu.feed("abcdefghij")
        emu.feed("\u001B[3G")
        emu.feed("\u001B[K")
        val line = emu.getLines().first()
        val text = line.runs.joinToString("") { it.text }.trimEnd()
        assertEquals("ab", text)
    }

    @Test
    fun trueColorParsing() {
        val emu = TerminalEmulator(cols = 20, rows = 2)
        emu.feed("\u001B[38;2;10;20;30mX\u001B[0m")
        val line = emu.getLines().first()
        val run = line.runs.first { it.text == "X" }
        assertEquals(0xFF0A141E.toInt(), run.style.fg)
    }

    @Test
    fun trailingBlankRowsAreTruncated() {
        val emu = TerminalEmulator(cols = 20, rows = 40)
        emu.feed("$ ")
        assertEquals(1, emu.getLines().size)
    }

    @Test
    fun cursorRenderedAsBlock() {
        val emu = TerminalEmulator(cols = 20, rows = 40)
        emu.feed("$ ")
        val line = emu.getLines().first()
        val cursorRun = line.runs.firstOrNull { it.text == " " && it.style.bg == 0xFFE6EDF3.toInt() }
        assertTrue(cursorRun != null)
    }

    @Test
    fun resizeReflowsContent() {
        val emu = TerminalEmulator(cols = 10, rows = 3)
        emu.feed("abcdefghijklmnopqrst")
        emu.resize(5, 4)
        val text = plainText(emu.getLines())
        assertTrue(text.contains("abcde"))
        assertTrue(text.contains("fghij"))
        assertTrue(text.contains("klmno"))
        assertTrue(text.contains("pqrst"))
    }

    @Test
    fun bracketedPasteModeAndPlainText() {
        val emu = TerminalEmulator(cols = 20, rows = 6)
        assertFalse(emu.isBracketedPaste)
        emu.feed("echo hi\r\n")
        emu.feed("\u001B[?2004h")
        assertTrue(emu.isBracketedPaste)
        assertTrue(emu.plainText().contains("echo hi"))
        emu.feed("\u001B[?2004l")
        assertFalse(emu.isBracketedPaste)
    }
}
