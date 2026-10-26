package com.example

import com.example.model.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testFastForex14DayHistoricalCandles() {
    val service = com.example.data.FastForexService.getInstance()
    val d1Candles = service.build14DayCandlesFromAnchors("XAUUSD", Timeframe.D1, 4165.24)
    assertTrue(d1Candles.size >= 14)
    assertEquals(4165.24, d1Candles.last().close, 0.001)

    val h1Candles = service.build14DayCandlesFromAnchors("EURUSD", Timeframe.H1, 1.12400)
    assertTrue(h1Candles.size >= 336)
    assertEquals(1.12400, h1Candles.last().close, 0.00001)
  }

  @Test
  fun testFastForexSymbolParsing() {
    val service = com.example.data.FastForexService.getInstance()
    val xauParsed = service.parseSymbol("XAUUSD")
    assertEquals(Pair("XAU", "USD"), xauParsed)

    val eurParsed = service.parseSymbol("EURUSD")
    assertEquals(Pair("EUR", "USD"), eurParsed)

    val audCadParsed = service.parseSymbol("AUDCAD")
    assertEquals(Pair("AUD", "CAD"), audCadParsed)

    val btcParsed = service.parseSymbol("BTCUSD")
    assertEquals(Pair("BTC", "USD"), btcParsed)

    assertEquals("XAUUSD", service.normalizeSymbol("XAU/USD"))
  }

  @Test
  fun testNoAutoOpenTradesAndUserTradeLifecycle() {
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
    val repo = com.example.data.TradingRepository(scope)

    // Verify 0 open trades on app startup
    assertTrue("No trades should be open automatically on startup", repo.positions.value.isEmpty())
    assertEquals(0.0, repo.account.value.floatingPnL, 0.001)
    assertEquals(0.0, repo.account.value.margin, 0.001)

    // User opens a BUY trade with SL and TP
    val opened = repo.executeOrder(symbol = "XAUUSD", isBuy = true, lots = 0.10, sl = 4150.00, tp = 4180.00)
    assertEquals(1, repo.positions.value.size)
    assertEquals(opened.id, repo.positions.value.first().id)
    assertEquals("XAUUSD", repo.positions.value.first().symbol)
    assertEquals(true, repo.positions.value.first().isBuy)
    assertEquals(4150.00, repo.positions.value.first().sl ?: 0.0, 0.001)
    assertEquals(4180.00, repo.positions.value.first().tp ?: 0.0, 0.001)

    // User closes the trade
    repo.closePosition(opened.id)
    assertTrue("Trade list should be empty after closing user trade", repo.positions.value.isEmpty())
    kotlinx.coroutines.cancel(scope.coroutineContext)
  }

  @Test
  fun testNaturalContinuousCandleFormation() {
    val service = com.example.data.FastForexService.getInstance()
    val aggregator = com.example.data.CandleAggregator(Timeframe.M1)
    val initial = service.build14DayCandlesFromAnchors("XAUUSD", Timeframe.M1, 4165.24)
    aggregator.seedHistory(initial)

    val naturalRange = service.getNaturalM1Range("XAUUSD")
    val maxExcursion = naturalRange * 1.35
    val startTs = initial.last().timestamp
    var currentPrice = initial.last().close

    // Simulate 300 ticks spanning 3 minutes (every 600ms)
    for (i in 1..300) {
      val stepDelta = if (i % 3 == 0) -0.04 else 0.03
      currentPrice += stepDelta
      val tickTs = startTs + (i * 600L)
      val effective = aggregator.onTick(currentPrice, tickTs, maxExcursion)
      assertTrue("Price must remain valid", effective > 0.0)
    }

    val finalCandles = aggregator.getCandles()
    assertTrue("New minute candles should have rolled over and been added", finalCandles.size > initial.size)
    val latest = finalCandles.last()
    assertTrue("Candle high must be >= max(open, close)", latest.high >= kotlin.math.max(latest.open, latest.close))
    assertTrue("Candle low must be <= min(open, close)", latest.low <= kotlin.math.min(latest.open, latest.close))
    assertTrue("Candle range must stay within natural proportion", (latest.high - latest.low) <= maxExcursion * 2.8)
  }
}
