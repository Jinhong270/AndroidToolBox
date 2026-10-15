package com.jh270.toolbox.ssh

internal fun readShellLoop(
    read: (ByteArray) -> Int,
    isChannelAlive: () -> Boolean,
    decode: (ByteArray, Int) -> String,
    onOutput: (String) -> Unit
): Boolean {
    val buffer = ByteArray(4096)
    var ended = false
    try {
        while (!ended) {
            val count = read(buffer)
            if (count < 0) {
                ended = true
                break
            }
            if (count > 0) {
                val text = decode(buffer, count)
                if (text.isNotEmpty()) onOutput(text)
            }
            if (!isChannelAlive()) {
                ended = true
            }
        }
    } catch (_: Exception) {
        ended = true
    }
    return ended
}

internal fun shouldReportShellExit(
    ended: Boolean,
    token: Int,
    activeToken: Int,
    closingLocally: Boolean
): Boolean {
    if (!ended) return false
    if (token != activeToken) return false
    return !closingLocally
}
