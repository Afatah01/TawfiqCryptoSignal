package com.tawfiq.cryptosignal.work
import android.content.Context
import androidx.work.CoroutineWorker; import androidx.work.WorkerParameters
import com.tawfiq.cryptosignal.BuildConfig; import com.tawfiq.cryptosignal.data.*
import com.tawfiq.cryptosignal.util.Notify
// Background: ~every 15 min (Android minimum; battery optimisation may delay). Real prices only.
class PriceCheckWorker(c:Context,p:WorkerParameters): CoroutineWorker(c,p){
  override suspend fun doWork(): Result {
    return try {
      val db=AppDb.get(applicationContext); val dao=db.dao(); AppDb.seedIfEmpty(dao)
      val holdings=dao.holdings(); if(holdings.isEmpty()) return Result.success()
      val repo=Repository(dao, backendUrl(applicationContext))
      val (quotes,err)=repo.fetchPortfolioQuotes(holdings)
      if(quotes.isEmpty()) return Result.retry()
      val state=PortfolioState(holdings.map{AssetState(it,quotes[it.symbol])},System.currentTimeMillis(),quotes.values.firstOrNull()?.source?:"")
      dao.addSnapshot(Snapshot(ts=System.currentTimeMillis(),totalValue=state.total,cryptoValue=state.cryptoValue,pnl=state.pnl,
        btc=state.assets.firstOrNull{it.holding.symbol=="BTC"}?.currentValue?:0.0,
        bnb=state.assets.firstOrNull{it.holding.symbol=="BNB"}?.currentValue?:0.0,
        xrp=state.assets.firstOrNull{it.holding.symbol=="XRP"}?.currentValue?:0.0))
      AlertsEngine.evaluate(state,dao.enabledRules()).forEach{ f ->
        Notify.send(applicationContext,(System.currentTimeMillis()%100000).toInt()+f.rule.id.toInt()*1000,f.title,f.message)
        dao.addHistory(AlertHistory(ts=System.currentTimeMillis(),title=f.title,message=f.message,symbol=f.symbol,triggerPrice=f.price,pctChange=f.pct))
        dao.updateRule(f.rule.copy(lastFiredAt=System.currentTimeMillis(),lastFiredKey=f.key))
      }
      Result.success()
    } catch(e:Exception){ Result.retry() }
  }
  companion object { fun backendUrl(c:Context):String =
    c.getSharedPreferences("tawfiq_prefs",Context.MODE_PRIVATE).getString("backend_url",BuildConfig.DEFAULT_BACKEND_URL) ?: BuildConfig.DEFAULT_BACKEND_URL }
}
