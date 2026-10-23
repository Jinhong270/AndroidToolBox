package com.jh270.toolbox

import com.jh270.toolbox.ui.terminalKeyLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalKeyLabelTest {
    @Test
    fun collapsesDuplicatedKeyNames() {
        assertEquals("Esc", terminalKeyLabel("ESCEsc"))
        assertEquals("Tab", terminalKeyLabel("TABTab"))
        assertEquals("Ctrl", terminalKeyLabel("CTRLCtrl"))
        assertEquals("Del", terminalKeyLabel("DELDel"))
        assertEquals("Home", terminalKeyLabel("HOMEHome"))
        assertEquals("End", terminalKeyLabel("ENDEnd"))
    }

    @Test
    fun keepsSingleLabels() {
        assertEquals("Esc", terminalKeyLabel("Esc"))
        assertEquals("Tab", terminalKeyLabel("Tab"))
        assertEquals("Ctrl", terminalKeyLabel("Ctrl"))
        assertEquals("Ctrl ON", terminalKeyLabel("Ctrl ON"))
        assertEquals("Del", terminalKeyLabel("Del"))
        assertEquals("Home", terminalKeyLabel("Home"))
        assertEquals("End", terminalKeyLabel("End"))
        assertEquals("←", terminalKeyLabel("←"))
    }
}
