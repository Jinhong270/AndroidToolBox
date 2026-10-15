package com.jh270.toolbox

import com.jh270.toolbox.ssh.readShellLoop
import com.jh270.toolbox.ssh.shouldReportShellExit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellSessionWatcherTest {

    private fun decode(buffer: ByteArray, count: Int): String = String(buffer, 0, count, Charsets.UTF_8)

    @Test
    fun reportsEndOnEofAfterDeliveringOutput() {
        val chunks = listOf("hello", "world")
        var index = 0
        val output = StringBuilder()

        val ended = readShellLoop(
            read = { buffer ->
                if (index >= chunks.size) {
                    -1
                } else {
                    val next = chunks[index++]
                    next.toByteArray(Charsets.UTF_8).copyInto(buffer)
                    next.length
                }
            },
            isChannelAlive = { true },
            decode = ::decode,
            onOutput = { output.append(it) }
        )

        assertTrue("EOF must end the read loop", ended)
        assertEquals("helloworld", output.toString())
    }

    @Test
    fun reportsEndWhenTheConnectionDropsAbruptly() {
        var calls = 0

        val ended = readShellLoop(
            read = { buffer ->
                calls++
                if (calls == 1) {
                    "banner".toByteArray(Charsets.UTF_8).copyInto(buffer)
                    6
                } else {
                    throw IllegalStateException("connection reset by peer")
                }
            },
            isChannelAlive = { true },
            decode = ::decode,
            onOutput = { }
        )

        assertTrue("a broken socket must end the read loop", ended)
    }

    @Test
    fun reportsEndWhenTheChannelIsNoLongerAlive() {
        val ended = readShellLoop(
            read = { 0 },
            isChannelAlive = { false },
            decode = ::decode,
            onOutput = { }
        )

        assertTrue("a dead channel must end the read loop", ended)
    }

    @Test
    fun exitIsReportedOnlyForTheActiveSession() {
        assertTrue(shouldReportShellExit(ended = true, token = 3, activeToken = 3, closingLocally = false))
        assertFalse(shouldReportShellExit(ended = false, token = 3, activeToken = 3, closingLocally = false))
        assertFalse(shouldReportShellExit(ended = true, token = 2, activeToken = 3, closingLocally = false))
        assertFalse(shouldReportShellExit(ended = true, token = 3, activeToken = 3, closingLocally = true))
    }
}
