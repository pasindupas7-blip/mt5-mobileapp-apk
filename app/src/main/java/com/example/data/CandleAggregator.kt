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
        val prevActive = candles.lastOrNull()
        val prevBucket = currentBucketStart

        candles.clear()
        val clampedNow = SriLankaMarketHours.clampToLastOpenMarketTimestamp(SriLankaMarketHours.chartNowMillis())
        val maxAllowedBucket = (clampedNow / intervalMillis) * intervalMillis

        // Strictly keep only candles that fall inside Monday 03:30 .. Saturday 02:29 Sri Lanka Time
        // and do not exceed the TradingView-aligned chart clock (now - 10 minutes)
        val validOpenMarketCandles = initialCandles.filter { candle ->
            candle.timestamp <= 0L ||
                (SriLankaMarketHours.isMarketOpen(candle.timestamp) && candle.timestamp <= maxAllowedBucket)
        }
        candles.addAll(validOpenMarketCandles.takeLast(maxCandles))
        if (candles.isNotEmpty()) {
            val lastTs = candles.last().timestamp
            currentBucketStart = if (lastTs > 0L) {
                (lastTs / intervalMillis) * intervalMillis
            } else {
                maxAllowedBucket
            }

            // Preserve accumulated live high/low/close on the active candle only if re-seeded from the same open price
            if (prevActive != null && prevBucket > 0L && prevBucket == currentBucketStart) {
                val seededLast = candles.last()
                if (kotlin.math.abs(prevActive.open - seededLast.open) <= 1e-5) {
                    val liveOpen = seededLast.open
                    val liveClose = prevActive.close
                    val liveHigh = max(max(seededLast.high, prevActive.high), max(liveOpen, liveClose))
                    val liveLow = min(min(seededLast.low, prevActive.low), min(liveOpen, liveClose))
                    candles[candles.lastIndex] = seededLast.copy(
                        open = liveOpen,
                        high = liveHigh,
                        low = liveLow,
                        close = liveClose
                    )
                }
            }
        }
    }

    @Synchronized
    fun onTick(
        price: Double,
        timestamp: Long = SriLankaMarketHours.chartNowMillis(),
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
            currentBucketStart = (candles.last().timestamp / intervalMillis) * intervalMillis
            if (currentBucketStart <= 0L) currentBucketStart = bucket
        }

        val effectivePrice: Double
        if (bucket > currentBucketStart) {
            var runningPrevClose = candles.last().close
            val boundedTarget = price.coerceIn(
                runningPrevClose - maxCandleExcursion,
                runningPrevClose + maxCandleExcursion
            )

            // If more than 1 interval elapsed (e.g. up to 15 bars on M1/M5/M15/M30), step through intermediate buckets
            val gapSteps = ((bucket - currentBucketStart) / intervalMillis).toInt()
            if (gapSteps in 2..15) {
                for (step in 1 until gapSteps) {
                    val midBucket = currentBucketStart + step * intervalMillis
                    if (!SriLankaMarketHours.isMarketOpen(midBucket)) continue
                    val frac = step.toDouble() / gapSteps.toDouble()
                    val midClose = runningPrevClose + (boundedTarget - runningPrevClose) * frac
                    val midWick = kotlin.math.abs(midClose - runningPrevClose).coerceAtLeast(runningPrevClose * 0.00008) * 0.35
                    candles.add(
                        Candle(
                            time = SriLankaMarketHours.formatCandleTime(midBucket, intervalMillis),
                            timestamp = midBucket,
                            open = runningPrevClose,
                            high = max(runningPrevClose, midClose) + midWick,
                            low = (min(runningPrevClose, midClose) - midWick).coerceAtLeast(0.00001),
                            close = midClose
                        )
                    )
                    runningPrevClose = midClose
                }
            }

            // Open the new active candle anchored to previous close for seamless lower-timeframe continuity
            val newHigh = max(runningPrevClose, boundedTarget)
            val newLow = min(runningPrevClose, boundedTarget)

            currentBucketStart = bucket
            candles.add(
                Candle(
                    time = formattedTime,
                    timestamp = bucket,
                    open = runningPrevClose,
                    high = newHigh,
                    low = newLow,
                    close = boundedTarget
                )
            )
            while (candles.size > maxCandles) {
                candles.removeAt(0)
            }
            effectivePrice = boundedTarget
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
