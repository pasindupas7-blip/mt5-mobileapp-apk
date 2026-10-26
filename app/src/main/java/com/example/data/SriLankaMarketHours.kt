package com.example.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Central utility for Sri Lanka Standard Time (Asia/Colombo, UTC+05:30)
 * and the exact weekly trading schedule:
 * - Week Starts (Market Opens): Monday 03:30 AM Sri Lanka Time
 * - Week Ends (Market Closes): Saturday 02:29 AM Sri Lanka Time (inclusive through 02:29:59.999)
 * - Market Closed Window: Saturday 02:30:00 AM through Monday 03:29:59.999 AM Sri Lanka Time
 */
object SriLankaMarketHours {

    val SRI_LANKA_TIME_ZONE: TimeZone = TimeZone.getTimeZone("Asia/Colombo")

    // Monday 03:30 AM = 3 * 60 + 30 = 210 minutes from midnight
    private const val MONDAY_OPEN_MINUTE_OF_DAY = 3 * 60 + 30 // 210

    // Saturday 02:29 AM = 2 * 60 + 29 = 149 minutes from midnight (inclusive up to 02:29:59.999)
    private const val SATURDAY_CLOSE_LAST_MINUTE_OF_DAY = 2 * 60 + 29 // 149

    /**
     * Returns true if the given epoch millisecond timestamp falls inside the active trading week
     * in Sri Lanka Standard Time:
     * Monday 03:30:00.000 AM -> Saturday 02:29:59.999 AM (Asia/Colombo).
     */
    fun isMarketOpen(timestampMs: Long): Boolean {
        val cal = Calendar.getInstance(SRI_LANKA_TIME_ZONE, Locale.US)
        cal.timeInMillis = timestampMs
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val minuteOfDay = hour * 60 + minute

        return when (dayOfWeek) {
            Calendar.SUNDAY -> false
            Calendar.MONDAY -> minuteOfDay >= MONDAY_OPEN_MINUTE_OF_DAY
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY -> true
            Calendar.SATURDAY -> minuteOfDay <= SATURDAY_CLOSE_LAST_MINUTE_OF_DAY
            else -> false
        }
    }

    /**
     * If [timestampMs] is during the weekend closed window (Saturday 02:30 AM .. Monday 03:29:59 AM),
     * rewinds and clamps it to the most recent Saturday 02:29:00 AM in Sri Lanka Time.
     * If the market is currently open, returns [timestampMs] unchanged.
     */
    fun clampToLastOpenMarketTimestamp(timestampMs: Long): Long {
        if (isMarketOpen(timestampMs)) return timestampMs

        val cal = Calendar.getInstance(SRI_LANKA_TIME_ZONE, Locale.US)
        cal.timeInMillis = timestampMs
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

        when (dayOfWeek) {
            Calendar.SUNDAY -> {
                cal.add(Calendar.DAY_OF_YEAR, -1) // Move back to Saturday
            }
            Calendar.MONDAY -> {
                cal.add(Calendar.DAY_OF_YEAR, -2) // Move back to Saturday
            }
            Calendar.SATURDAY -> {
                // Already Saturday (after 02:29 AM)
            }
        }

        cal.set(Calendar.HOUR_OF_DAY, 2)
        cal.set(Calendar.MINUTE, 29)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Steps backward by [intervalMs] from [currentTimestampMs], automatically skipping the closed
     * weekend window (Saturday 02:30 AM .. Monday 03:29:59 AM Sri Lanka Time) so that generated
     * historical candles only exist inside Monday 03:30 AM .. Saturday 02:29 AM.
     */
    fun stepBackwardOpenMarketBucket(currentTimestampMs: Long, intervalMs: Long): Long {
        val safeStep = intervalMs.coerceAtLeast(60_000L)
        var candidate = ((currentTimestampMs - safeStep) / safeStep) * safeStep
        if (isMarketOpen(candidate)) {
            return candidate
        }
        val clampedSaturdayClose = clampToLastOpenMarketTimestamp(candidate)
        candidate = (clampedSaturdayClose / safeStep) * safeStep
        while (!isMarketOpen(candidate)) {
            candidate -= safeStep
        }
        return candidate
    }

    /**
     * Generates a chronological list of [count] open-market candle bucket timestamps ending at [nowMs],
     * strictly within Monday 03:30 AM .. Saturday 02:29 AM Sri Lanka Time.
     */
    fun buildOpenMarketTimestamps(count: Int, intervalMs: Long, nowMs: Long = System.currentTimeMillis()): LongArray {
        val safeStep = intervalMs.coerceAtLeast(60_000L)
        val result = LongArray(count)
        if (count <= 0) return result

        val clampedNow = clampToLastOpenMarketTimestamp(nowMs)
        var current = (clampedNow / safeStep) * safeStep
        while (!isMarketOpen(current)) {
            current = stepBackwardOpenMarketBucket(current, safeStep)
        }

        for (i in (count - 1) downTo 0) {
            result[i] = current
            if (i > 0) {
                current = stepBackwardOpenMarketBucket(current, safeStep)
            }
        }
        return result
    }

    fun formatTickTime(timestampMs: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
        sdf.timeZone = SRI_LANKA_TIME_ZONE
        return sdf.format(Date(timestampMs))
    }

    fun formatDateTime(timestampMs: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.US)
        sdf.timeZone = SRI_LANKA_TIME_ZONE
        return sdf.format(Date(timestampMs))
    }

    fun formatHistoryTimestamp(timestampMs: Long = System.currentTimeMillis()): String {
        return formatDateTime(timestampMs)
    }

    fun formatCandleTime(timestampMs: Long, intervalMs: Long): String {
        val pattern = when {
            intervalMs >= 86_400_000L -> "dd MMM"
            intervalMs >= 3_600_000L -> "dd MMM HH:mm"
            else -> "HH:mm"
        }
        val sdf = SimpleDateFormat(pattern, Locale.US)
        sdf.timeZone = SRI_LANKA_TIME_ZONE
        return sdf.format(Date(timestampMs))
    }

    fun formatChartAxisDateTime(timestampMs: Long): String {
        val sdf = SimpleDateFormat("d MMM HH:mm", Locale.US)
        sdf.timeZone = SRI_LANKA_TIME_ZONE
        return sdf.format(Date(timestampMs))
    }

    /**
     * Parses an MT5 date/time string ("yyyy.MM.dd HH:mm:ss", "yyyy.MM.dd HH:mm", or "yyyy.MM.dd")
     * in Sri Lanka Standard Time (Asia/Colombo) into epoch milliseconds for chronological sorting
     * and period filtering.
     */
    fun parseMt5DateTimeMillis(dateStr: String): Long {
        val clean = dateStr.trim().replace("-", ".").replace("/", ".")
        if (clean.isEmpty()) return 0L
        val patterns = listOf(
            "yyyy.MM.dd HH:mm:ss",
            "yyyy.MM.dd HH:mm",
            "yyyy.MM.dd"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = SRI_LANKA_TIME_ZONE
                    isLenient = true
                }
                val parsed = sdf.parse(clean)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return 0L
    }

    /**
     * Checks whether a trade/withdrawal timestamp string falls within the selected MT5 HistoryPeriod
     * relative to current Sri Lanka Standard Time (or the MT5 reference date 2026.10.25).
     */
    fun isWithinHistoryPeriod(
        dateStr: String,
        periodName: String,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (periodName == "CUSTOM") return true
        val itemMs = parseMt5DateTimeMillis(dateStr)
        if (itemMs <= 0L) return true

        val ref2026Ms = parseMt5DateTimeMillis("2026.10.25 23:59:59")
        return matchesPeriodAnchor(itemMs, periodName, nowMs) ||
            (ref2026Ms > 0L && matchesPeriodAnchor(itemMs, periodName, ref2026Ms))
    }

    private fun matchesPeriodAnchor(itemMs: Long, periodName: String, anchorMs: Long): Boolean {
        val cal = Calendar.getInstance(SRI_LANKA_TIME_ZONE, Locale.US)
        cal.timeInMillis = anchorMs
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val endOfAnchorDayMs = cal.timeInMillis

        val startCal = Calendar.getInstance(SRI_LANKA_TIME_ZONE, Locale.US)
        startCal.timeInMillis = anchorMs
        startCal.set(Calendar.HOUR_OF_DAY, 0)
        startCal.set(Calendar.MINUTE, 0)
        startCal.set(Calendar.SECOND, 0)
        startCal.set(Calendar.MILLISECOND, 0)

        when (periodName) {
            "TODAY" -> {
                // Keep startCal at 00:00:00 of anchor day
            }
            "LAST_WEEK" -> {
                startCal.add(Calendar.DAY_OF_YEAR, -7)
            }
            "LAST_MONTH" -> {
                startCal.add(Calendar.MONTH, -1)
            }
            "LAST_3_MONTHS" -> {
                startCal.add(Calendar.MONTH, -3)
            }
            else -> return true
        }

        val startMs = startCal.timeInMillis
        return itemMs in startMs..(endOfAnchorDayMs + 86_400_000L)
    }
}
