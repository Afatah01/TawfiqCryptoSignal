package com.tawfiq.cryptosignal
import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tawfiq.cryptosignal.data.*
import com.tawfiq.cryptosignal.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

val Emerald = Color(0xFF4776F5); val EmeraldDark = Color(0xFF263247); val Gold = Color(0xFFF5BD55)
val RoyalBlue = Color(0xFF4776F5); val Turq = Color(0xFF16C9B2); val Violet = Color(0xFF9270F5); val EPurple = Color(0xFF7457E8)
val LightBg = Color(0xFFF4F7FC); val UpGreen = Color(0xFF18B981); val DownRed = Color(0xFFF36B7F); val Muted = Color(0xFF8793A5)
fun usd(v: Double): String = (if (v < 0) "-$" else "$") + "%,.2f".format(kotlin.math.abs(v))
fun pct(v: Double): String = "%+.2f%%".format(v)
fun timeStr(ts: Long): String = if (ts == 0L) "—" else SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.UK).format(Date(ts))

class MainActivity : ComponentActivity() {
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { AppRoot() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AppRoot(vm: MainViewModel = viewModel()) {
    var splash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(1600); splash = false }
    MaterialTheme(colorScheme = lightColorScheme(primary = Emerald, secondary = Gold, background = LightBg, surface = Color.White)) {
        if (splash) { SplashScreen(); return@MaterialTheme }
        val tabs = listOf("Dashboard", "Portfolio", "Market", "Charts", "AI", "Settings", "About")
        val icons = listOf(Icons.Filled.Home, Icons.Filled.Star, Icons.Filled.Search, Icons.Filled.List, Icons.Filled.Face, Icons.Filled.Settings, Icons.Filled.Info)
        var tab by remember { mutableStateOf(0) }
        val state by vm.state.collectAsState()
        DisposableEffect(Unit) { vm.startStreaming(); onDispose { vm.stopStreaming() } }
        Scaffold(
            topBar = { TopAppBar(title = { Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.logo), null, Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(8.dp)); Text("Tawfiq CryptoSignal", fontWeight = FontWeight.Bold, color = EmeraldDark)
            } }, actions = { IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, "Refresh prices") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)) },
            bottomBar = { NavigationBar(containerColor = Color.White) { tabs.forEachIndexed { i, t ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icons[i], t) }, label = { Text(t, maxLines = 1) }) } } }
        ) { pad ->
            Box(Modifier.padding(pad).background(LightBg).fillMaxSize()) {
                when (tab) {
                    0 -> Dashboard(vm, state) { tab = it }
                    1 -> PortfolioScreen(vm, state)
                    2 -> MarketScreen(vm)
                    3 -> ChartsScreen(vm, state)
                    4 -> AiScreen(vm)
                    5 -> SettingsScreen(vm, state)
                    else -> AboutScreen()
                }
            }
        }
    }
}

@Composable fun SplashScreen() {
    Column(Modifier.fillMaxSize().background(Color.White), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(painterResource(R.drawable.logo), "Tawfiq CryptoSignal logo", Modifier.size(260.dp))
        Spacer(Modifier.height(12.dp)); CircularProgressIndicator(color = Gold)
        Text("TRACK • ALERT • GROW", color = Emerald, fontWeight = FontWeight.Medium)
    }
}

@Composable fun StatusBanner(state: PortfolioState) {
    if (state.error != null) Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)), modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Text("⚠ ${state.error} Prices may be stale — last update: ${timeStr(state.lastUpdate)}", Modifier.padding(12.dp), color = DownRed) }
    else if (state.isStale) Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)), modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Text("⏱ Data is stale (over 2 min old) — last update: ${timeStr(state.lastUpdate)} • Source: ${state.source}", Modifier.padding(12.dp)) }
    else if (state.lastUpdate > 0) Text("● Live • Source: ${state.source} • Updated: ${timeStr(state.lastUpdate)}", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = UpGreen)
}

@Composable fun Dashboard(vm: MainViewModel, state: PortfolioState, go: (Int) -> Unit) {
    LazyColumn {
        item { StatusBanner(state) }
        item { Card(Modifier.fillMaxWidth().padding(12.dp).shadow(14.dp, RoundedCornerShape(24.dp)), colors = CardDefaults.cardColors(containerColor = Color.Transparent), shape = RoundedCornerShape(24.dp)) { Box(Modifier.background(Brush.linearGradient(listOf(RoyalBlue, Violet, EPurple))).fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("Total Portfolio Value (incl. $200 USDT reserve)", color = Color.White.copy(.85f))
                Text(usd(state.total), color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Initial (28 Oct 2026): ${usd(state.initialTotal)}  •  Since purchase: ${usd(state.pnl)} (${pct(state.pnlPct)})", color = if (state.pnl >= 0) Color(0xFFB9F6CA) else Color(0xFFFFCDD2))
                Text("24h market change (not since-purchase): ${usd(state.change24hUsd)}", color = Color.White.copy(.9f))
                if (state.loading) LinearProgressIndicator(color = Gold, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            } } } }
        item { Row(Modifier.padding(horizontal = 12.dp)) {
            StatCard("Crypto invested\n$1,000 → now", usd(state.cryptoValue) + "\n" + pct(state.cryptoPnlPct), state.cryptoPnl >= 0, Modifier.weight(1f))
            StatCard("Emergency reserve\nUSDT (separate)", usd(state.reserveValue), true, Modifier.weight(1f)) } }
        item { Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            StatCard("Best performer", (state.best?.holding?.symbol ?: "—") + "\n" + pct(state.best?.pnlPct ?: 0.0), true, Modifier.weight(1f))
            StatCard("Worst performer", (state.worst?.holding?.symbol ?: "—") + "\n" + pct(state.worst?.pnlPct ?: 0.0), (state.worst?.pnlPct ?: 0.0) >= 0, Modifier.weight(1f)) } }
        items(state.assets) { a -> AssetCard(a) }
        item { Text("Estimates use live public prices × your stored quantities. Final Binance valuation may differ (fees/spread). Monitoring only — no trading.", Modifier.padding(16.dp), color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
        item { Row(Modifier.padding(12.dp)) { Button(onClick = { go(2) }, colors = ButtonDefaults.buttonColors(containerColor = EPurple), modifier = Modifier.weight(1f)) { Text("Market Highlights", color = Color.White) }
            Spacer(Modifier.width(8.dp)); OutlinedButton(onClick = { go(4) }, modifier = Modifier.weight(1f)) { Text("🤖 Tawfiq AI") } } }
    }
}
@Composable fun StatCard(label: String, value: String, up: Boolean, modifier: Modifier = Modifier) {
    Card(modifier.padding(4.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(12.dp)) { Text(label, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            Text(value, color = if (up) UpGreen else DownRed, fontWeight = FontWeight.Bold) } }
}
@Composable fun AssetCard(a: AssetState) {
    val q = a.quote; val up = a.pnl >= 0
    val grad = when(a.holding.symbol){ "BTC" -> listOf(Color(0xFFFFF3E0), Color(0xFFFFE3B3)); "BNB" -> listOf(Color(0xFFFFF8E1), Color(0xFFFBE9B7)); "XRP" -> listOf(Color(0xFFE3F2FD), Color(0xFFCDEBFF)); else -> listOf(Color(0xFFE0F7F3), Color(0xFFC9F5EE)) }
    val badge = when(a.holding.symbol){ "BTC" -> Color(0xFFF5A623); "BNB" -> Color(0xFFE3B341); "XRP" -> Color(0xFF4FA8E0); else -> Turq }
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).shadow(8.dp, RoundedCornerShape(20.dp)), colors = CardDefaults.cardColors(containerColor = Color.Transparent), shape = RoundedCornerShape(20.dp)) {
      Box(Modifier.background(Brush.linearGradient(grad)).fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(badge), contentAlignment = Alignment.Center) { Text(a.holding.symbol.take(1), color = Color.White, fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(8.dp)); Text("${a.holding.symbol} — ${a.holding.name}", fontWeight = FontWeight.Bold, color = EmeraldDark) }
                Text(usd(a.currentValue), fontWeight = FontWeight.Bold) }
            Text("Qty ${a.holding.quantity} • Bought @ ${usd(a.holding.purchasePrice)} (invested ${usd(a.holding.investedUsd)})", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            if (a.holding.isReserve) Text("Emergency reserve — excluded from crypto return.", color = Gold, style = MaterialTheme.typography.bodySmall)
            else if (q != null) {
                Text("Now ${usd(q.price)} • 24h ${pct(q.change24hPct)} • High ${usd(q.high24h)} / Low ${usd(q.low24h)}", style = MaterialTheme.typography.bodySmall)
                Text("My P/L since purchase: ${usd(a.pnl)} (${pct(a.pnlPct)})", color = if (up) UpGreen else DownRed, fontWeight = FontWeight.SemiBold)
                Text("Source: ${q.source} • ${timeStr(q.ts)}${if (q.stale) " • STALE" else ""}", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            } else Text("Waiting for live price…", color = Muted)
        } } }
}

@Composable fun PortfolioScreen(vm: MainViewModel, state: PortfolioState) {
    LazyColumn {
        item { StatusBanner(state); Text("My Portfolio — baseline 28 Oct 2026 (never overwritten by prices; edit only in Settings)", Modifier.padding(12.dp), fontWeight = FontWeight.Bold, color = EmeraldDark) }
        item { Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(16.dp)) {
            Text("Total invested capital: ${usd(state.initialTotal)}  (Crypto $1,000 + Reserve $200)")
            Text("Current total: ${usd(state.total)}  •  P/L: ${usd(state.pnl)} (${pct(state.pnlPct)})", color = if (state.pnl >= 0) UpGreen else DownRed, fontWeight = FontWeight.Bold)
            Text("Crypto-only return: ${usd(state.cryptoPnl)} (${pct(state.cryptoPnlPct)}) on $1,000")
            Text("Change since 28 Oct 2026: ${usd(state.pnl)} (${pct(state.pnlPct)})") } } }
        items(state.assets) { a -> AssetCard(a) }
    }
}

@Composable fun MarketScreen(vm: MainViewModel) {
    val market by vm.market.collectAsState(); val err by vm.marketErr.collectAsState()
    var search by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadMarket() }
    val filtered = market.filter { search.isBlank() || it.name?.contains(search, true) == true || it.symbol?.contains(search, true) == true }
    val gainers = market.sortedByDescending { it.pct24h ?: 0.0 }.take(3); val losers = market.sortedBy { it.pct24h ?: 0.0 }.take(3)
    Column {
        Text("Market Highlights — market-wide moves (NOT your portfolio). No predictions or guaranteed signals.", Modifier.padding(12.dp), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(search, { search = it }, label = { Text("Search coins (e.g. ETH, SOL)") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
        if (err != null) Text("⚠ $err", Modifier.padding(12.dp), color = DownRed)
        LazyColumn {
            item { Text("🚀 Biggest gainers (24h)", Modifier.padding(12.dp), fontWeight = FontWeight.Bold, color = UpGreen) }
            items(gainers) { MarketRow(it) }
            item { Text("📉 Biggest losers (24h)", Modifier.padding(12.dp), fontWeight = FontWeight.Bold, color = DownRed) }
            items(losers) { MarketRow(it) }
            item { Text("All coins (Binance real-time)", Modifier.padding(12.dp), fontWeight = FontWeight.Bold) }
            if (filtered.isEmpty()) item { Text("No market data yet — check connection and tap the top refresh button.", Modifier.padding(16.dp), color = Color.Gray) }
            items(filtered) { MarketRow(it) }
        }
    }
}
@Composable fun MarketRow(m: GeckoMarket) {
    val up = (m.pct24h ?: 0.0) >= 0
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("${m.symbol?.uppercase()} — ${m.name}", fontWeight = FontWeight.SemiBold); Text("Vol ${usd(m.volume ?: 0.0)} • H ${usd(m.high24h ?: 0.0)} / L ${usd(m.low24h ?: 0.0)}", color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
            Column(horizontalAlignment = Alignment.End) { Text(usd(m.currentPrice ?: 0.0), fontWeight = FontWeight.Bold); Text(pct(m.pct24h ?: 0.0), color = if (up) UpGreen else DownRed) } } }
}

@Composable fun AlertsScreen(vm: MainViewModel) {
    val rules by vm.rules.collectAsState(); val history by vm.history.collectAsState()
    var scope by remember { mutableStateOf("PORTFOLIO") }; var type by remember { mutableStateOf("PCT_UP") }; var thresh by remember { mutableStateOf("3") }
    LazyColumn {
        item { Text("Price Alerts", Modifier.padding(12.dp), style = MaterialTheme.typography.titleLarge, color = EmeraldDark, fontWeight = FontWeight.Bold)
            Text("Foreground: immediate (REST + WebSocket). Background: WorkManager checks ~every 15 min (Android minimum; battery optimisation may delay). Dedup + cooldown prevent repeats.", Modifier.padding(horizontal = 12.dp), color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
        item { Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(14.dp)) {
            Text("Add rule — scope: $scope", fontWeight = FontWeight.SemiBold)
            Row { listOf("PORTFOLIO", "BTC", "BNB", "XRP").forEach { s -> FilterChip(selected = scope == s, onClick = { scope = s }, label = { Text(s) }, modifier = Modifier.padding(end = 4.dp)) } }
            Row { listOf("PCT_UP", "PCT_DOWN", "TARGET_ABOVE", "TARGET_BELOW", "PNL_USD").forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) }, modifier = Modifier.padding(end = 4.dp)) } }
            OutlinedTextField(thresh, { thresh = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Threshold (% for PCT, USD otherwise)") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { thresh.toDoubleOrNull()?.let { vm.addRule(AlertRule(scope = scope, type = type, threshold = it)) } }, colors = ButtonDefaults.buttonColors(containerColor = Emerald), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Add alert rule") }
            Text("Quick portfolio presets 1% / 2% / 3% / 5% up & down are pre-installed. Portfolio % is vs your $1,200 baseline; coin % is vs your purchase price.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        } } }
        item { Text("Active rules (${rules.size})", Modifier.padding(12.dp), fontWeight = FontWeight.Bold) }
        items(rules) { r -> Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${r.scope} • ${r.type} • ${r.threshold} • cooldown ${r.cooldownMin}m", Modifier.weight(1f))
                Switch(checked = r.enabled, onCheckedChange = { vm.updateRule(r.copy(enabled = it)) })
                IconButton(onClick = { vm.deleteRule(r) }) { Icon(Icons.Filled.Delete, "Delete rule") } } } }
        item { Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Alert history", fontWeight = FontWeight.Bold); TextButton(onClick = { vm.clearHistory() }) { Text("Clear history") } } }
        if (history.isEmpty()) item { Text("No alerts fired yet.", Modifier.padding(16.dp), color = Color.Gray) }
        items(history) { h -> Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(12.dp)) { Text(h.title, fontWeight = FontWeight.SemiBold); Text(h.message); Text("${timeStr(h.ts)} • ${h.symbol} @ ${usd(h.triggerPrice)} • ${pct(h.pctChange)}", color = Color.Gray, style = MaterialTheme.typography.bodySmall) } } }
    }
}

@Composable fun ChartsScreen(vm: MainViewModel, state: PortfolioState) {
    val snaps by vm.snapshots.collectAsState()
    var range by remember { mutableStateOf("All") }
    val now = System.currentTimeMillis()
    val filtered = when (range) { "24h" -> snaps.filter { it.ts > now - 86400000 }; "7d" -> snaps.filter { it.ts > now - 7 * 86400000L }
        "30d" -> snaps.filter { it.ts > now - 30 * 86400000L }; "90d" -> snaps.filter { it.ts > now - 90 * 86400000L }; else -> snaps }
    LazyColumn {
        item { Text("Portfolio Charts — from snapshots recorded by THIS app (no invented history before first run)", Modifier.padding(12.dp), fontWeight = FontWeight.Bold, color = EmeraldDark)
            Row(Modifier.padding(horizontal = 12.dp)) { listOf("24h", "7d", "30d", "90d", "All").forEach { r -> FilterChip(selected = range == r, onClick = { range = r }, label = { Text(if (r == "All") "Since 28 Oct 2026" else r) }, modifier = Modifier.padding(end = 4.dp)) } } }
        item { ChartCard("Total portfolio value over time") { LineChart(filtered.map { it.totalValue }, Emerald) } }
        item { ChartCard("Profit / Loss ($) over time") { LineChart(filtered.map { it.pnl }, if ((filtered.lastOrNull()?.pnl ?: 0.0) >= 0) UpGreen else DownRed) } }
        item { ChartCard("BTC value over time") { LineChart(filtered.map { it.btc }, Gold) } }
        item { ChartCard("BNB value over time") { LineChart(filtered.map { it.bnb }, Color(0xFFF0B90B)) } }
        item { ChartCard("XRP value over time") { LineChart(filtered.map { it.xrp }, Color(0xFF25A4E9)) } }
        item { ChartCard("Asset allocation now") { AllocationChart(state) } }
        if (filtered.size < 2) item { Text("Charts need at least 2 recorded snapshots. Keep using the app — snapshots save on refresh and in background checks.", Modifier.padding(16.dp), color = Color.Gray) }
    }
}
@Composable fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(14.dp)) { Text(title, fontWeight = FontWeight.Bold, color = EmeraldDark); content() } }
}
@Composable fun LineChart(values: List<Double>, color: Color) {
    if (values.size < 2) { Text("Not enough data yet.", color = Color.Gray, modifier = Modifier.padding(8.dp)); return }
    val min = values.min(); val max = values.max(); val range = (max - min).takeIf { it > 0 } ?: 1.0
    Canvas(Modifier.fillMaxWidth().height(140.dp).padding(8.dp)) {
        val stepX = size.width / (values.size - 1)
        for (i in 0 until values.size - 1) {
            val x1 = i * stepX; val y1 = size.height - ((values[i] - min) / range * size.height).toFloat()
            val x2 = (i + 1) * stepX; val y2 = size.height - ((values[i + 1] - min) / range * size.height).toFloat()
            drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = 5f) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Min ${usd(min)}", color = Color.Gray, style = MaterialTheme.typography.labelSmall); Text("Max ${usd(max)}", color = Color.Gray, style = MaterialTheme.typography.labelSmall) }
}
@Composable fun AllocationChart(state: PortfolioState) {
    if (state.total <= 0) { Text("No data yet.", color = Color.Gray); return }
    val colors = listOf(RoyalBlue, Gold, Turq, Violet)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(130.dp)) { var start = -90f; state.assets.forEachIndexed { i, a ->
            val sweep = (a.currentValue / state.total * 360).toFloat(); drawArc(colors[i % colors.size], start, sweep, useCenter = true, size = Size(size.width, size.height)); start += sweep } }
        Column(Modifier.padding(start = 12.dp)) { state.assets.forEachIndexed { i, a ->
            Text("■ ${a.holding.symbol}: ${"%.1f".format(a.currentValue / state.total * 100)}% (${usd(a.currentValue)})", color = colors[i % colors.size]) } }
    }
}

@Composable fun AiScreen(vm: MainViewModel) {
    val ai by vm.aiText.collectAsState(); val busy by vm.aiBusy.collectAsState()
    var q by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadAi() }
    LazyColumn {
        item { Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent), shape = RoundedCornerShape(24.dp)) {
            Box(Modifier.background(Brush.linearGradient(listOf(RoyalBlue, Violet, EPurple))).fillMaxWidth()) { Column(Modifier.padding(20.dp)) {
                Text("🤖 TAWFIQ AI MARKET INTELLIGENCE", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Real-time market analysis and intelligent insights — live Binance data. Analysis only, never guaranteed profits.", color = Color.White.copy(.9f))
                Button(onClick = { vm.loadAi() }, colors = ButtonDefaults.buttonColors(containerColor = Color.White), modifier = Modifier.padding(top = 8.dp)) { Text("↻ Refresh analysis", color = EPurple) }
            } } } }
        if (busy) item { LinearProgressIndicator(color = EPurple, modifier = Modifier.fillMaxWidth().padding(12.dp)) }
        item { Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp)) {
            Text("💬 ASK TAWFIQ AI", fontWeight = FontWeight.Bold, color = EmeraldDark)
            OutlinedTextField(q, { q = it }, label = { Text("Ask about BTC, XRP, your portfolio, risk…") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { if (q.isNotBlank()) vm.askAi(q) }, colors = ButtonDefaults.buttonColors(containerColor = EPurple), modifier = Modifier.fillMaxWidth()) { Text("Ask Tawfiq AI ➤") }
            Row { listOf("How is my investment performing?", "Should I hold my XRP?", "Compare BTC, BNB and XRP").forEach { chip -> TextButton(onClick = { q = chip; vm.askAi(chip) }) { Text(chip, style = MaterialTheme.typography.labelSmall) } } }
        } } }
        item { Text(ai, Modifier.padding(16.dp)) }
    }
}

@Composable fun AboutScreen() {
    LazyColumn { item { Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(24.dp)) { Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.logo), "Tawfiq CryptoSignal logo", Modifier.size(140.dp).clip(RoundedCornerShape(20.dp)))
        Text("TAWFIQ CRYPTOSIGNAL", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Text("TRACK • ALERT • GROW", color = Gold, fontWeight = FontWeight.Bold)
        Text("AI-powered cryptocurrency market monitoring and portfolio intelligence.", Modifier.padding(top = 8.dp))
        Text("Live data: Binance (primary, real-time) via secure backend. CoinGecko emergency fallback only. AI: RSI/MACD/EMA/Bollinger engine + OpenAI when configured (server-side). Portfolio: $1,200 fixed baseline, 28 October 2026. Monitoring & education only — no trading, no transfers, no Binance login.", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        Text("Disclaimer: Crypto is volatile. AI analysis is interpretation, not financial advice or a guarantee.", color = Muted, style = MaterialTheme.typography.bodySmall)
    } } } }
}

@Composable fun SettingsScreen(vm: MainViewModel, state: PortfolioState) {
    var backend by remember { mutableStateOf(vm.backendUrl()) }
    LazyColumn {
        item { Text("Settings", Modifier.padding(12.dp), style = MaterialTheme.typography.titleLarge, color = EmeraldDark, fontWeight = FontWeight.Bold) }
        item { Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp)) {
            Text("📌 Original Investment (fixed — not editable)", fontWeight = FontWeight.Bold)
            Text("BTC $500 · 0.006198 @ $80,642.26\nBNB $300 · 0.398894 @ $752.08\nXRP $200 · 151.52 @ $1.32\nUSDT $200 · 200 @ $1.00 (emergency reserve)\nTotal $1,200 (crypto $1,000 + reserve $200) · Baseline: 28 October 2026", color = Muted, style = MaterialTheme.typography.bodySmall)
        } } }
        item { Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp)) {
            Text("🤖 AI & backend", fontWeight = FontWeight.Bold)
            Text("App tries Binance first, then this Render backend (which also hosts Tawfiq AI), then CoinGecko fallback.", color = Muted, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(backend, { backend = it }, label = { Text("Render backend URL") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.setBackendUrl(backend.trim()) }, colors = ButtonDefaults.buttonColors(containerColor = EPurple), modifier = Modifier.fillMaxWidth()) { Text("Save backend URL") }
            Spacer(Modifier.height(8.dp)); OutlinedButton(onClick = { vm.testNotification() }, modifier = Modifier.fillMaxWidth()) { Text("Send test notification") }
            OutlinedButton(onClick = { vm.loadAi() }, modifier = Modifier.fillMaxWidth()) { Text("Refresh Tawfiq AI analysis") }
            Text("API status: Source ${state.source} • Last sync: ${timeStr(state.lastUpdate)}${if (state.isStale) " • STALE" else ""}", Modifier.padding(top = 8.dp), color = if (state.error != null) DownRed else UpGreen)
            Text("Background alerts: WorkManager ~every 15 min (Android minimum). Foreground: immediate. Currency: USD. Theme: premium light.", color = Muted, style = MaterialTheme.typography.bodySmall)
        } } }
        item { Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp)) {
            Text("Data & privacy", fontWeight = FontWeight.Bold)
            Text("Stored locally only: holdings baseline (fixed), snapshots, alert rules/history. Network (HTTPS): Binance / your Render backend (AI). No Binance login, passwords, trading or withdrawal permissions. OpenAI key lives only on the Render server, never in this app.", color = Muted, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { vm.clearSnapshots() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Clear chart history (snapshots)") }
            Text("Disclaimer: Monitoring/education only. Not financial advice. No guaranteed opportunities.", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        } } }
    }
}
