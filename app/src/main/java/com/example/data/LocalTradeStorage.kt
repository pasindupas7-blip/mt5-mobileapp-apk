package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.AccountInfo
import com.example.model.HistoryTrade
import com.example.model.TradeHitOutcome
import com.example.model.TradePosition
import org.json.JSONArray
import org.json.JSONObject

class LocalTradeStorage(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasSeededDefaultHistory(): Boolean {
        return prefs.getBoolean(KEY_SEEDED_DEFAULT_HISTORY, false)
    }

    fun setSeededDefaultHistory(seeded: Boolean) {
        prefs.edit().putBoolean(KEY_SEEDED_DEFAULT_HISTORY, seeded).apply()
    }

    private fun encodePositionsJson(positions: List<TradePosition>): String {
        val arr = JSONArray()
        for (p in positions) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("symbol", p.symbol)
                put("isBuy", p.isBuy)
                put("lots", p.lots)
                put("openPrice", p.openPrice)
                put("currentPrice", p.currentPrice)
                if (p.sl != null) put("sl", p.sl)
                if (p.tp != null) put("tp", p.tp)
                put("profit", p.profit)
                put("openTime", p.openTime)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodePositionsJson(raw: String?): List<TradePosition> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<TradePosition>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val sl = if (obj.has("sl") && !obj.isNull("sl")) obj.getDouble("sl") else null
                val tp = if (obj.has("tp") && !obj.isNull("tp")) obj.getDouble("tp") else null
                list.add(
                    TradePosition(
                        id = obj.getLong("id"),
                        symbol = obj.getString("symbol"),
                        isBuy = obj.getBoolean("isBuy"),
                        lots = obj.getDouble("lots"),
                        openPrice = obj.getDouble("openPrice"),
                        currentPrice = obj.optDouble("currentPrice", obj.getDouble("openPrice")),
                        sl = sl,
                        tp = tp,
                        profit = obj.optDouble("profit", 0.0),
                        openTime = obj.optString("openTime", "")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun savePositions(positions: List<TradePosition>) {
        prefs.edit().putString(KEY_POSITIONS, encodePositionsJson(positions)).apply()
    }

    fun loadPositions(): List<TradePosition> {
        return decodePositionsJson(prefs.getString(KEY_POSITIONS, null))
    }

    fun savePositionsForAccount(accountKey: String, positions: List<TradePosition>) {
        val safeKey = accountKey.ifBlank { "main" }
        val json = encodePositionsJson(positions)
        val editor = prefs.edit().putString("${KEY_POSITIONS}_$safeKey", json)
        if (safeKey == "main") {
            editor.putString(KEY_POSITIONS, json)
        }
        editor.apply()
    }

    fun loadPositionsForAccount(accountKey: String): List<TradePosition> {
        val safeKey = accountKey.ifBlank { "main" }
        val raw = prefs.getString("${KEY_POSITIONS}_$safeKey", null)
        if (raw != null) {
            return decodePositionsJson(raw)
        }
        return if (safeKey == "main") loadPositions() else emptyList()
    }

    private fun encodeHistoryJson(history: List<HistoryTrade>): String {
        val arr = JSONArray()
        for (h in history) {
            val obj = JSONObject().apply {
                put("id", h.id)
                put("symbol", h.symbol)
                put("isBuy", h.isBuy)
                put("lots", h.lots)
                put("openPrice", h.openPrice)
                put("closePrice", h.closePrice)
                put("profit", h.profit)
                put("openTime", h.openTime)
                put("closeTime", h.closeTime)
                put("commission", h.commission)
                put("swap", h.swap)
                if (h.sl != null) put("sl", h.sl)
                if (h.tp != null) put("tp", h.tp)
                put("ticketId", h.ticketId)
                put("isWithdrawal", h.isWithdrawal)
                put("comment", h.comment)
                if (h.hitOutcome != null) put("hitOutcome", h.hitOutcome.name)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodeHistoryJson(raw: String?): List<HistoryTrade> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<HistoryTrade>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getLong("id")
                val sl = if (obj.has("sl") && !obj.isNull("sl")) obj.getDouble("sl") else null
                val tp = if (obj.has("tp") && !obj.isNull("tp")) obj.getDouble("tp") else null
                val rawHitOutcome = if (obj.has("hitOutcome") && !obj.isNull("hitOutcome")) {
                    obj.optString("hitOutcome", "")
                } else ""
                val hitOutcome = rawHitOutcome.takeIf { it.isNotBlank() }?.let {
                    runCatching { TradeHitOutcome.valueOf(it) }.getOrNull()
                }
                list.add(
                    HistoryTrade(
                        id = id,
                        symbol = obj.getString("symbol"),
                        isBuy = obj.getBoolean("isBuy"),
                        lots = obj.getDouble("lots"),
                        openPrice = obj.getDouble("openPrice"),
                        closePrice = obj.getDouble("closePrice"),
                        profit = obj.getDouble("profit"),
                        openTime = obj.optString("openTime", ""),
                        closeTime = obj.optString("closeTime", ""),
                        commission = obj.optDouble("commission", 0.0),
                        swap = obj.optDouble("swap", 0.0),
                        sl = sl,
                        tp = tp,
                        ticketId = obj.optString("ticketId", "#${385000000L + id}"),
                        isWithdrawal = obj.optBoolean("isWithdrawal", false),
                        comment = obj.optString("comment", ""),
                        hitOutcome = hitOutcome
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveHistory(history: List<HistoryTrade>) {
        prefs.edit().putString(KEY_HISTORY, encodeHistoryJson(history)).apply()
    }

    fun loadHistory(): List<HistoryTrade> {
        return decodeHistoryJson(prefs.getString(KEY_HISTORY, null))
    }

    fun saveHistoryForAccount(accountKey: String, history: List<HistoryTrade>) {
        val safeKey = accountKey.ifBlank { "main" }
        val json = encodeHistoryJson(history)
        val editor = prefs.edit().putString("${KEY_HISTORY}_$safeKey", json)
        if (safeKey == "main") {
            editor.putString(KEY_HISTORY, json)
        }
        editor.apply()
    }

    fun loadHistoryForAccount(accountKey: String): List<HistoryTrade> {
        val safeKey = accountKey.ifBlank { "main" }
        val raw = prefs.getString("${KEY_HISTORY}_$safeKey", null)
        if (raw != null) {
            return decodeHistoryJson(raw)
        }
        return if (safeKey == "main") loadHistory() else emptyList()
    }

    private fun accountToJsonObject(account: AccountInfo): JSONObject {
        return JSONObject().apply {
            put("key", account.key.ifBlank { "main" })
            put("balance", account.balance)
            put("equity", account.equity)
            put("margin", account.margin)
            put("freeMargin", account.freeMargin)
            put("marginLevel", account.marginLevel)
            put("floatingPnL", account.floatingPnL)
            put("accountName", account.accountName)
            put("brokerName", account.brokerName)
            put("accountId", account.accountId)
            put("serverName", account.serverName)
            put("isReal", account.isReal)
            put("deposit", account.deposit)
            put("realizedProfit", account.realizedProfit)
            put("totalWithdrawal", account.totalWithdrawal)
            put("totalSwap", account.totalSwap)
            put("totalCommission", account.totalCommission)
            put("logoType", account.logoType.ifBlank { "AUTO" })
        }
    }

    private fun jsonObjectToAccount(obj: JSONObject, isLegacySingleAccount: Boolean = false): AccountInfo {
        val defaults = AccountInfo()
        val key = obj.optString("key", "main").ifBlank { "main" }
        val savedName = obj.optString("accountName", defaults.accountName)
        val savedBroker = obj.optString("brokerName", defaults.brokerName)
        val savedId = obj.optString("accountId", defaults.accountId)
        val savedServer = obj.optString("serverName", defaults.serverName)
        val savedLogoType = obj.optString("logoType", "AUTO").ifBlank { "AUTO" }

        val isLegacyDefault = isLegacySingleAccount && key == "main" && (
            savedName == "REAL 21" ||
                savedName == "Pasindu Jeewantha Pelan..."
            )
        val rawDeposit = obj.optDouble("deposit", if (key == "main") defaults.deposit else 0.0)
        val effectiveDeposit = if (isLegacySingleAccount && rawDeposit == 674.08) 500.00 else rawDeposit.coerceAtLeast(0.0)
        val rawProfit = obj.optDouble("realizedProfit", if (key == "main") defaults.realizedProfit else 0.0)
        val effectiveProfit = if (isLegacySingleAccount && rawProfit == -139.83) defaults.realizedProfit else rawProfit
        val effectiveWithdrawal = obj.optDouble("totalWithdrawal", 0.00)
        val effectiveBalance = kotlin.math.round((effectiveDeposit + effectiveProfit - effectiveWithdrawal) * 100.0) / 100.0

        return AccountInfo(
            balance = effectiveBalance,
            equity = obj.optDouble("equity", effectiveBalance),
            margin = obj.optDouble("margin", 0.00),
            freeMargin = obj.optDouble("freeMargin", effectiveBalance),
            marginLevel = obj.optDouble("marginLevel", 0.00),
            floatingPnL = obj.optDouble("floatingPnL", 0.00),
            accountName = if (isLegacyDefault) defaults.accountName else savedName,
            brokerName = if (isLegacyDefault) defaults.brokerName else savedBroker,
            accountId = if (isLegacyDefault) defaults.accountId else savedId,
            serverName = if (isLegacyDefault) defaults.serverName else savedServer,
            isReal = obj.optBoolean("isReal", defaults.isReal),
            deposit = effectiveDeposit,
            realizedProfit = effectiveProfit,
            totalWithdrawal = effectiveWithdrawal,
            totalSwap = 0.00,
            totalCommission = 0.00,
            key = key,
            logoType = savedLogoType
        )
    }

    fun saveAccount(account: AccountInfo) {
        prefs.edit().putString(KEY_ACCOUNT, accountToJsonObject(account).toString()).apply()
    }

    fun loadAccount(): AccountInfo? {
        val raw = prefs.getString(KEY_ACCOUNT, null) ?: return null
        return try {
            val obj = JSONObject(raw)
            jsonObjectToAccount(obj, isLegacySingleAccount = true)
        } catch (e: Exception) {
            null
        }
    }

    fun saveAccountsList(accounts: List<AccountInfo>) {
        val arr = JSONArray()
        for (acc in accounts) {
            arr.put(accountToJsonObject(acc))
        }
        prefs.edit().putString(KEY_ACCOUNTS_LIST, arr.toString()).apply()
    }

    fun loadAccountsList(): List<AccountInfo> {
        val raw = prefs.getString(KEY_ACCOUNTS_LIST, null)
        if (!raw.isNullOrBlank()) {
            try {
                val arr = JSONArray(raw)
                val list = mutableListOf<AccountInfo>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(jsonObjectToAccount(obj, isLegacySingleAccount = false))
                }
                if (list.isNotEmpty()) {
                    return list
                }
            } catch (ignored: Exception) {
            }
        }
        val legacyMain = loadAccount()
        return if (legacyMain != null) listOf(legacyMain) else emptyList()
    }

    fun saveActiveAccountKey(accountKey: String) {
        prefs.edit().putString(KEY_ACTIVE_ACCOUNT_KEY, accountKey.ifBlank { "main" }).apply()
    }

    fun loadActiveAccountKey(): String {
        return prefs.getString(KEY_ACTIVE_ACCOUNT_KEY, "main")?.ifBlank { "main" } ?: "main"
    }

    fun deleteAccountStorage(accountKey: String) {
        val safeKey = accountKey.ifBlank { "main" }
        prefs.edit()
            .remove("${KEY_POSITIONS}_$safeKey")
            .remove("${KEY_HISTORY}_$safeKey")
            .apply()
    }

    fun saveNextPositionId(nextId: Long) {
        prefs.edit().putLong(KEY_NEXT_ID, nextId).apply()
    }

    fun loadNextPositionId(): Long {
        return prefs.getLong(KEY_NEXT_ID, 1001L)
    }

    fun saveCandles(symbol: String, timeframe: String, candles: List<com.example.model.Candle>) {
        if (candles.isEmpty()) return
        val recent = candles.takeLast(600)
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

    fun loadCandles(symbol: String, timeframe: String): List<com.example.model.Candle> {
        val key = "${KEY_CANDLES_PREFIX}${symbol.uppercase()}_${timeframe.uppercase()}"
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = ArrayList<com.example.model.Candle>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val ts = obj.optLong("ts", 0L)
                val o = obj.optDouble("o", 0.0)
                val h = obj.optDouble("h", 0.0)
                val l = obj.optDouble("l", 0.0)
                val c = obj.optDouble("c", 0.0)
                if (ts > 0L && o > 0.0 && h > 0.0 && l > 0.0 && c > 0.0) {
                    list.add(
                        com.example.model.Candle(
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
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveChartHistoryMoveEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CHART_HISTORY_MOVE_ENABLED, enabled).apply()
    }

    fun loadChartHistoryMoveEnabled(): Boolean {
        return prefs.getBoolean(KEY_CHART_HISTORY_MOVE_ENABLED, true)
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "mt5_local_trade_storage"
        private const val KEY_POSITIONS = "open_positions_json"
        private const val KEY_HISTORY = "trade_history_json"
        private const val KEY_ACCOUNT = "account_info_json"
        private const val KEY_ACCOUNTS_LIST = "accounts_list_json_v1"
        private const val KEY_ACTIVE_ACCOUNT_KEY = "active_account_key_v1"
        private const val KEY_NEXT_ID = "next_position_id"
        private const val KEY_SEEDED_DEFAULT_HISTORY = "seeded_default_history_v1"
        private const val KEY_CANDLES_PREFIX = "chart_candles_real_v6_"
        private const val KEY_CHART_HISTORY_MOVE_ENABLED = "chart_history_move_enabled"
    }
}
