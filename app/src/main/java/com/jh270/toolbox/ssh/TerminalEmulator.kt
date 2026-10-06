package com.jh270.toolbox.ssh

import kotlin.math.min

class TerminalEmulator(
    cols: Int = 80,
    rows: Int = 24,
    private val maxScrollback: Int = 2000
) {
    class Style(
        var fg: Int = DEFAULT_FG,
        var bg: Int = DEFAULT_BG,
        var bold: Boolean = false,
        var dim: Boolean = false,
        var inverse: Boolean = false,
        var underline: Boolean = false,
        var italic: Boolean = false,
        var strike: Boolean = false
    ) {
        fun copyOf(): Style = Style(fg, bg, bold, dim, inverse, underline, italic, strike)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Style) return false
            return fg == other.fg && bg == other.bg && bold == other.bold && dim == other.dim &&
                inverse == other.inverse && underline == other.underline && italic == other.italic && strike == other.strike
        }

        override fun hashCode(): Int {
            var h = fg
            h = 31 * h + bg
            h = 31 * h + (if (bold) 1 else 0)
            h = 31 * h + (if (dim) 1 else 0)
            h = 31 * h + (if (inverse) 1 else 0)
            h = 31 * h + (if (underline) 1 else 0)
            h = 31 * h + (if (italic) 1 else 0)
            h = 31 * h + (if (strike) 1 else 0)
            return h
        }

        companion object {
            const val DEFAULT_FG = 0xFFE6EDF3.toInt()
            const val DEFAULT_BG = 0xFF0A0E14.toInt()
        }
    }

    class StyledRun(val text: String, val style: Style)
    class TerminalLine(val runs: List<StyledRun>)

    private class Cell {
        var ch: Char = ' '
        var style: Style = Style()
        var wide: Boolean = false
    }

    private val CONT = '\u0000'
    private val ESC = '\u001B'

    private var cols: Int = cols.coerceAtLeast(20)
    private var rows: Int = rows.coerceAtLeast(5)

    private var grid: Array<Array<Cell>> = Array(this.rows) { Array(this.cols) { Cell() } }
    private var altGrid: Array<Array<Cell>> = Array(this.rows) { Array(this.cols) { Cell() } }
    private val scrollback = ArrayDeque<Array<Cell>>()

    private var isAlt = false
    private var cursorRow = 0
    private var cursorCol = 0
    private var wrapPending = false
    private val currentStyle = Style()
    private var savedCursorRow = 0
    private var savedCursorCol = 0
    private val savedStyle = Style()

    fun feed(data: String) {
        var i = 0
        val n = data.length
        while (i < n) {
            val c = data[i]
            when {
                c == ESC -> {
                    i++
                    if (i >= n) return
                    when (val c1 = data[i]) {
                        '[' -> i = parseCsi(data, i + 1)
                        ']' -> i = parseOsc(data, i + 1)
                        '(', ')', '*', '+' -> i = if (i + 1 < n) i + 2 else n
                        '7' -> {
                            saveCursor()
                            i++
                        }
                        '8' -> {
                            restoreCursor()
                            i++
                        }
                        'M' -> {
                            reverseIndex()
                            i++
                        }
                        'D' -> {
                            lineFeed()
                            i++
                        }
                        'E' -> {
                            lineFeed()
                            cursorCol = 0
                            i++
                        }
                        'c' -> {
                            reset()
                            i++
                        }
                        '=' , '>' -> i++
                        '#' -> i = if (i + 1 < n) i + 2 else n
                        else -> i++
                    }
                }
                c == '\r' -> {
                    cursorCol = 0
                    wrapPending = false
                    i++
                }
                c == '\n' || c == '\u000B' || c == '\u000C' -> {
                    lineFeed()
                    wrapPending = false
                    i++
                }
                c == '\b' -> {
                    if (cursorCol > 0) cursorCol--
                    i++
                }
                c == '\t' -> {
                    cursorCol = min(cols - 1, (cursorCol / 8 + 1) * 8)
                    i++
                }
                c == '\u0007' || c == '\u0000' -> i++
                c.code < 0x20 -> i++
                else -> {
                    val cp = data.codePointAt(i)
                    val width = charWidth(cp)
                    putChar(if (cp <= 0xFFFF) cp.toChar() else '\uFFFD', width)
                    i += Character.charCount(cp)
                }
            }
        }
    }

    fun getLines(): List<TerminalLine> {
        val result = ArrayList<TerminalLine>(scrollback.size + rows)
        for (row in scrollback) {
            result.add(buildLine(row, true))
        }
        val g = activeGrid()
        var lastRow = cursorRow
        for (r in 0 until rows) {
            if (isRowVisible(g[r])) lastRow = maxOf(lastRow, r)
        }
        for (r in 0..lastRow) {
            result.add(buildLine(g[r], false, if (r == cursorRow) cursorCol else -1))
        }
        return result
    }

    fun clearScreen() {
        scrollback.clear()
        val g = activeGrid()
        for (r in 0 until rows) clearRow(r, g)
        cursorRow = 0
        cursorCol = 0
        wrapPending = false
    }

    fun reset() {
        scrollback.clear()
        clearGrid(grid)
        clearGrid(altGrid)
        cursorRow = 0
        cursorCol = 0
        wrapPending = false
        isAlt = false
        resetStyle()
    }

    private fun activeGrid(): Array<Array<Cell>> = if (isAlt) altGrid else grid

    private fun parseCsi(data: String, start: Int): Int {
        var i = start
        while (i < data.length) {
            val c = data[i]
            if (c.code in 0x40..0x7E) break
            i++
        }
        if (i >= data.length) return i
        val final = data[i]
        handleCsi(final, data.substring(start, i))
        return i + 1
    }

    private fun parseOsc(data: String, start: Int): Int {
        var i = start
        while (i < data.length) {
            val c = data[i]
            if (c == '\u0007') return i + 1
            if (c == ESC && i + 1 < data.length && data[i + 1] == '\\') return i + 2
            i++
        }
        return i
    }

    private fun handleCsi(final: Char, body: String) {
        var paramsBody = body
        var privateMode = false
        if (paramsBody.startsWith("?")) {
            privateMode = true
            paramsBody = paramsBody.substring(1)
        } else if (paramsBody.startsWith(">")) {
            paramsBody = paramsBody.substring(1)
        }
        val params = parseParams(paramsBody)
        when (final) {
            'A' -> cursorUp(param(params, 0, 1))
            'B' -> cursorDown(param(params, 0, 1))
            'C' -> cursorForward(param(params, 0, 1))
            'D' -> cursorBack(param(params, 0, 1))
            'E' -> {
                cursorDown(param(params, 0, 1))
                cursorCol = 0
            }
            'F' -> {
                cursorUp(param(params, 0, 1))
                cursorCol = 0
            }
            'G' -> cursorCol = clampCol(param(params, 0, 1) - 1)
            'H', 'f' -> {
                cursorRow = clampRow(param(params, 0, 1) - 1)
                cursorCol = clampCol(param(params, 1, 1) - 1)
            }
            'J' -> eraseInDisplay(param(params, 0, 0))
            'K' -> eraseInLine(param(params, 0, 0))
            'm' -> applySgr(params)
            'd' -> cursorRow = clampRow(param(params, 0, 1) - 1)
            '@' -> insertChars(param(params, 0, 1))
            'P' -> deleteChars(param(params, 0, 1))
            'L' -> insertLines(param(params, 0, 1))
            'M' -> deleteLines(param(params, 0, 1))
            'X' -> eraseChars(param(params, 0, 1))
            'S' -> scrollUpLines(param(params, 0, 1))
            'T' -> scrollDownLines(param(params, 0, 1))
            's' -> saveCursor()
            'u' -> restoreCursor()
            'h', 'l' -> handleMode(privateMode, params, final == 'h')
            else -> {}
        }
    }

    private fun parseParams(s: String): IntArray {
        var cleaned = s
        if (cleaned.isNotEmpty() && (cleaned[0] == '?' || cleaned[0] == '>' || cleaned[0] == '!' || cleaned[0] == '=')) {
            cleaned = cleaned.substring(1)
        }
        return cleaned.split(';').map { it.toIntOrNull() ?: 0 }.toIntArray()
    }

    private fun param(params: IntArray, index: Int, default: Int): Int {
        return if (index < params.size) params[index] else default
    }

    private fun handleMode(privateMode: Boolean, params: IntArray, enable: Boolean) {
        if (!privateMode) return
        for (p in params) {
            if (p == 1049 || p == 47 || p == 1047) {
                if (enable) enterAlt() else exitAlt()
            }
        }
    }

    private fun enterAlt() {
        if (isAlt) return
        isAlt = true
        clearGrid(altGrid)
        cursorRow = 0
        cursorCol = 0
        wrapPending = false
    }

    private fun exitAlt() {
        isAlt = false
        cursorRow = 0
        cursorCol = 0
        wrapPending = false
    }

    private fun cursorUp(count: Int) {
        cursorRow = clampRow(cursorRow - count)
        wrapPending = false
    }

    private fun cursorDown(count: Int) {
        cursorRow = clampRow(cursorRow + count)
        wrapPending = false
    }

    private fun cursorForward(count: Int) {
        cursorCol = clampCol(cursorCol + count)
        wrapPending = false
    }

    private fun cursorBack(count: Int) {
        cursorCol = clampCol(cursorCol - count)
        wrapPending = false
    }

    private fun clampRow(v: Int): Int = v.coerceIn(0, rows - 1)
    private fun clampCol(v: Int): Int = v.coerceIn(0, cols - 1)

    private fun saveCursor() {
        savedCursorRow = cursorRow
        savedCursorCol = cursorCol
        savedStyle.fg = currentStyle.fg
        savedStyle.bg = currentStyle.bg
        savedStyle.bold = currentStyle.bold
        savedStyle.dim = currentStyle.dim
        savedStyle.inverse = currentStyle.inverse
        savedStyle.underline = currentStyle.underline
        savedStyle.italic = currentStyle.italic
        savedStyle.strike = currentStyle.strike
    }

    private fun restoreCursor() {
        cursorRow = clampRow(savedCursorRow)
        cursorCol = clampCol(savedCursorCol)
        currentStyle.fg = savedStyle.fg
        currentStyle.bg = savedStyle.bg
        currentStyle.bold = savedStyle.bold
        currentStyle.dim = savedStyle.dim
        currentStyle.inverse = savedStyle.inverse
        currentStyle.underline = savedStyle.underline
        currentStyle.italic = savedStyle.italic
        currentStyle.strike = savedStyle.strike
        wrapPending = false
    }

    private fun lineFeed() {
        if (cursorRow == rows - 1) {
            scroll()
        } else {
            cursorRow++
        }
    }

    private fun reverseIndex() {
        if (cursorRow == 0) {
            scrollDownLines(1)
        } else {
            cursorRow--
        }
    }

    private fun scroll() {
        val g = activeGrid()
        scrollback.addLast(snapshotRow(g[0]))
        for (r in 0 until rows - 1) {
            for (c in 0 until cols) {
                g[r][c].ch = g[r + 1][c].ch
                g[r][c].style = g[r + 1][c].style
                g[r][c].wide = g[r + 1][c].wide
            }
        }
        clearRow(rows - 1, g)
        while (scrollback.size > maxScrollback) scrollback.removeFirst()
    }

    private fun scrollUpLines(count: Int) {
        repeat(min(count, rows)) { scroll() }
    }

    private fun scrollDownLines(count: Int) {
        val g = activeGrid()
        repeat(min(count, rows)) {
            for (r in rows - 1 downTo 1) {
                for (c in 0 until cols) {
                    g[r][c].ch = g[r - 1][c].ch
                    g[r][c].style = g[r - 1][c].style
                    g[r][c].wide = g[r - 1][c].wide
                }
            }
            clearRow(0, g)
        }
    }

    private fun insertLines(count: Int) {
        val g = activeGrid()
        repeat(min(count, rows - cursorRow)) {
            for (r in rows - 1 downTo cursorRow + 1) {
                for (c in 0 until cols) {
                    g[r][c].ch = g[r - 1][c].ch
                    g[r][c].style = g[r - 1][c].style
                    g[r][c].wide = g[r - 1][c].wide
                }
            }
            clearRow(cursorRow, g)
        }
    }

    private fun deleteLines(count: Int) {
        val g = activeGrid()
        repeat(min(count, rows - cursorRow)) {
            for (r in cursorRow until rows - 1) {
                for (c in 0 until cols) {
                    g[r][c].ch = g[r + 1][c].ch
                    g[r][c].style = g[r + 1][c].style
                    g[r][c].wide = g[r + 1][c].wide
                }
            }
            clearRow(rows - 1, g)
        }
    }

    private fun insertChars(count: Int) {
        val g = activeGrid()
        val r = cursorRow
        repeat(min(count, cols - cursorCol)) {
            for (c in cols - 1 downTo cursorCol + 1) {
                g[r][c].ch = g[r][c - 1].ch
                g[r][c].style = g[r][c - 1].style
                g[r][c].wide = g[r][c - 1].wide
            }
            setBlank(r, cursorCol, g)
        }
    }

    private fun deleteChars(count: Int) {
        val g = activeGrid()
        val r = cursorRow
        repeat(min(count, cols - cursorCol)) {
            for (c in cursorCol until cols - 1) {
                g[r][c].ch = g[r][c + 1].ch
                g[r][c].style = g[r][c + 1].style
                g[r][c].wide = g[r][c + 1].wide
            }
            setBlank(r, cols - 1, g)
        }
    }

    private fun eraseChars(count: Int) {
        val g = activeGrid()
        val r = cursorRow
        repeat(min(count, cols - cursorCol)) {
            setBlank(r, cursorCol + it, g)
        }
    }

    private fun eraseInLine(mode: Int) {
        val g = activeGrid()
        val r = cursorRow
        when (mode) {
            0 -> for (c in cursorCol until cols) setBlank(r, c, g)
            1 -> for (c in 0..cursorCol) setBlank(r, c, g)
            2 -> clearRow(r, g)
        }
    }

    private fun eraseInDisplay(mode: Int) {
        when (mode) {
            0 -> {
                eraseInLine(0)
                val g = activeGrid()
                for (r in cursorRow + 1 until rows) clearRow(r, g)
            }
            1 -> {
                eraseInLine(1)
                val g = activeGrid()
                for (r in 0 until cursorRow) clearRow(r, g)
            }
            2 -> {
                val g = activeGrid()
                for (r in 0 until rows) clearRow(r, g)
            }
            3 -> scrollback.clear()
        }
    }

    private fun putChar(ch: Char, width: Int) {
        if (wrapPending) {
            wrapPending = false
            lineFeed()
            cursorCol = 0
        }
        val g = activeGrid()
        if (width == 2 && cursorCol >= cols - 1) {
            lineFeed()
            cursorCol = 0
        }
        g[cursorRow][cursorCol].ch = ch
        g[cursorRow][cursorCol].style = currentStyle.copyOf()
        g[cursorRow][cursorCol].wide = width == 2
        cursorCol++
        if (width == 2 && cursorCol < cols) {
            g[cursorRow][cursorCol].ch = CONT
            g[cursorRow][cursorCol].style = currentStyle.copyOf()
            g[cursorRow][cursorCol].wide = false
            cursorCol++
        }
        if (cursorCol >= cols) {
            cursorCol = cols - 1
            wrapPending = true
        }
    }

    private fun clearRow(r: Int, g: Array<Array<Cell>>) {
        for (c in 0 until cols) {
            g[r][c].ch = ' '
            g[r][c].style = currentStyle.copyOf()
            g[r][c].wide = false
        }
    }

    private fun setBlank(r: Int, c: Int, g: Array<Array<Cell>>) {
        g[r][c].ch = ' '
        g[r][c].style = currentStyle.copyOf()
        g[r][c].wide = false
    }

    private fun snapshotRow(row: Array<Cell>): Array<Cell> {
        return Array(cols) { c ->
            Cell().also {
                it.ch = row[c].ch
                it.style = row[c].style.copyOf()
                it.wide = row[c].wide
            }
        }
    }

    private fun clearGrid(g: Array<Array<Cell>>) {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                g[r][c].ch = ' '
                g[r][c].style = Style()
                g[r][c].wide = false
            }
        }
    }

    private fun buildLine(row: Array<Cell>, trimTrailing: Boolean, cursorCol: Int = -1): TerminalLine {
        val runs = ArrayList<StyledRun>()
        val sb = StringBuilder()
        var current: Style? = null
        var end = row.size
        if (trimTrailing) {
            while (end > 0 && row[end - 1].ch == ' ') end--
        }
        for (c in 0 until end) {
            val cell = row[c]
            if (cell.ch == CONT) continue
            var style = cell.style
            if (c == cursorCol) {
                style = Style(cell.style.bg, cell.style.fg, false, false, false, false, false, false)
            }
            if (current == null) {
                current = style
            } else if (current != style) {
                runs.add(StyledRun(sb.toString(), current))
                sb.setLength(0)
                current = style
            }
            sb.append(cell.ch)
        }
        if (current != null && sb.isNotEmpty()) {
            runs.add(StyledRun(sb.toString(), current))
        }
        if (runs.isEmpty()) {
            runs.add(StyledRun("", Style()))
        }
        return TerminalLine(runs)
    }

    private fun isRowVisible(row: Array<Cell>): Boolean {
        for (c in 0 until cols) {
            val cell = row[c]
            if (cell.ch != ' ' && cell.ch != CONT) return true
            if (cell.style.bg != Style.DEFAULT_BG) return true
        }
        return false
    }

    private fun applySgr(params: IntArray) {
        if (params.isEmpty()) {
            resetStyle()
            return
        }
        var i = 0
        while (i < params.size) {
            val p = params[i]
            when {
                p == 0 -> resetStyle()
                p == 1 -> currentStyle.bold = true
                p == 2 -> currentStyle.dim = true
                p == 3 -> currentStyle.italic = true
                p == 4 -> currentStyle.underline = true
                p == 7 -> currentStyle.inverse = true
                p == 9 -> currentStyle.strike = true
                p == 21 || p == 22 -> currentStyle.bold = false
                p == 23 -> currentStyle.italic = false
                p == 24 -> currentStyle.underline = false
                p == 27 -> currentStyle.inverse = false
                p == 29 -> currentStyle.strike = false
                p == 39 -> currentStyle.fg = Style.DEFAULT_FG
                p == 49 -> currentStyle.bg = Style.DEFAULT_BG
                p in 30..37 -> currentStyle.fg = COLOR_16[p - 30]
                p in 40..47 -> currentStyle.bg = COLOR_16[p - 40]
                p in 90..97 -> currentStyle.fg = COLOR_16_BRIGHT[p - 90]
                p in 100..107 -> currentStyle.bg = COLOR_16_BRIGHT[p - 100]
                p == 38 || p == 48 -> {
                    val isFg = p == 38
                    if (i + 1 < params.size) {
                        val mode = params[i + 1]
                        if (mode == 5 && i + 2 < params.size) {
                            val color = color256(params[i + 2])
                            if (isFg) currentStyle.fg = color else currentStyle.bg = color
                            i += 2
                        } else if (mode == 2 && i + 4 < params.size) {
                            val color = rgb(params[i + 2], params[i + 3], params[i + 4])
                            if (isFg) currentStyle.fg = color else currentStyle.bg = color
                            i += 4
                        }
                    }
                }
            }
            i++
        }
    }

    private fun resetStyle() {
        currentStyle.fg = Style.DEFAULT_FG
        currentStyle.bg = Style.DEFAULT_BG
        currentStyle.bold = false
        currentStyle.dim = false
        currentStyle.inverse = false
        currentStyle.underline = false
        currentStyle.italic = false
        currentStyle.strike = false
    }

    private fun charWidth(cp: Int): Int {
        return when {
            cp < 0x20 -> 0
            cp < 0x7F -> 1
            cp < 0xA0 -> 1
            cp in 0x1100..0x115F -> 2
            cp in 0x2E80..0x303E -> 2
            cp in 0x3041..0x33FF -> 2
            cp in 0x3400..0x4DBF -> 2
            cp in 0x4E00..0x9FFF -> 2
            cp in 0xA000..0xA4CF -> 2
            cp in 0xAC00..0xD7A3 -> 2
            cp in 0xF900..0xFAFF -> 2
            cp in 0xFE30..0xFE4F -> 2
            cp in 0xFF00..0xFF60 -> 2
            cp in 0xFFE0..0xFFE6 -> 2
            cp in 0x1F300..0x1FAFF -> 2
            cp in 0x20000..0x2FFFD -> 2
            cp in 0x30000..0x3FFFD -> 2
            else -> 1
        }
    }

    private fun color256(n: Int): Int {
        return when {
            n < 16 -> COLOR_16[n.coerceIn(0, 15)]
            n in 16..231 -> {
                val v = n - 16
                val r = v / 36
                val g = (v % 36) / 6
                val b = v % 6
                val levels = intArrayOf(0, 95, 135, 175, 215, 255)
                rgb(levels[r], levels[g], levels[b])
            }
            else -> {
                val v = 8 + (n.coerceIn(232, 255) - 232) * 10
                rgb(v, v, v)
            }
        }
    }

    private fun rgb(r: Int, g: Int, b: Int): Int {
        return (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    }

    companion object {
        private val COLOR_16 = intArrayOf(
            0xFF000000.toInt(),
            0xFFCD3131.toInt(),
            0xFF0DBC79.toInt(),
            0xFFE5E510.toInt(),
            0xFF2472C8.toInt(),
            0xFFBC3FBC.toInt(),
            0xFF11A8CD.toInt(),
            0xFFE5E5E5.toInt()
        )
        private val COLOR_16_BRIGHT = intArrayOf(
            0xFF666666.toInt(),
            0xFFF14C4C.toInt(),
            0xFF23D18B.toInt(),
            0xFFF5F543.toInt(),
            0xFF3B8EEA.toInt(),
            0xFFD670D6.toInt(),
            0xFF29B8DB.toInt(),
            0xFFFFFFFF.toInt()
        )
    }
}
