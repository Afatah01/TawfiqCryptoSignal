package com.tawfiq.cryptosignal.ui
import android.app.Application
import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.tawfiq.cryptosignal.*
import com.tawfiq.cryptosignal.data.*
import com.tawfiq.cryptosignal.util.Notify
import okhttp3.WebSocket

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()
    private val prefs = app.getSharedPreferences("tawfiq_prefs", 0)
    private val repo = Repository(dao, prefs.getString("backend_url", BuildConfig.DEFAULT_BACKEND_URL) ?: BuildConfig.DEFAULT_BACKEND_URL)
    val holdings: StateFlow<List<Holding>> = dao.holdingsFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val snapshots: StateFlow<List<Snapshot>> = dao.snapshotsFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val rules: StateFlow<List<AlertRule>> = dao.rulesFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history: StateFlow<List<AlertHistory>> = dao.historyFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _state = MutableStateFlow(PortfolioState(emptyList(), loading = true)); val state: StateFlow<PortfolioState> = _state
    private val _market = MutableStateFlow<List<GeckoMarket>>(emptyList()); val market: StateFlow<List<GeckoMarket>> = _market
    private val _marketErr = MutableStateFlow<String?>(null); val marketErr: StateFlow<String?> = _marketErr
    private var ws: WebSocket? = null
    private var wsJob: Job? = null
    private var lastSnapshotAt = 0L

    init { viewModelScope.launch { AppDb.seedIfEmpty(dao); refresh() } }

    fun refresh() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        val hs = holdings.value.ifEmpty { daoHoldings() }
        val (quotes, err) = repo.fetchPortfolioQuotes(hs)
        if (quotes.isEmpty()) {
            _state.value = PortfolioState(hs.map { AssetState(it, null) }, _state.value.lastUpdate, error = err ?: "Could not load live prices. Showing last known values.", loading = false)
            return@launch
        }
        val st = PortfolioState(hs.map { AssetState(it, quotes[it.symbol]) }, System.currentTimeMillis(), quotes.values.firstOrNull()?.source ?: "", loading = false)
        _state.value = st
        if (System.currentTimeMillis() - lastSnapshotAt > 300_000) {
            lastSnapshotAt = System.currentTimeMillis()
            dao.addSnapshot(Snapshot(ts = System.currentTimeMillis(), totalValue = st.total, cryptoValue = st.cryptoValue, pnl = st.pnl,
                btc = st.assets.firstOrNull { it.holding.symbol == "BTC" }?.currentValue ?: 0.0,
                bnb = st.assets.firstOrNull { it.holding.symbol == "BNB" }?.currentValue ?: 0.0,
                xrp = st.assets.firstOrNull { it.holding.symbol == "XRP" }?.currentValue ?: 0.0))
        }
        evaluateAlerts(st)
        loadMarket()
    }
    private suspend fun daoHoldings(): List<Holding> { AppDb.seedIfEmpty(dao); return AppDb.get(getApplication()).dao().holdings() }

    fun loadMarket() = viewModelScope.launch {
        val (list, err) = repo.fetchMarket(); _market.value = list; _marketErr.value = err
    }

    private suspend fun evaluateAlerts(st: PortfolioState) {
        AlertsEngine.evaluate(st, AppDb.get(getApplication()).dao().enabledRules()).forEach { f ->
            Notify.send(getApplication(), (f.rule.id * 1000 + (System.currentTimeMillis() % 900)).toInt(), f.title, f.message)
            dao.addHistory(AlertHistory(ts = System.currentTimeMillis(), title = f.title, message = f.message, symbol = f.symbol, triggerPrice = f.price, pctChange = f.pct))
            dao.updateRule(f.rule.copy(lastFiredAt = System.currentTimeMillis(), lastFiredKey = f.key))
        }
    }

    fun startStreaming() {
        if (wsJob?.isActive == true) return
        wsJob = viewModelScope.launch {
            var delayMs = 2000L
            while (isActive) {
                try {
                    val closed = CompletableDeferred<Unit>()
                    ws = repo.openMiniTickerSocket(onTick = { sym, price ->
                        val cur = _state.value; if (cur.assets.isEmpty()) return@openMiniTickerSocket
                        val assets = cur.assets.map { a ->
                            if (a.holding.symbol == sym && a.quote != null) {
                                val q = a.quote
                                AssetState(a.holding, q.copy(price = price, ts = System.currentTimeMillis()))
                            } else a
                        }
                        _state.value = cur.copy(assets = assets, lastUpdate = System.currentTimeMillis())
                    }, onClose = { closed.complete(Unit) })
                    delayMs = 2000L
                    closed.await()
                } catch (_: Exception) { }
                delay(delayMs); delayMs = (delayMs * 2).coerceAtMost(30_000)
            }
        }
    }
    fun stopStreaming() { ws?.close(1000, "background"); ws = null; wsJob?.cancel(); wsJob = null }

    fun saveHolding(h: Holding) = viewModelScope.launch { dao.upsertHolding(h); refresh() }
    fun addRule(r: AlertRule) = viewModelScope.launch { dao.addRule(r); refresh() }
    fun updateRule(r: AlertRule) = viewModelScope.launch { dao.updateRule(r) }
    fun deleteRule(r: AlertRule) = viewModelScope.launch { dao.deleteRule(r) }
    fun clearHistory() = viewModelScope.launch { dao.clearHistory() }
    fun clearSnapshots() = viewModelScope.launch { dao.clearSnapshots() }
    fun setBackendUrl(url: String) { prefs.edit().putString("backend_url", url).apply(); repo.backendUrl = url; refresh() }
    fun backendUrl(): String = prefs.getString("backend_url", BuildConfig.DEFAULT_BACKEND_URL) ?: BuildConfig.DEFAULT_BACKEND_URL
    fun testNotification() = Notify.send(getApplication(), 999001, "Tawfiq CryptoSignal", "Test notification — alerts are working on this device.")
    override fun onCleared() { stopStreaming(); super.onCleared() }
}
