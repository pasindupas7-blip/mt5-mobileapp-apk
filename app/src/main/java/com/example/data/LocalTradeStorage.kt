package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.Candle
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores ONLY local chart UI display toggles and chart candlestick cache.
 * All user account data, balance, open trades, and trade history live exclusively in Cloud Firestore.
 */
class LocalTradeStorage(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        purgeLegacyTradingData()
    }

    /**
     * Permanently removes any legacy local account, balance, open-trade, or trade-history keys
     * left over from earlier versions so Firestore is the sole source of truth.
     */
    private fun purgeLegacyTradingData() {
        val allKeys = prefs.all.keys
        val legacyKeys = allKeys.filter { key ->
            key.startsWith("open_positions_json") ||
                key.startsWith("trade_history_json") ||
                key.startsWith("deleted_positions_ids") ||
                key.startsWith("deleted_history_ids") ||
                key == "account_info_json" ||
                key == "accounts_list_json_v1" ||
                key == "active_account_key_v1" ||
                key == "next_position_id" ||
                key == "seeded_default_history_v1"
        }
        if (legacyKeys.isNotEmpty()) {
            val editor = prefs.edit()
            legacyKeys.forEach { editor.remove(it) }
            editor.apply()
        }
    }

    fun saveCandles(symbol: String, timeframe: String, candles: List<Candle>) {
        if (candles.isEmpty()) return
        val recent = candles.takeLast(3000)
        val arr = JSONArray()
        for (c in recent) {
            if (c.timestamp <= 0L) continue
            val obj = JSONObject().apply {
                put("t", c.time)
                put("ts", c.timestamp)
                put("o", c.open)
                put("h", c.high)
                put("l", c.low)
                put("c", c.close)
            }
            arr.put(obj)
        }
        val key = "${KEY_CANDLES_PREFIX}${symbol.uppercase()}_${timeframe.uppercase()}"
        prefs.edit().putString(key, arr.toString()).apply()
    }

    fun loadCandles(symbol: String, timeframe: String): List<Candle> {
        val key = "${KEY_CANDLES_PREFIX}${symbol.uppercase()}_${timeframe.uppercase()}"
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = ArrayList<Candle>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val ts = obj.optLong("ts", 0L)
                val o = obj.optDouble("o", 0.0)
                val h = obj.optDouble("h", 0.0)
                val l = obj.optDouble("l", 0.0)
                val c = obj.optDouble("c", 0.0)
                if (ts > 0L && o > 0.0 && h > 0.0 && l > 0.0 && c > 0.0) {
                    list.add(
                        Candle(
                            time = obj.optString("t", ""),
                            timestamp = ts,
                            open = o,
                            high = h,
                            low = l,
                            close = c
                        )
                    )
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveChartHistoryMoveEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CHART_HISTORY_MOVE_ENABLED, enabled).apply()
    }

    fun loadChartHistoryMoveEnabled(): Boolean {
        return prefs.getBoolean(KEY_CHART_HISTORY_MOVE_ENABLED, true)
    }

    fun saveChartGridEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CHART_GRID_ENABLED, enabled).apply()
    }

    fun loadChartGridEnabled(): Boolean {
        return prefs.getBoolean(KEY_CHART_GRID_ENABLED, true)
    }

    fun saveChartSymbolDescriptionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CHART_SYMBOL_DESC_ENABLED, enabled).apply()
    }

    fun loadChartSymbolDescriptionEnabled(): Boolean {
        return prefs.getBoolean(KEY_CHART_SYMBOL_DESC_ENABLED, true)
    }

    companion object {
        private const val PREFS_NAME = "mt5_local_trade_storage"
        private const val KEY_CANDLES_PREFIX = "chart_candles_real_v8_"
        private const val KEY_CHART_HISTORY_MOVE_ENABLED = "chart_history_move_enabled"
        private const val KEY_CHART_GRID_ENABLED = "chart_grid_enabled"
        private const val KEY_CHART_SYMBOL_DESC_ENABLED = "chart_symbol_desc_enabled"
    }
}
