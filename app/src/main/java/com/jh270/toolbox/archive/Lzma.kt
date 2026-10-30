package com.jh270.toolbox.archive

import java.io.ByteArrayOutputStream

internal object Lzma {
    fun decodeAlone(data: ByteArray): ByteArray {
        if (data.size < 13) throw IllegalArgumentException("invalid lzma")
        val props = data[0].toInt() and 0xFF
        val dict = readIntLe(data, 1)
        var size = 0L
        for (i in 0 until 8) size = size or ((data[5 + i].toLong() and 0xFF) shl (8 * i))
        val packed = data.copyOfRange(13, data.size)
        return decode(props, dict, packed, size, allowEnd = true)
    }

    fun decodeRaw(properties: ByteArray, packed: ByteArray, unpackedSize: Long): ByteArray {
        if (properties.size < 5) throw IllegalArgumentException("unsupported 7z codec")
        val dict = readIntLe(properties, 1)
        return decode(properties[0].toInt() and 0xFF, dict, packed, unpackedSize, allowEnd = false)
    }

    private fun decode(props: Int, dictSize: Int, packed: ByteArray, unpackedSize: Long, allowEnd: Boolean): ByteArray {
        val lcLp = splitProps(props)
        val decoder = Decoder(lcLp[0], lcLp[1], lcLp[2], dictSize)
        val unknown = unpackedSize < 0 || unpackedSize == -1L
        val limit = if (unknown) Long.MAX_VALUE else unpackedSize
        decoder.decompress(packed, limit, allowEnd && unknown)
        val out = decoder.sink.toByteArray()
        if (!unknown && out.size.toLong() != unpackedSize) throw IllegalArgumentException("invalid lzma")
        return out
    }

    private fun splitProps(props: Int): IntArray {
        if (props > (4 * 5 + 4) * 9 + 8) throw IllegalArgumentException("invalid lzma")
        val pb = props / (9 * 5)
        var rest = props - pb * 9 * 5
        val lp = rest / 9
        val lc = rest - lp * 9
        return intArrayOf(lc, lp, pb)
    }

    private fun readIntLe(data: ByteArray, offset: Int): Int {
        var value = 0
        for (i in 0 until 4) value = value or ((data[offset + i].toInt() and 0xFF) shl (8 * i))
        return value
    }
}

internal object Lzma2 {
    fun decode(property: Int, packed: ByteArray, unpackedSize: Long): ByteArray {
        if (property > 40) throw IllegalArgumentException("unsupported 7z codec")
        val dict = if (property == 40) {
            throw IllegalArgumentException("unsupported 7z codec")
        } else {
            (2 or (property and 1)) shl (property / 2 + 11)
        }
        if (dict <= 0) throw IllegalArgumentException("unsupported 7z codec")
        return decodeWithDict(dict, packed, unpackedSize)
    }

    fun decodeWithDict(dictSize: Int, packed: ByteArray, unpackedSize: Long): ByteArray {
        val sink = ByteArrayOutputStream()
        var decoder: Decoder? = null
        var pos = 0
        var needProps = true
        var needDict = true
        while (pos < packed.size) {
            val control = packed[pos].toInt() and 0xFF
            pos++
            if (control == 0) break
            if (control >= 0xE0 || control == 1) {
                needProps = true
                needDict = true
            } else if (needDict) {
                throw IllegalArgumentException("invalid lzma")
            }
            var unpack = if (control >= 0x80) (control and 0x1F) shl 16 else 0
            if (pos + 2 > packed.size) throw IllegalArgumentException("invalid lzma")
            unpack = unpack or ((packed[pos].toInt() and 0xFF) shl 8) or (packed[pos + 1].toInt() and 0xFF)
            unpack++
            pos += 2
            if (control < 0x80) {
                if (pos + unpack > packed.size) throw IllegalArgumentException("invalid lzma")
                if (decoder == null) decoder = Decoder(3, 0, 2, dictSize)
                if (control == 1) decoder.resetDictionary()
                decoder.copyRaw(packed, pos, unpack)
                needDict = false
                pos += unpack
                continue
            }
            if (pos + 2 > packed.size) throw IllegalArgumentException("invalid lzma")
            var pack = ((packed[pos].toInt() and 0xFF) shl 8) or (packed[pos + 1].toInt() and 0xFF)
            pack++
            pos += 2
            val reset = control >= 0xA0
            if (control >= 0xC0) {
                if (pos >= packed.size) throw IllegalArgumentException("invalid lzma")
                val props = packed[pos].toInt() and 0xFF
                pos++
                val parts = split(props)
                decoder = Decoder(parts[0], parts[1], parts[2], dictSize, decoder)
                needProps = false
            } else if (needProps || decoder == null) {
                throw IllegalArgumentException("invalid lzma")
            }
            if (pos + pack > packed.size) throw IllegalArgumentException("invalid lzma")
            val chunk = packed.copyOfRange(pos, pos + pack)
            pos += pack
            val active = decoder ?: throw IllegalArgumentException("invalid lzma")
            if (needDict) active.resetDictionary()
            if (reset) active.resetState()
            needDict = false
            active.decompress(chunk, unpack.toLong(), false)
        }
        val out = (decoder?.sink ?: sink).toByteArray()
        if (unpackedSize >= 0 && out.size.toLong() != unpackedSize) throw IllegalArgumentException("invalid lzma")
        return out
    }

    private fun split(props: Int): IntArray {
        if (props > (4 * 5 + 4) * 9 + 8) throw IllegalArgumentException("invalid lzma")
        val pb = props / (9 * 5)
        var rest = props - pb * 9 * 5
        val lp = rest / 9
        val lc = rest - lp * 9
        return intArrayOf(lc, lp, pb)
    }
}

private class Decoder(lc: Int, lp: Int, pb: Int, dictSize: Int, previous: Decoder? = null) {
    val sink: ByteArrayOutputStream = previous?.sink ?: ByteArrayOutputStream()
    private val window = ByteArray(dictSize.coerceAtLeast(4096))
    private var cursor = 0
    private var filled = 0
    private var position = 0
    private val posMask = (1 shl pb) - 1
    private val lpMask = (1 shl lp) - 1
    private val lcShift = 8 - lc
    private val literalCount = 1 shl (lc + lp)
    private val literals = Array(literalCount) { IntArray(0x300) }
    private val isMatch = Array(12) { IntArray(16) }
    private val isRep = IntArray(12)
    private val isRep0 = IntArray(12)
    private val isRep1 = IntArray(12)
    private val isRep2 = IntArray(12)
    private val isRep0Long = Array(12) { IntArray(16) }
    private val distSlots = Array(4) { IntArray(64) }
    private val distSpecial = arrayOf(
        IntArray(2), IntArray(2), IntArray(4), IntArray(4),
        IntArray(8), IntArray(8), IntArray(16), IntArray(16),
        IntArray(32), IntArray(32)
    )
    private val distAlign = IntArray(16)
    private val matchChoice = IntArray(2)
    private val repChoice = IntArray(2)
    private val matchLow = Array(16) { IntArray(8) }
    private val matchMid = Array(16) { IntArray(8) }
    private val matchHigh = IntArray(256)
    private val repLow = Array(16) { IntArray(8) }
    private val repMid = Array(16) { IntArray(8) }
    private val repHigh = IntArray(256)
    private val reps = IntArray(4)
    private var state = 0
    private var pending = 0
    private var pendingDist = 0

    init {
        if (previous != null) {
            cursor = previous.cursor
            filled = previous.filled
            position = previous.position
            previous.window.copyInto(window, endIndex = minOf(previous.window.size, window.size))
            }
        val keptPending = previous?.pending ?: 0
        val keptPendingDist = previous?.pendingDist ?: 0
        val keptState = previous?.state ?: 0
        val keptReps = previous?.reps?.copyOf()
        resetState()
        pending = keptPending
        pendingDist = keptPendingDist
        if (previous != null && keptReps != null) {
            state = keptState
            keptReps.copyInto(reps)
            copyProbs(previous)
        }
    }

    fun resetDictionary() {
        cursor = 0
        filled = 0
        position = 0
        pending = 0
    }

    fun resetState() {
        state = 0
        reps.fill(0)
        fill(isRep)
        fill(isRep0)
        fill(isRep1)
        fill(isRep2)
        fill(distAlign)
        fill(matchChoice)
        fill(repChoice)
        fill(matchHigh)
        fill(repHigh)
        for (row in isMatch) fill(row)
        for (row in isRep0Long) fill(row)
        for (row in distSlots) fill(row)
        for (row in distSpecial) fill(row)
        for (row in matchLow) fill(row)
        for (row in matchMid) fill(row)
        for (row in repLow) fill(row)
        for (row in repMid) fill(row)
        for (row in literals) fill(row)
    }

    fun copyRaw(data: ByteArray, offset: Int, length: Int) {
        for (i in 0 until length) put(data[offset + i].toInt() and 0xFF)
    }

    fun decompress(packed: ByteArray, limit: Long, untilEnd: Boolean) {
        val range = Range(packed)
        var produced = 0L
        val start = sink.size()
        while (pending > 0 && produced < limit) {
            put(byteAt(pendingDist))
            pending--
            produced++
        }
        while (produced < limit) {
            val posState = position and posMask
            if (range.bit(isMatch[state], posState) == 0) {
                decodeLiteral(range)
                produced++
            } else {
                val len = if (range.bit(isRep, state) == 0) decodeMatch(range) else decodeRep(range, posState)
                if (reps[0] == -1) {
                    if (!untilEnd) throw IllegalArgumentException("invalid lzma")
                    break
                }
                var left = len
                while (left > 0 && produced < limit) {
                    put(byteAt(reps[0]))
                    left--
                    produced++
                }
                pending = left
                pendingDist = reps[0]
            }
        }
        if (!untilEnd && sink.size() - start != limit.toInt() && limit <= Int.MAX_VALUE) {
            if (produced != limit) throw IllegalArgumentException("invalid lzma")
        }
    }

    private fun decodeLiteral(range: Range) {
        val prev = if (filled == 0) 0 else byteAt(0)
        val index = ((position and lpMask) shl (8 - lcShift)) + (prev ushr lcShift)
        val probs = literals[index]
        var symbol = 1
        if (state < 7) {
            do {
                symbol = (symbol shl 1) or range.bit(probs, symbol)
            } while (symbol < 0x100)
        } else {
            var match = byteAt(reps[0])
            var offset = 0x100
            do {
                match = match shl 1
                val matchBit = match and offset
                val bit = range.bit(probs, offset + matchBit + symbol)
                symbol = (symbol shl 1) or bit
                offset = offset and ((0 - bit) xor matchBit.inv())
            } while (symbol < 0x100)
        }
        put(symbol and 0xFF)
        state = when {
            state < 4 -> 0
            state < 10 -> state - 3
            else -> state - 6
        }
    }

    private fun decodeMatch(range: Range): Int {
        state = if (state < 7) 7 else 10
        reps[3] = reps[2]
        reps[2] = reps[1]
        reps[1] = reps[0]
        val len = decodeLen(range, matchChoice, matchLow, matchMid, matchHigh)
        val distState = if (len < 6) len - 2 else 3
        val slot = range.tree(distSlots[distState])
        if (slot < 4) {
            reps[0] = slot
        } else {
            val limit = (slot shr 1) - 1
            reps[0] = (2 or (slot and 1)) shl limit
            if (slot < 14) {
                reps[0] = reps[0] or range.reverse(distSpecial[slot - 4])
            } else {
                reps[0] = reps[0] or (range.direct(limit - 4) shl 4)
                reps[0] = reps[0] or range.reverse(distAlign)
            }
        }
        return len
    }

    private fun decodeRep(range: Range, posState: Int): Int {
        if (range.bit(isRep0, state) == 0) {
            if (range.bit(isRep0Long[state], posState) == 0) {
                state = if (state < 7) 9 else 11
                return 1
            }
        } else {
            val dist = if (range.bit(isRep1, state) == 0) {
                reps[1]
            } else if (range.bit(isRep2, state) == 0) {
                val tmp = reps[2]
                reps[2] = reps[1]
                tmp
            } else {
                val tmp = reps[3]
                reps[3] = reps[2]
                reps[2] = reps[1]
                tmp
            }
            reps[1] = reps[0]
            reps[0] = dist
        }
        state = if (state < 7) 8 else 11
        return decodeLen(range, repChoice, repLow, repMid, repHigh)
    }

    private fun decodeLen(range: Range, choice: IntArray, low: Array<IntArray>, mid: Array<IntArray>, high: IntArray): Int {
        val posState = position and posMask
        if (range.bit(choice, 0) == 0) return range.tree(low[posState]) + 2
        if (range.bit(choice, 1) == 0) return range.tree(mid[posState]) + 10
        return range.tree(high) + 18
    }

    private fun put(value: Int) {
        window[cursor] = value.toByte()
        cursor++
        if (cursor == window.size) cursor = 0
        if (filled < window.size) filled++
        position++
        sink.write(value)
    }

    private fun byteAt(dist: Int): Int {
        if (dist < 0 || dist >= filled) throw IllegalArgumentException("invalid lzma")
        var index = cursor - dist - 1
        if (index < 0) index += window.size
        return window[index].toInt() and 0xFF
    }

    private fun copyProbs(previous: Decoder) {
        copy(isRep, previous.isRep)
        copy(isRep0, previous.isRep0)
        copy(isRep1, previous.isRep1)
        copy(isRep2, previous.isRep2)
        copy(distAlign, previous.distAlign)
        copy(matchChoice, previous.matchChoice)
        copy(repChoice, previous.repChoice)
        copy(matchHigh, previous.matchHigh)
        copy(repHigh, previous.repHigh)
        for (i in isMatch.indices) copy(isMatch[i], previous.isMatch[i])
        for (i in isRep0Long.indices) copy(isRep0Long[i], previous.isRep0Long[i])
        for (i in distSlots.indices) copy(distSlots[i], previous.distSlots[i])
        for (i in distSpecial.indices) copy(distSpecial[i], previous.distSpecial[i])
        for (i in matchLow.indices) copy(matchLow[i], previous.matchLow[i])
        for (i in matchMid.indices) copy(matchMid[i], previous.matchMid[i])
        for (i in repLow.indices) copy(repLow[i], previous.repLow[i])
        for (i in repMid.indices) copy(repMid[i], previous.repMid[i])
        if (literals.size == previous.literals.size) {
            for (i in literals.indices) copy(literals[i], previous.literals[i])
        }
    }

    private fun fill(probs: IntArray) {
        probs.fill(1024)
    }

    private fun copy(dest: IntArray, src: IntArray) {
        src.copyInto(dest, endIndex = minOf(dest.size, src.size))
    }
}

private class Range(data: ByteArray) {
    private val input = data
    private var offset = 0
    private var range = -1
    private var code = 0

    init {
        if (data.size < 5 || data[0].toInt() != 0) throw IllegalArgumentException("invalid lzma")
        offset = 1
        code = readInt()
    }

    fun finished(): Boolean = offset >= input.size && code == 0

    fun bit(probs: IntArray, index: Int): Int {
        normalize()
        val prob = probs[index]
        val bound = (range ushr 11) * prob
        val bit = if (unsignedLess(code, bound)) {
            range = bound
            probs[index] = prob + ((2048 - prob) ushr 5)
            0
        } else {
            range -= bound
            code -= bound
            probs[index] = prob - (prob ushr 5)
            1
        }
        return bit
    }

    fun tree(probs: IntArray): Int {
        var symbol = 1
        do {
            symbol = (symbol shl 1) or bit(probs, symbol)
        } while (symbol < probs.size)
        return symbol - probs.size
    }

    fun reverse(probs: IntArray): Int {
        var symbol = 1
        var result = 0
        var i = 0
        do {
            val value = bit(probs, symbol)
            symbol = (symbol shl 1) or value
            result = result or (value shl i)
            i++
        } while (symbol < probs.size)
        return result
    }

    fun direct(count: Int): Int {
        var left = count
        var result = 0
        while (left > 0) {
            normalize()
            range = range ushr 1
            val t = (code - range) ushr 31
            code -= range and (t - 1)
            result = (result shl 1) or (1 - t)
            left--
        }
        return result
    }

    private fun normalize() {
        if ((range and TOP_MASK) == 0) {
            if (offset >= input.size) throw IllegalArgumentException("invalid lzma")
            code = (code shl 8) or (input[offset].toInt() and 0xFF)
            offset++
            range = range shl 8
        }
    }

    private fun readInt(): Int {
        if (offset + 4 > input.size) throw IllegalArgumentException("invalid lzma")
        var value = 0
        repeat(4) {
            value = (value shl 8) or (input[offset].toInt() and 0xFF)
            offset++
        }
        return value
    }

    private fun unsignedLess(left: Int, right: Int): Boolean =
        (left xor Int.MIN_VALUE) < (right xor Int.MIN_VALUE)

    companion object {
        private const val TOP_MASK = 0xFF000000.toInt()
    }
}
