package com.example

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("MetaTrader 5", appName)
  }

  @Test
  fun `launch MainActivity scenario`() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        assertNotNull(activity)
      }
    }
  }

  @Test
  fun `user opened and closed trades persist across sessions`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.data.LocalTradeStorage(context).clearAll()

    val scope1 = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
    val repo1 = com.example.data.TradingRepository(scope1)
    repo1.initializeStorage(context)

    // Initially empty open positions
    assertEquals(0, repo1.positions.value.size)

    // User opens 2 trades and closes 1 trade
    val pos1 = repo1.executeOrder("XAUUSD", isBuy = true, lots = 0.10, sl = 4150.0, tp = 4180.0)
    val pos2 = repo1.executeOrder("EURUSD", isBuy = false, lots = 0.20)
    repo1.closePosition(pos1.id)

    assertEquals(1, repo1.positions.value.size)
    assertTrue(repo1.history.value.any { it.id == pos1.id })

    // Simulate closing and reopening the app with a fresh TradingRepository
    val scope2 = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
    val repo2 = com.example.data.TradingRepository(scope2)
    repo2.initializeStorage(context)

    assertEquals(1, repo2.positions.value.size)
    assertEquals(pos2.id, repo2.positions.value.first().id)
    assertTrue(repo2.history.value.any { it.id == pos1.id && it.symbol == "XAUUSD" })
    scope1.cancel()
    scope2.cancel()
  }
}
