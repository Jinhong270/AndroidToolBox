package com.jh270.toolbox.domain

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class ExpiryHint(val text: String, val urgent: Boolean)

object DomainDates {
    private val months = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )

    fun parse(raw: String): Long? {
        var text = raw.trim()
        if (text.isEmpty()) return null
        if (text.endsWith("Z", ignoreCase = true)) {
            text = text.dropLast(1).trim()
        } else if (text.endsWith("UTC", ignoreCase = true)) {
            text = text.dropLast(3).trim()
        }
        val stripped = stripOffset(text)
        text = stripped.first
        val offsetMinutes = stripped.second
        val parts = text.split(Regex("[T ]"), limit = 2)
        val date = parseDate(parts[0]) ?: return null
        val time = if (parts.size == 2) parseTime(parts[1]) ?: return null else intArrayOf(0, 0, 0)
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.clear()
        calendar.set(date[0], date[1] - 1, date[2], time[0], time[1], time[2])
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis - offsetMinutes * 60_000L
    }

    fun format(raw: String?): String {
        if (raw.isNullOrBlank()) return "—"
        val millis = parse(raw) ?: return raw.trim()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = millis
        return String.format(
            Locale.ROOT,
            "%04d-%02d-%02d %02d:%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE)
        )
    }

    fun expiryHint(raw: String?, now: Long = System.currentTimeMillis()): ExpiryHint? {
        if (raw.isNullOrBlank()) return null
        val millis = parse(raw) ?: return null
        val delta = millis - now
        val days = delta / 86_400_000L
        val text = when {
            delta >= 0 && days == 0L -> "今天到期"
            delta < 0 && days == 0L -> "已过期"
            days == 1L -> "还有 1 天到期"
            days > 1L -> "还有 $days 天到期"
            days == -1L -> "已过期 1 天"
            else -> "已过期 ${-days} 天"
        }
        return ExpiryHint(text, delta < 30L * 86_400_000L)
    }

    private fun stripOffset(raw: String): Pair<String, Int> {
        val colon = Regex("""([+-])(\d{2}):(\d{2})$""").find(raw)
        if (colon != null && colon.range.first > 0 && raw[colon.range.first - 1].isDigit()) {
            return raw.substring(0, colon.range.first).trim() to offsetMinutes(colon.groupValues[1], colon.groupValues[2], colon.groupValues[3])
        }
        val compact = Regex("""([+-])(\d{2})(\d{2})$""").find(raw)
        if (compact != null && compact.range.first > 0 && raw[compact.range.first - 1].isDigit()) {
            val prefix = raw.substring(0, compact.range.first)
            if (prefix.contains(':') || prefix.contains('T') || prefix.contains('t')) {
                return prefix.trim() to offsetMinutes(compact.groupValues[1], compact.groupValues[2], compact.groupValues[3])
            }
        }
        return raw to 0
    }

    private fun offsetMinutes(sign: String, hours: String, minutes: String): Int {
        val direction = if (sign == "-") -1 else 1
        return direction * (hours.toInt() * 60 + minutes.toInt())
    }

    private fun parseDate(raw: String): IntArray? {
        val text = raw.trim().trimEnd('.')
        Regex("""^(\d{4})[-./](\d{1,2})[-./](\d{1,2})$""").matchEntire(text)?.let {
            return ints(it.groupValues[1], it.groupValues[2], it.groupValues[3])
        }
        Regex("""^(\d{4})(\d{2})(\d{2})$""").matchEntire(text)?.let {
            return ints(it.groupValues[1], it.groupValues[2], it.groupValues[3])
        }
        Regex("""^(\d{1,2})[-./]([A-Za-z]{3})[-./](\d{4})$""").matchEntire(text)?.let {
            val month = months[it.groupValues[2].lowercase(Locale.ROOT)] ?: return null
            val day = it.groupValues[1].toIntOrNull() ?: return null
            val year = it.groupValues[3].toIntOrNull() ?: return null
            if (year !in 1970..2200 || day !in 1..31) return null
            return intArrayOf(year, month, day)
        }
        return null
    }

    private fun parseTime(raw: String): IntArray? {
        val text = raw.trim().substringBefore(' ')
        val match = Regex("""^(\d{1,2}):(\d{2})(?::(\d{2}))?(?:\.\d+)?$""").matchEntire(text) ?: return null
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        val second = match.groupValues[3].toIntOrNull() ?: 0
        if (hour !in 0..23 || minute !in 0..59 || second !in 0..60) return null
        return intArrayOf(hour, minute, second)
    }

    private fun ints(year: String, month: String, day: String): IntArray? {
        val y = year.toIntOrNull() ?: return null
        val m = month.toIntOrNull() ?: return null
        val d = day.toIntOrNull() ?: return null
        if (y !in 1970..2200 || m !in 1..12 || d !in 1..31) return null
        return intArrayOf(y, m, d)
    }
}
