package com.tawfiq.cryptosignal.data
data class Holding(val symbol:String, val name:String, val quantity:Double, val purchasePrice:Double,
  val investedUsd:Double, val isReserve:Boolean=false, val alertEnabled:Boolean=true)
data class Snapshot(val id:Long=0, val ts:Long, val totalValue:Double, val cryptoValue:Double, val pnl:Double,
  val btc:Double, val bnb:Double, val xrp:Double)
data class AlertRule(val id:Long=0, val scope:String, val type:String, val threshold:Double,
  val enabled:Boolean=true, val cooldownMin:Int=60, val lastFiredAt:Long=0, val lastFiredKey:String="")
data class AlertHistory(val id:Long=0, val ts:Long, val title:String, val message:String, val symbol:String,
  val triggerPrice:Double, val pctChange:Double)
data class Quote(val symbol:String, val pair:String, val price:Double, val change24hPct:Double,
  val change24hUsd:Double, val high24h:Double, val low24h:Double, val volume24h:Double,
  val source:String, val ts:Long, val stale:Boolean=false)
data class AssetState(val holding:Holding, val quote:Quote?) {
  val currentValue get() = (quote?.price ?: holding.purchasePrice) * holding.quantity
  val pnl get() = currentValue - holding.investedUsd
  val pnlPct get() = if (holding.investedUsd==0.0) 0.0 else pnl/holding.investedUsd*100.0
}
data class PortfolioState(val assets:List<AssetState>, val lastUpdate:Long=0, val source:String="", val error:String?=null, val loading:Boolean=false) {
  val total get() = assets.sumOf { it.currentValue }
  val cryptoValue get() = assets.filter{!it.holding.isReserve}.sumOf { it.currentValue }
  val reserveValue get() = assets.filter{it.holding.isReserve}.sumOf { it.currentValue }
  val initialTotal get() = assets.sumOf { it.holding.investedUsd }
  val initialCrypto get() = assets.filter{!it.holding.isReserve}.sumOf { it.holding.investedUsd }
  val pnl get() = total - initialTotal
  val pnlPct get() = if(initialTotal==0.0) 0.0 else pnl/initialTotal*100.0
  val cryptoPnl get() = cryptoValue - initialCrypto
  val cryptoPnlPct get() = if(initialCrypto==0.0) 0.0 else cryptoPnl/initialCrypto*100.0
  val change24hUsd get() = assets.sumOf { a -> (a.quote?.let { it.change24hUsd * a.holding.quantity } ?: 0.0) }
  val best get() = assets.filter{!it.holding.isReserve}.maxByOrNull{it.pnlPct}
  val worst get() = assets.filter{!it.holding.isReserve}.minByOrNull{it.pnlPct}
  val isStale get() = lastUpdate>0 && System.currentTimeMillis()-lastUpdate > 120_000
}
