package com.tawfiq.cryptosignal.data
import kotlin.math.abs
// Pure evaluation - unit-tested. Reference: purchase price for coins, $1,200 baseline for portfolio.
// Dedup: same rule+rounded condition key cannot refire until cooldown passes.
object AlertsEngine {
  data class Fired(val rule:AlertRule, val title:String, val message:String, val symbol:String, val price:Double, val pct:Double, val key:String)
  fun evaluate(state:PortfolioState, rules:List<AlertRule>, now:Long=System.currentTimeMillis()): List<Fired> {
    val out=mutableListOf<Fired>()
    for(r in rules.filter{it.enabled}){
      if(now - r.lastFiredAt < r.cooldownMin*60_000L) continue
      fun fire(title:String,msg:String,sym:String,price:Double,pct:Double,key:String){
        if(r.lastFiredKey!=key) out.add(Fired(r,title,msg,sym,price,pct,key)) }
      if(r.scope=="PORTFOLIO"){
        when(r.type){
          "PCT_UP" -> if(state.pnlPct >= r.threshold) fire("Tawfiq CryptoSignal",
            "Your portfolio has increased by ${fmtUsd(state.pnl)} (${fmtPct(state.pnlPct)}). Current value is ${fmtUsd(state.total)}.",
            "PORTFOLIO",state.total,state.pnlPct,"PUP:${r.threshold}:${(state.pnlPct*2).toInt()}")
          "PCT_DOWN" -> if(state.pnlPct <= -r.threshold) fire("Tawfiq CryptoSignal",
            "Your portfolio has decreased by ${fmtUsd(abs(state.pnl))} (${fmtPct(state.pnlPct)}). Current value is ${fmtUsd(state.total)}.",
            "PORTFOLIO",state.total,state.pnlPct,"PDN:${r.threshold}:${(state.pnlPct*2).toInt()}")
          "TARGET_ABOVE" -> if(state.total >= r.threshold) fire("Tawfiq CryptoSignal",
            "Portfolio reached your target of ${fmtUsd(r.threshold)}. Current value is ${fmtUsd(state.total)}.","PORTFOLIO",state.total,state.pnlPct,"PTA:${r.threshold}")
          "TARGET_BELOW" -> if(state.total <= r.threshold) fire("Tawfiq CryptoSignal",
            "Portfolio fell below ${fmtUsd(r.threshold)}. Current value is ${fmtUsd(state.total)}.","PORTFOLIO",state.total,state.pnlPct,"PTB:${r.threshold}")
          "PNL_USD" -> if(abs(state.pnl) >= r.threshold) fire("Tawfiq CryptoSignal",
            "Your overall profit/loss is now ${fmtUsd(state.pnl)}. Current value is ${fmtUsd(state.total)}.","PORTFOLIO",state.total,state.pnlPct,"PNL:${r.threshold}:${if(state.pnl>=0)1 else -1}")
        }
      } else {
        val a = state.assets.firstOrNull{it.holding.symbol==r.scope} ?: continue
        if(!a.holding.alertEnabled) continue
        val q=a.quote ?: continue; val ref=a.holding.purchasePrice; if(ref==0.0) continue
        val pct=(q.price-ref)/ref*100.0
        when(r.type){
          "PCT_UP" -> if(pct >= r.threshold) fire("${r.scope} Alert",
            "${a.holding.name} has increased by ${fmtPct(pct)} since your purchase price. Your ${r.scope} holding is now worth ${fmtUsd(a.currentValue)}.",
            r.scope,q.price,pct,"CU:${r.threshold}:${(pct*2).toInt()}")
          "PCT_DOWN" -> if(pct <= -r.threshold) fire("${r.scope} Alert",
            "${a.holding.name} has fallen by ${fmtPct(pct)} since your purchase price. Your ${r.scope} holding is now worth ${fmtUsd(a.currentValue)}.",
            r.scope,q.price,pct,"CD:${r.threshold}:${(pct*2).toInt()}")
          "TARGET_ABOVE" -> if(q.price >= r.threshold) fire("${r.scope} Alert",
            "${a.holding.name} rose above your target ${fmtUsd(r.threshold)} (now ${fmtUsd(q.price)}).",r.scope,q.price,pct,"TA:${r.threshold}")
          "TARGET_BELOW" -> if(q.price <= r.threshold) fire("${r.scope} Alert",
            "${a.holding.name} fell below ${fmtUsd(r.threshold)} (now ${fmtUsd(q.price)}).",r.scope,q.price,pct,"TB:${r.threshold}")
          "NEW_DAY_HIGH" -> if(q.high24h>0 && q.price>=q.high24h*0.999) fire("${r.scope} Alert",
            "${a.holding.name} is at its 24h high of ${fmtUsd(q.high24h)}.",r.scope,q.price,pct,"NDH:${q.high24h.toInt()}")
          "NEW_DAY_LOW" -> if(q.low24h>0 && q.price<=q.low24h*1.001) fire("${r.scope} Alert",
            "${a.holding.name} is at its 24h low of ${fmtUsd(q.low24h)}.",r.scope,q.price,pct,"NDL:${q.low24h.toInt()}")
        }
      }
    }
    return out
  }
  fun fmtUsd(v:Double):String = (if(v<0) "-$" else "$") + "%,.2f".format(abs(v))
  fun fmtPct(v:Double):String = "%+.2f%%".format(v)
}
