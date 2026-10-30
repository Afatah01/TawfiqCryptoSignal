package com.tawfiq.cryptosignal.data
import kotlinx.coroutines.*
import okhttp3.*; import org.json.JSONObject
// Fallback chain per refresh: Binance (data-api.binance.vision) -> your Render backend -> CoinGecko.
// Every Quote carries source + timestamp; stale (>2 min) is flagged, never shown as live.
class Repository(private val dao:Dao, @Volatile var backendUrl:String) {
  private val geckoIds = mapOf("BTC" to "bitcoin","BNB" to "binancecoin","XRP" to "ripple","ETH" to "ethereum","SOL" to "solana","DOGE" to "dogecoin","USDT" to "tether","USDC" to "usd-coin")
  suspend fun fetchPortfolioQuotes(holdings:List<Holding>): Pair<Map<String,Quote>,String?> {
    val out = mutableMapOf<String,Quote>(); var lastErr:String?=null; val now=System.currentTimeMillis()
    try {
      coroutineScope { holdings.filter{!it.isReserve}.map{ h -> async {
        val t = Net.binance.ticker(h.symbol+"USDT")
        synchronized(out){ out[h.symbol]=Quote(h.symbol,h.symbol+"USDT",t.lastPrice?.toDoubleOrNull()?:0.0,
          t.priceChangePercent?.toDoubleOrNull()?:0.0, (t.priceChange?.toDoubleOrNull()?:0.0), t.highPrice?.toDoubleOrNull()?:0.0,
          t.lowPrice?.toDoubleOrNull()?:0.0, t.quoteVolume?.toDoubleOrNull()?:0.0, "Binance", now) }
      }}.awaitAll() }
      out["USDT"]=Quote("USDT","USDTUSD",1.0,0.0,0.0,1.0,1.0,0.0,"Pegged (display)",now)
      return out to null
    } catch(e:Exception){ lastErr="Binance: ${e.message}" }
    try {
      Net.backend(backendUrl)?.let{ b -> val m=b.prices()
        m.forEach{(k,v)-> out[k]=Quote(k,k+"USDT",v.price?:0.0,v.change24hPct?:0.0,v.change24hUsd?:0.0,v.high24h?:0.0,v.low24h?:0.0,v.volume24h?:0.0,v.source?:"Backend",v.ts?:now) }
        if(out.isNotEmpty()){ out.putIfAbsent("USDT",Quote("USDT","USDTUSD",1.0,0.0,0.0,1.0,1.0,0.0,"Pegged (display)",now)); return out to lastErr }
      }
    } catch(e:Exception){ lastErr="$lastErr | Backend: ${e.message}" }
    try {
      val ids = holdings.mapNotNull{geckoIds[it.symbol]}.joinToString(",")
      Net.gecko.markets(ids=ids).forEach{ g ->
        val sym = holdings.firstOrNull{ geckoIds[it.symbol]==g.id }?.symbol ?: (g.symbol?.uppercase() ?: return@forEach)
        out[sym]=Quote(sym,sym+"USDT",g.currentPrice?:0.0,g.pct24h?:0.0,g.change24h?:0.0,g.high24h?:0.0,g.low24h?:0.0,g.volume?:0.0,"CoinGecko",now)
      }
      out.putIfAbsent("USDT",Quote("USDT","USDTUSD",1.0,0.0,0.0,1.0,1.0,0.0,"Pegged (display)",now))
      if(out.isNotEmpty()) return out to lastErr
    } catch(e:Exception){ lastErr="$lastErr | CoinGecko: ${e.message}" }
    return out to (lastErr ?: "No price source reachable. Check connection.")
  }
  // Market Highlights = Binance real-time (via backend /api/market). CoinGecko only if Binance+backend unreachable.
  suspend fun fetchMarket(): Pair<List<GeckoMarket>,String?> {
    try { Net.backend(backendUrl)?.let{ val m=it.market(); if(m.isNotEmpty()) return m to null } } catch(_:Exception){}
    try { return Net.gecko.topMarkets() to "Binance unavailable - showing CoinGecko fallback (labelled)." } catch(e:Exception){
      return emptyList<GeckoMarket>() to "Market data unavailable: ${e.message}"
    }
  }
  fun openMiniTickerSocket(onTick:(String,Double)->Unit, onClose:()->Unit): WebSocket {
    val req = Request.Builder().url("wss://data-stream.binance.vision/stream?streams=btcusdt@miniTicker/bnbusdt@miniTicker/xrpusdt@miniTicker").build()
    return Net.wsClient().newWebSocket(req, object: WebSocketListener(){
      override fun onMessage(ws:WebSocket, text:String){
        try { val j=JSONObject(text); val d=j.getJSONObject("data"); val s=d.getString("s").removeSuffix("USDT"); val p=d.getString("c").toDouble(); onTick(s,p) } catch(_:Exception){}
      }
      override fun onFailure(ws:WebSocket, t:Throwable, r:Response?){ onClose() }
      override fun onClosed(ws:WebSocket, code:Int, reason:String){ onClose() }
    })
  }
}
