package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GoogleAuthHelper
import com.example.data.TradingRepository
import com.example.model.AccountInfo
import com.example.model.Candle
import com.example.model.HistoryTrade
import com.example.model.MarketConnectionStatus
import com.example.model.MinuteCandleLookupResult
import com.example.model.SymbolQuote
import com.example.model.Timeframe
import com.example.model.TpSlChartCheckResult
import com.example.model.TradeHitOutcome
import com.example.model.TradePosition
import com.example.ui.components.MT5Tab
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class ActiveDialog {
    object None : ActiveDialog()
    data class NewOrder(val symbol: String) : ActiveDialog()
    data class ClosePosition(val position: TradePosition) : ActiveDialog()
    data class TopUp(val currentBalance: Double) : ActiveDialog()
}

data class ChartViewportState(
    val panOffsetPx: Float = 0f,
    val zoomScale: Float = 1.0f,
    val priceAxisZoom: Float = 1.0f,
    val verticalPanPriceOffset: Double = 0.0
)

class TradingViewModel @JvmOverloads constructor(
    repositoryParam: TradingRepository? = null
) : ViewModel() {

    private val repository: TradingRepository = repositoryParam ?: TradingRepository(viewModelScope)

    val quotes: StateFlow<List<SymbolQuote>> = repository.quotes
    val positions: StateFlow<List<TradePosition>> = repository.positions
    val history: StateFlow<List<HistoryTrade>> = repository.history
    val account: StateFlow<AccountInfo> = repository.account
    val accounts: StateFlow<List<AccountInfo>> = repository.accounts
    val candles: StateFlow<List<Candle>> = repository.selectedPairCandles
    val marketConnectionStatus: StateFlow<MarketConnectionStatus> = repository.marketConnectionStatus
    val fastForexLastSync: StateFlow<Long> = repository.fastForexService.lastSyncTimestamp
    val fastForexError: StateFlow<String?> = repository.fastForexService.lastErrorMessage
    val isCloudSynced: StateFlow<Boolean> = repository.isCloudSynced
    val currentUserEmail: StateFlow<String?> = repository.currentUserEmail
    val chartHistoryMoveEnabled: StateFlow<Boolean> = repository.chartHistoryMoveEnabled

    private val _currentTab = MutableStateFlow(MT5Tab.QUOTES)
    val currentTab: StateFlow<MT5Tab> = _currentTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedSymbol = MutableStateFlow("XAUUSD")
    val selectedSymbol: StateFlow<String> = _selectedSymbol.asStateFlow()

    private val _selectedTimeframe = MutableStateFlow(Timeframe.M5)
    val selectedTimeframe: StateFlow<Timeframe> = _selectedTimeframe.asStateFlow()

    private val _quickTradeLot = MutableStateFlow(0.01)
    val quickTradeLot: StateFlow<Double> = _quickTradeLot.asStateFlow()

    private val _showQuickTradeBar = MutableStateFlow(true)
    val showQuickTradeBar: StateFlow<Boolean> = _showQuickTradeBar.asStateFlow()

    private val _fit14DaysMode = MutableStateFlow(false)
    val fit14DaysMode: StateFlow<Boolean> = _fit14DaysMode.asStateFlow()

    private val chartViewportMap = java.util.concurrent.ConcurrentHashMap<String, ChartViewportState>()

    private val _showMA = MutableStateFlow(false)
    val showMA: StateFlow<Boolean> = _showMA.asStateFlow()

    private val _showCrosshair = MutableStateFlow(false)
    val showCrosshair: StateFlow<Boolean> = _showCrosshair.asStateFlow()

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _isDrawerOpen = MutableStateFlow(false)
    val isDrawerOpen: StateFlow<Boolean> = _isDrawerOpen.asStateFlow()

    private val _showSettingsPage = MutableStateFlow(false)
    val showSettingsPage: StateFlow<Boolean> = _showSettingsPage.asStateFlow()

    private val _showAccountsPage = MutableStateFlow(false)
    val showAccountsPage: StateFlow<Boolean> = _showAccountsPage.asStateFlow()

    private val _showAboutPage = MutableStateFlow(false)
    val showAboutPage: StateFlow<Boolean> = _showAboutPage.asStateFlow()

    private val _activeDialog = MutableStateFlow<ActiveDialog>(ActiveDialog.None)
    val activeDialog: StateFlow<ActiveDialog> = _activeDialog.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    init {
        repository.setSelectedSymbol("XAUUSD")
        repository.setSelectedTimeframe(Timeframe.M5)
    }

    val filteredQuotes: StateFlow<List<SymbolQuote>> = combine(quotes, _searchQuery) { list, query ->
        if (query.isBlank()) {
            list
        } else {
            list.filter {
                it.symbol.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, quotes.value)

    val selectedQuote: StateFlow<SymbolQuote?> = combine(quotes, _selectedSymbol) { list, symbol ->
        list.find { it.symbol == symbol } ?: list.firstOrNull()
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        quotes.value.find { it.symbol == "XAUUSD" } ?: quotes.value.firstOrNull()
    )

    fun initializeStorage(context: Context) {
        repository.initializeStorage(context)
    }

    fun toggleChartHistoryMove() {
        val next = !chartHistoryMoveEnabled.value
        repository.setChartHistoryMoveEnabled(next)
        showNotification(
            if (next) "Chart Trade History Move: ON"
            else "Chart Trade History Move: OFF"
        )
    }

    fun setChartHistoryMoveEnabled(enabled: Boolean) {
        repository.setChartHistoryMoveEnabled(enabled)
        showNotification(
            if (enabled) "Chart Trade History Move: ON"
            else "Chart Trade History Move: OFF"
        )
    }

    fun toggleQuickTradeBar() {
        _showQuickTradeBar.value = !_showQuickTradeBar.value
    }

    fun setFit14DaysMode(enabled: Boolean) {
        _fit14DaysMode.value = enabled
    }

    fun getChartViewportState(symbol: String, timeframeLabel: String): ChartViewportState {
        val key = "${symbol.uppercase(Locale.US)}_${timeframeLabel.uppercase(Locale.US)}"
        return chartViewportMap[key] ?: ChartViewportState()
    }

    fun updateChartViewportState(
        symbol: String,
        timeframeLabel: String,
        state: ChartViewportState
    ) {
        val key = "${symbol.uppercase(Locale.US)}_${timeframeLabel.uppercase(Locale.US)}"
        chartViewportMap[key] = state
    }

    fun signInWithGoogle(activityContext: Context) {
        viewModelScope.launch {
            val result = GoogleAuthHelper.signInWithGoogleButton(activityContext)
            result.onSuccess { user ->
                showNotification("Cloud Server Connected: ${user.email ?: "Google Account"}")
            }.onFailure { err ->
                showNotification("Sign-In: ${err.message ?: "Unable to connect Google Account"}")
            }
        }
    }

    fun promptAutoSignInIfNeeded(activityContext: Context) {
        if (isCloudSynced.value) return
        viewModelScope.launch {
            val result = GoogleAuthHelper.promptSignInIfEligible(activityContext)
            result.onSuccess { user ->
                showNotification("Cloud Trade History Restored (${user.email ?: "Account"})")
            }
        }
    }

    fun signOutCloud(context: Context) {
        viewModelScope.launch {
            GoogleAuthHelper.signOut(context)
            showNotification("Signed out of Cloud Server (Local Storage Active)")
        }
    }

    fun syncCloudNow() {
        repository.syncNowWithServer()
        showNotification("Syncing trades & history with Cloud Server...")
    }

    fun selectTab(tab: MT5Tab) {
        _showSettingsPage.value = false
        _showAccountsPage.value = false
        _showAboutPage.value = false
        _isDrawerOpen.value = false
        _currentTab.value = tab
    }

    fun openDrawer() {
        _isDrawerOpen.value = true
    }

    fun closeDrawer() {
        _isDrawerOpen.value = false
    }

    fun openAccountsPage() {
        _isDrawerOpen.value = false
        _showSettingsPage.value = false
        _showAboutPage.value = false
        _showAccountsPage.value = true
    }

    fun closeAccountsPage() {
        _showAccountsPage.value = false
    }

    fun openSettingsPage() {
        _isDrawerOpen.value = false
        _showAccountsPage.value = false
        _showAboutPage.value = false
        _showSettingsPage.value = true
    }

    fun closeSettingsPage() {
        _showSettingsPage.value = false
    }

    fun openAboutPage() {
        _isDrawerOpen.value = false
        _showAccountsPage.value = false
        _showSettingsPage.value = false
        _showAboutPage.value = true
    }

    fun closeAboutPage() {
        _showAboutPage.value = false
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun selectSymbol(symbol: String, navigateToChart: Boolean = false) {
        val cleanSym = symbol.trim().uppercase(Locale.US)
        if (_selectedSymbol.value != cleanSym) {
            _selectedSymbol.value = cleanSym
            repository.setSelectedSymbol(cleanSym)
        }
        if (navigateToChart) {
            _showSettingsPage.value = false
            _showAccountsPage.value = false
            _showAboutPage.value = false
            _isDrawerOpen.value = false
            _currentTab.value = MT5Tab.CHART
        }
    }

    fun selectTimeframe(tf: Timeframe) {
        if (_selectedTimeframe.value != tf) {
            _selectedTimeframe.value = tf
            repository.setSelectedTimeframe(tf)
        }
    }

    fun setQuickTradeLot(lot: Double) {
        _quickTradeLot.value = String.format(Locale.US, "%.2f", lot).toDouble()
    }

    fun toggleMA() {
        _showMA.value = !_showMA.value
        showNotification(if (_showMA.value) "Indicator: Moving Average (20) Enabled" else "Indicator Disabled")
    }

    fun toggleCrosshair() {
        _showCrosshair.value = !_showCrosshair.value
    }

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
    }

    fun refreshMarketData() {
        repository.forceRefreshMarketData()
        showNotification("Fetching live rates from FastForex.io...")
    }

    fun openNewOrderDialog(symbol: String? = null) {
        val sym = symbol ?: _selectedSymbol.value
        _activeDialog.value = ActiveDialog.NewOrder(sym)
    }

    fun openClosePositionDialog(position: TradePosition) {
        _activeDialog.value = ActiveDialog.ClosePosition(position)
    }

    fun openTopUpDialog() {
        _activeDialog.value = ActiveDialog.TopUp(account.value.balance)
    }

    fun dismissDialog() {
        _activeDialog.value = ActiveDialog.None
    }

    fun executeQuickTrade(isBuy: Boolean, lot: Double) {
        val symbol = _selectedSymbol.value
        val quote = quotes.value.find { it.symbol == symbol } ?: return
        repository.executeOrder(symbol, isBuy, lot)
        val priceStr = String.format(Locale.US, "%.${quote.digits}f", if (isBuy) quote.ask else quote.bid)
        showNotification("Done: ${if (isBuy) "BUY" else "SELL"} ${String.format(Locale.US, "%.2f", lot)} $symbol at $priceStr")
    }

    fun executeDialogOrder(symbol: String, isBuy: Boolean, lots: Double, sl: Double?, tp: Double?) {
        val quote = quotes.value.find { it.symbol == symbol } ?: return
        repository.executeOrder(symbol, isBuy, lots, sl, tp)
        val priceStr = String.format(Locale.US, "%.${quote.digits}f", if (isBuy) quote.ask else quote.bid)
        dismissDialog()
        showNotification("Done: ${if (isBuy) "BUY" else "SELL"} ${String.format(Locale.US, "%.2f", lots)} $symbol at $priceStr")
    }

    fun confirmCloseFull(positionId: Long) {
        repository.closePosition(positionId)
        dismissDialog()
        showNotification("Position #$positionId Closed & Saved to History")
    }

    fun confirmClosePartial(positionId: Long, lots: Double) {
        repository.closePositionPartial(positionId, lots)
        dismissDialog()
        showNotification("Position #$positionId Partially Closed ($lots lots)")
    }

    fun generateUniqueTicketId(): String {
        return repository.generateUniqueTicketId()
    }

    fun lookupMinuteChartCandle(
        symbol: String,
        dateTimeStr: String,
        isOpen: Boolean
    ): MinuteCandleLookupResult? {
        return repository.lookupMinuteChartCandle(symbol, dateTimeStr, isOpen)
    }

    fun getRecentSymbolM1Candles(symbol: String, limit: Int = 24): List<Candle> {
        return repository.getRecentSymbolM1Candles(symbol, limit)
    }

    fun resolveTradeHitOutcome(trade: HistoryTrade): TradeHitOutcome {
        return repository.resolveTradeHitOutcome(trade)
    }

    fun checkTpSlHitFromChartData(
        symbol: String,
        isBuy: Boolean,
        openPrice: Double,
        closePrice: Double,
        openTime: String,
        closeTime: String,
        sl: Double?,
        tp: Double?,
        strictWithinTradeWindow: Boolean = false
    ): TpSlChartCheckResult {
        return repository.checkTpSlHitFromChartData(
            symbol = symbol,
            isBuy = isBuy,
            openPrice = openPrice,
            closePrice = closePrice,
            openTime = openTime,
            closeTime = closeTime,
            sl = sl,
            tp = tp,
            strictWithinTradeWindow = strictWithinTradeWindow
        )
    }

    fun calculateHistoricalTradeProfit(
        symbol: String,
        isBuy: Boolean,
        lots: Double,
        openPrice: Double,
        closePrice: Double
    ): Double {
        return repository.calculateHistoricalTradeProfit(symbol, isBuy, lots, openPrice, closePrice)
    }

    fun saveOrUpdateRunningPosition(
        existingId: Long? = null,
        symbol: String,
        isBuy: Boolean,
        lots: Double,
        openPrice: Double,
        openTime: String,
        sl: Double? = null,
        tp: Double? = null,
        customTicketId: String? = null
    ) {
        val saved = repository.saveOrUpdateRunningPosition(
            existingId = existingId,
            symbol = symbol,
            isBuy = isBuy,
            lots = lots,
            openPrice = openPrice,
            openTime = openTime,
            sl = sl,
            tp = tp,
            customTicketId = customTicketId
        )
        val ticketLabel = "#${if (saved.id >= 100000000L) saved.id else 385000000L + saved.id}"
        showNotification("Running Trade $ticketLabel (${saved.symbol}) active & synced to Cloud")
    }

    fun deleteRunningPosition(id: Long) {
        repository.deleteRunningPosition(id)
        showNotification("Running trade removed & synced to Cloud")
    }

    fun saveOrUpdateHistoryTrade(
        existingId: Long? = null,
        symbol: String,
        isBuy: Boolean,
        lots: Double,
        openPrice: Double,
        closePrice: Double,
        openTime: String,
        closeTime: String,
        sl: Double? = null,
        tp: Double? = null,
        customProfit: Double? = null,
        customTicketId: String? = null,
        hitOutcome: TradeHitOutcome? = null
    ) {
        val saved = repository.saveOrUpdateHistoryTrade(
            existingId = existingId,
            symbol = symbol,
            isBuy = isBuy,
            lots = lots,
            openPrice = openPrice,
            closePrice = closePrice,
            openTime = openTime,
            closeTime = closeTime,
            sl = sl,
            tp = tp,
            customProfit = customProfit,
            customTicketId = customTicketId,
            hitOutcome = hitOutcome
        )
        showNotification("Trade ${saved.ticketId} (${saved.symbol}) saved & synced to Cloud")
    }

    fun saveOrUpdateWithdrawal(
        existingId: Long? = null,
        amount: Double,
        closeTime: String,
        comment: String = "Withdrawal",
        customTicketId: String? = null
    ) {
        val saved = repository.saveOrUpdateWithdrawal(
            existingId = existingId,
            amount = amount,
            closeTime = closeTime,
            comment = comment,
            customTicketId = customTicketId
        )
        showNotification("Withdrawal -$${String.format(Locale.US, "%.2f", kotlin.math.abs(saved.profit))} saved & synced")
    }

    fun deleteHistoryItem(id: Long) {
        repository.deleteHistoryItem(id)
        showNotification("History record removed & synced to Cloud")
    }

    fun setDepositAmount(newDeposit: Double) {
        repository.setDepositAmount(newDeposit)
        showNotification("Deposit updated to $${String.format(Locale.US, "%.2f", newDeposit)} USD")
    }

    fun updateAccountDetails(
        accountName: String,
        brokerName: String? = null,
        accountId: String? = null,
        serverName: String? = null,
        isReal: Boolean? = null,
        newBalance: Double? = null,
        logoType: String? = null
    ) {
        val current = repository.account.value
        repository.updateAccountDetails(
            accountName = accountName,
            brokerName = brokerName ?: current.brokerName,
            accountId = accountId ?: current.accountId,
            serverName = serverName ?: current.serverName,
            isReal = isReal,
            newBalance = newBalance,
            logoType = logoType
        )
        showNotification("Account updated: $accountName")
    }

    fun addAccount(
        accountName: String,
        brokerName: String,
        accountId: String,
        serverName: String,
        isReal: Boolean,
        initialBalance: Double,
        logoType: String = "AUTO",
        switchToNew: Boolean = true
    ) {
        val created = repository.addAccount(
            accountName = accountName,
            brokerName = brokerName,
            accountId = accountId,
            serverName = serverName,
            isReal = isReal,
            initialBalance = initialBalance,
            logoType = logoType,
            switchToNew = switchToNew
        )
        showNotification("Account added: ${created.accountName}")
    }

    fun switchAccount(accountKey: String) {
        repository.switchAccount(accountKey)
        val active = repository.account.value
        showNotification("Switched to ${active.accountName}")
    }

    fun updateAccountByKey(
        targetKey: String,
        accountName: String,
        brokerName: String,
        accountId: String,
        serverName: String,
        isReal: Boolean? = null,
        newBalance: Double? = null,
        logoType: String? = null
    ) {
        repository.updateAccountByKey(
            targetKey = targetKey,
            accountName = accountName,
            brokerName = brokerName,
            accountId = accountId,
            serverName = serverName,
            isReal = isReal,
            newBalance = newBalance,
            logoType = logoType
        )
        showNotification("Account saved: $accountName")
    }

    fun deleteAccount(targetKey: String) {
        val targetName = repository.accounts.value.find { it.key == targetKey }?.accountName ?: "Account"
        val deleted = repository.deleteAccount(targetKey)
        if (deleted) {
            showNotification("Deleted account: $targetName")
        }
    }

    fun topUpBalance(amount: Double) {
        repository.topUpDemo(amount)
        dismissDialog()
        showNotification("Account credited +$${amount.toInt()} USD")
    }

    fun resetDemo() {
        repository.resetDemoAccount()
        showNotification("Demo Account Reset to Default")
    }

    private fun showNotification(msg: String) {
        viewModelScope.launch {
            _toastMessage.value = msg
            delay(2500)
            if (_toastMessage.value == msg) {
                _toastMessage.value = null
            }
        }
    }
}
