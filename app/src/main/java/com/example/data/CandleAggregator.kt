package com.example.data

import com.example.model.Candle
import com.example.model.Timeframe
import kotlin.math.max
import kotlin.math.min

/**
 * Aggregates real-time market ticks into OHLC candlesticks for a given timeframe interval.
 * Strictly enforces Sri Lanka Standard Time (Asia/Colombo) and only forms/updates candles
 * within the active trading week: Monday 03:30 AM to Saturday 02:29 AM Sri Lanka Time.
 */
class CandleAggregator(
    initialTimeframe: Timeframe = Timeframe.M1,
    private val maxCandles: Int = 25000 // Supports full 2-week (14-day) M1 history and trade anchors across all timeframes
) {
    private var currentTimeframe: Timeframe = initialTimeframe
    private var intervalMillis: Long = (initialTimeframe.minutes * 60_000L).coerceAtLeast(60_000L)
    private val candles = mutableListOf<Candle>()
    private var currentBucketStart: Long = -1L

    @Synchronized
    fun setTimeframe(timeframe: Timeframe) {
        currentTimeframe = timeframe
        intervalMillis = (timeframe.minutes * 60_000L).coerceAtLeast(60_000L)
        currentBucketStart = -1L
    }

    @Synchronized
    fun seedHistory(initialCandles: List<Candle>) {
        candles.clear()
        // Strictly keep only candles that fall inside Monday 03:30 .. Saturday 02:29 Sri Lanka Time
        val validOpenMarketCandles = initialCandles.filter { candle ->
            candle.timestamp <= 0L || SriLankaMarketHours.isMarketOpen(candle.timestamp)
        }
        candles.addAll(validOpenMarketCandles.takeLast(maxCandles))
        if (candles.isNotEmpty()) {
            val lastTs = candles.last().timestamp
            currentBucketStart = if (lastTs > 0L) {
                (lastTs / intervalMillis) * intervalMillis
            } else {
                val clampedNow = SriLankaMarketHours.clampToLastOpenMarketTimestamp(System.currentTimeMillis())
                (clampedNow / intervalMillis) * intervalMillis
            }
        }
    }

    @Synchronized
    fun onTick(
        price: Double,
        timestamp: Long = System.currentTimeMillis(),
        maxCandleExcursion: Double = Double.MAX_VALUE
    ): Double {
        // Do NOT create or mutate candlesticks outside Monday 03:30 AM .. Saturday 02:29 AM Sri Lanka Time
        if (!SriLankaMarketHours.isMarketOpen(timestamp)) {
            return candles.lastOrNull()?.close ?: price
        }

        val bucket = (timestamp / intervalMillis) * intervalMillis
        if (!SriLankaMarketHours.isMarketOpen(bucket)) {
            return candles.lastOrNull()?.close ?: price
        }

        val formattedTime = SriLankaMarketHours.formatCandleTime(bucket, intervalMillis)

        if (candles.isEmpty()) {
            currentBucketStart = bucket
            candles.add(
                Candle(
                    time = formattedTime,
                    timestamp = bucket,
                    open = price,
                    high = price,
                    low = price,
                    close = price
                )
            )
            return price
        }

        if (currentBucketStart < 0L) {
            currentBucketStart = bucket
        }

        val effectivePrice: Double
        if (bucket > currentBucketStart) {
            // Finalize previous candle and open a new candle anchored to previous close for continuity
            val prevClose = candles.last().close
            val boundedClose = price.coerceIn(prevClose - maxCandleExcursion, prevClose + maxCandleExcursion)
            val newHigh = max(prevClose, boundedClose)
            val newLow = min(prevClose, boundedClose)

            currentBucketStart = bucket
            candles.add(
                Candle(
                    time = formattedTime,
                    timestamp = bucket,
                    open = prevClose,
                    high = newHigh,
                    low = newLow,
                    close = boundedClose
                )
            )
            if (candles.size > maxCandles) {
                candles.removeAt(0)
            }
            effectivePrice = boundedClose
        } else {
            // Update current active candle (open stays fixed; high/low/close update within realistic range)
            val lastIndex = candles.lastIndex
            val current = candles[lastIndex]
            val boundedClose = price.coerceIn(
                current.open - maxCandleExcursion,
                current.open + maxCandleExcursion
            )
            val updatedHigh = max(current.high, boundedClose).coerceAtMost(current.open + maxCandleExcursion)
            val updatedLow = min(current.low, boundedClose).coerceAtLeast(current.open - maxCandleExcursion)

            candles[lastIndex] = current.copy(
                high = max(updatedHigh, max(current.open, boundedClose)),
                low = min(updatedLow, min(current.open, boundedClose)),
                close = boundedClose
            )
            effectivePrice = boundedClose
        }

        return effectivePrice
    }

    @Synchronized
    fun getCandles(): List<Candle> = candles.toList()
}
