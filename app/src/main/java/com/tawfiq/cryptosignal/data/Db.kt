package com.tawfiq.cryptosignal.data
import android.content.*
import android.database.sqlite.*
import kotlinx.coroutines.flow.*

// Local persistence via SQLite (no annotation processing). Baseline seeded once with
// Oct 28, 2026 values; price updates NEVER touch holdings - only explicit user edits do.
class Dao(private val db: SQLiteDatabase) {
    private val _holdings = MutableStateFlow(readHoldings())
    private val _snaps = MutableStateFlow(readSnapshots())
    private val _rules = MutableStateFlow(readRules())
    private val _history = MutableStateFlow(readHistory())
    fun holdingsFlow(): Flow<List<Holding>> = _holdings
    fun snapshotsFlow(): Flow<List<Snapshot>> = _snaps
    fun rulesFlow(): Flow<List<AlertRule>> = _rules
    fun historyFlow(): Flow<List<AlertHistory>> = _history
    suspend fun holdings(): List<Holding> = readHoldings()
    suspend fun enabledRules(): List<AlertRule> = readRules().filter { it.enabled }
    suspend fun lastSnapshot(): Snapshot? = readSnapshots().lastOrNull()
    suspend fun upsertHolding(h: Holding) {
        db.replace("holdings", null, ContentValues().apply {
            put("symbol", h.symbol); put("name", h.name); put("quantity", h.quantity)
            put("purchasePrice", h.purchasePrice); put("investedUsd", h.investedUsd)
            put("isReserve", if (h.isReserve) 1 else 0); put("alertEnabled", if (h.alertEnabled) 1 else 0) })
        _holdings.value = readHoldings()
    }
    suspend fun addSnapshot(s: Snapshot) {
        db.insert("snapshots", null, ContentValues().apply {
            put("ts", s.ts); put("totalValue", s.totalValue); put("cryptoValue", s.cryptoValue)
            put("pnl", s.pnl); put("btc", s.btc); put("bnb", s.bnb); put("xrp", s.xrp) })
        _snaps.value = readSnapshots()
    }
    suspend fun clearSnapshots() { db.delete("snapshots", null, null); _snaps.value = emptyList() }
    suspend fun addRule(r: AlertRule): Long {
        val id = db.insert("alert_rules", null, ContentValues().apply {
            put("scope", r.scope); put("type", r.type); put("threshold", r.threshold)
            put("enabled", if (r.enabled) 1 else 0); put("cooldownMin", r.cooldownMin)
            put("lastFiredAt", r.lastFiredAt); put("lastFiredKey", r.lastFiredKey) })
        _rules.value = readRules(); return id
    }
    suspend fun updateRule(r: AlertRule) {
        db.update("alert_rules", ContentValues().apply {
            put("scope", r.scope); put("type", r.type); put("threshold", r.threshold)
            put("enabled", if (r.enabled) 1 else 0); put("cooldownMin", r.cooldownMin)
            put("lastFiredAt", r.lastFiredAt); put("lastFiredKey", r.lastFiredKey) }, "id=?", arrayOf(r.id.toString()))
        _rules.value = readRules()
    }
    suspend fun deleteRule(r: AlertRule) { db.delete("alert_rules", "id=?", arrayOf(r.id.toString())); _rules.value = readRules() }
    suspend fun addHistory(h: AlertHistory) {
        db.insert("alert_history", null, ContentValues().apply {
            put("ts", h.ts); put("title", h.title); put("message", h.message); put("symbol", h.symbol)
            put("triggerPrice", h.triggerPrice); put("pctChange", h.pctChange) })
        _history.value = readHistory()
    }
    suspend fun clearHistory() { db.delete("alert_history", null, null); _history.value = emptyList() }
    private fun readHoldings(): List<Holding> = db.rawQuery("SELECT * FROM holdings", null).use { c -> buildList {
        while (c.moveToNext()) add(Holding(c.getString(0), c.getString(1), c.getDouble(2), c.getDouble(3), c.getDouble(4), c.getInt(5) == 1, c.getInt(6) == 1)) } }
    private fun readSnapshots(): List<Snapshot> = db.rawQuery("SELECT * FROM snapshots ORDER BY ts ASC", null).use { c -> buildList {
        while (c.moveToNext()) add(Snapshot(c.getLong(0), c.getLong(1), c.getDouble(2), c.getDouble(3), c.getDouble(4), c.getDouble(5), c.getDouble(6), c.getDouble(7))) } }
    private fun readRules(): List<AlertRule> = db.rawQuery("SELECT * FROM alert_rules", null).use { c -> buildList {
        while (c.moveToNext()) add(AlertRule(c.getLong(0), c.getString(1), c.getString(2), c.getDouble(3), c.getInt(4) == 1, c.getInt(5), c.getLong(6), c.getString(7) ?: "")) } }
    private fun readHistory(): List<AlertHistory> = db.rawQuery("SELECT * FROM alert_history ORDER BY ts DESC LIMIT 200", null).use { c -> buildList {
        while (c.moveToNext()) add(AlertHistory(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4), c.getDouble(5), c.getDouble(6))) } }
}

class AppDb private constructor(context: Context) : SQLiteOpenHelper(context, "tawfiq.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE holdings(symbol TEXT PRIMARY KEY,name TEXT,quantity REAL,purchasePrice REAL,investedUsd REAL,isReserve INTEGER,alertEnabled INTEGER)")
        db.execSQL("CREATE TABLE snapshots(id INTEGER PRIMARY KEY AUTOINCREMENT,ts INTEGER,totalValue REAL,cryptoValue REAL,pnl REAL,btc REAL,bnb REAL,xrp REAL)")
        db.execSQL("CREATE TABLE alert_rules(id INTEGER PRIMARY KEY AUTOINCREMENT,scope TEXT,type TEXT,threshold REAL,enabled INTEGER,cooldownMin INTEGER,lastFiredAt INTEGER,lastFiredKey TEXT)")
        db.execSQL("CREATE TABLE alert_history(id INTEGER PRIMARY KEY AUTOINCREMENT,ts INTEGER,title TEXT,message TEXT,symbol TEXT,triggerPrice REAL,pctChange REAL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}
    fun dao(): Dao = daoInstance ?: Dao(writableDatabase).also { daoInstance = it }
    @Volatile private var daoInstance: Dao? = null
    companion object {
        @Volatile private var I: AppDb? = null
        fun get(c: Context): AppDb = I ?: synchronized(this) { AppDb(c.applicationContext).also { I = it } }
        suspend fun seedIfEmpty(dao: Dao) {
            if (dao.holdings().isEmpty()) {
                dao.upsertHolding(Holding("BTC", "Bitcoin", 0.006198, 80642.26, 500.0))
                dao.upsertHolding(Holding("BNB", "BNB", 0.398894, 752.08, 300.0))
                dao.upsertHolding(Holding("XRP", "XRP", 151.52, 1.32, 200.0))
                dao.upsertHolding(Holding("USDT", "Tether (Emergency Reserve)", 200.0, 1.0, 200.0, isReserve = true))
            }
            if (dao.enabledRules().isEmpty()) {
                listOf(1.0, 2.0, 3.0, 5.0).forEach { dao.addRule(AlertRule(scope = "PORTFOLIO", type = "PCT_UP", threshold = it)) }
                listOf(1.0, 2.0, 3.0, 5.0).forEach { dao.addRule(AlertRule(scope = "PORTFOLIO", type = "PCT_DOWN", threshold = it)) }
                listOf("BTC", "BNB", "XRP").forEach { s ->
                    dao.addRule(AlertRule(scope = s, type = "PCT_UP", threshold = 3.0))
                    dao.addRule(AlertRule(scope = s, type = "PCT_DOWN", threshold = 3.0))
                }
            }
        }
    }
}
