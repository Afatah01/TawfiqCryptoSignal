package com.tawfiq.cryptosignal.ui
import android.app.Application
import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.tawfiq.cryptosignal.*
import com.tawfiq.cryptosignal.data.*
import com.tawfiq.cryptosignal.util.Notify
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

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
    private val _aiText = MutableStateFlow("Tap Refresh analysis — Tawfiq AI uses live Binance indicators (RSI, MACD, EMA, Bollinger, volume)."); val aiText: StateFlow<String> = _aiText
    private val _aiBusy = MutableStateFlow(false); val aiBusy: StateFlow<Boolean> = _aiBusy
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    fun loadAi() = viewModelScope.launch {
        _aiBusy.value = true
        try {
            val req = Request.Builder().url(backendUrl().trimEnd('/') + "/api/ai/analysis").build()
            val body = withContext(Dispatchers.IO) { http.newCall(req).execute().body?.string() ?: "" }
            val j = JSONObject(body); val arr = j.getJSONArray("analyses"); val sb = StringBuilder()
            sb.append("Provider: ").append(j.optString("provider")).append("\nUpdated: ").append(java.util.Date(j.optLong("generatedAt"))).append("\n\n")
            for (i in 0 until arr.length()) { val a = arr.getJSONObject(i)
                sb.append(a.optString("symbol")).append("  ").append(String.format("USD %,.4g", a.optDouble("price")))
                  .append("  [").append(a.optString("signal")).append("] ").append(a.optString("direction"))
                  .append(" • RSI ").append(a.optDouble("rsi")).append(" • Risk ").append(a.optString("risk"))
                  .append(" • Confidence ").append(a.optInt("confidencePct")).append("%\n")
                val rs = a.optJSONArray("reasons"); if (rs != null) for (k in 0 until minOf(rs.length(), 3)) sb.append("• ").append(rs.optString(k)).append("\n")
                if (a.has("portfolio")) { val pf = a.getJSONObject("portfolio"); sb.append("My holding: $").append(pf.optDouble("value")).append(" (P/L $").append(pf.optDouble("pnl")).append(")\n") }
                sb.append("\n") }
            val al = j.optJSONArray("alerts"); if (al != null && al.length() > 0) { sb.append("🔔 AI Alerts\n"); for (i in 0 until al.length()) { val a = al.getJSONObject(i); sb.append("• ").append(a.optString("symbol")).append(": ").append(a.optString("type")).append(" — ").append(a.optString("detail")).append("\n") } }
            sb.append("\nAnalysis only — not financial advice, never guaranteed profits.")
            _aiText.value = sb.toString()
        } catch (e: Exception) { _aiText.value = "⚠ AI unavailable: ${e.message}. Check backend URL in Settings (needs the Render backend deployed)." }
        _aiBusy.value = false
    }
    fun askAi(q: String) = viewModelScope.launch {
        _aiBusy.value = true
        try {
            val payload = JSONObject().put("question", q).toString()
            val req = Request.Builder().url(backendUrl().trimEnd('/') + "/api/ai/ask").post(payload.toRequestBody("application/json".toMediaType())).build()
            val body = withContext(Dispatchers.IO) { http.newCall(req).execute().body?.string() ?: "" }
            val j = JSONObject(body)
            _aiText.value = "Q: " + q + "\n\n" + j.optString("answer") + "\n\nProvider: " + j.optString("provider") + " • Data: " + java.util.Date(j.optLong("dataTs"))
        } catch (e: Exception) { _aiText.value = "⚠ Ask Tawfiq AI failed: ${e.message}" }
        _aiBusy.value = false
    }
    fun testNotification() = Notify.send(getApplication(), 999001, "Tawfiq CryptoSignal", "Test notification — alerts are working on this device.")
    override fun onCleared() { stopStreaming(); super.onCleared() }
}
