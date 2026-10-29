package com.tawfiq.cryptosignal
import com.tawfiq.cryptosignal.data.*
import org.junit.Assert.*; import org.junit.Test
class PortfolioCalcTest {
  private fun q(s:String,p:Double)=Quote(s,s+"USDT",p,0.0,0.0,p,p,0.0,"Test",1L)
  @Test fun baseline1200_andPnl() {
    val hs=listOf(Holding("BTC","Bitcoin",0.006198,80642.26,500.0),Holding("BNB","BNB",0.398894,752.08,300.0),
      Holding("XRP","XRP",151.52,1.32,200.0),Holding("USDT","Tether",200.0,1.0,200.0,isReserve=true))
    val st=PortfolioState(listOf(AssetState(hs[0],q("BTC",80642.26*1.1)),AssetState(hs[1],q("BNB",752.08*1.1)),
      AssetState(hs[2],q("XRP",1.32*1.1)),AssetState(hs[3],q("USDT",1.0))),1L,"Test")
    assertEquals(1200.0, st.initialTotal, 0.001); assertEquals(1000.0, st.initialCrypto, 0.001)
    // NOTE: supplied quantities are rounded (e.g. 0.006198 BTC), so qty x price = $1,199.81 not exactly $1,200 at purchase prices.
    assertEquals(1300.0, st.total, 1.0); assertEquals(100.0, st.pnl, 1.0); assertEquals(100.0/1200*100, st.pnlPct, 0.1)
    assertEquals(200.0, st.reserveValue, 0.001)
  }
  @Test fun alertsPctUpFiresOnceKeyChanges() {
    val hs=listOf(Holding("BTC","Bitcoin",1.0,100.0,100.0),Holding("USDT","Tether",200.0,1.0,200.0,isReserve=true))
    val st=PortfolioState(listOf(AssetState(hs[0],q("BTC",110.0)),AssetState(hs[1],q("USDT",1.0))),1L,"Test")
    val fired=AlertsEngine.evaluate(st,listOf(AlertRule(scope="BTC",type="PCT_UP",threshold=3.0)),now=10_000_000L)
    assertEquals(1,fired.size); assertTrue(fired[0].message.contains("10.00"))
  }
}
