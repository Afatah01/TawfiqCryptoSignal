"""Tawfiq AI Engine - real Binance klines -> technical indicators -> BUY/SELL/HOLD/WAIT.
Local analysis ALWAYS works (no key needed). If OPENAI_API_KEY is set on the server,
OpenAI (OPENAI_MODEL, default gpt-4o-mini) writes the narrative on top of these real numbers.
No trading, no predictions presented as facts. Cached + rate-limited by caller.
"""
import os, math, time, httpx
BINANCE = "https://data-api.binance.vision"
AI_SYMBOLS = ["BTC","ETH","BNB","XRP","SOL","ADA","DOGE","LINK","AVAX","NEAR"]
PORTFOLIO = {"BTC": {"qty":0.006198,"buy":80642.26,"inv":500.0},
             "BNB": {"qty":0.398894,"buy":752.08,"inv":300.0},
             "XRP": {"qty":151.52,"buy":1.32,"inv":200.0}}  # USDT $200 reserve kept separate, never analysed as crypto

def ema(vals, n):
    if len(vals) < n: return None
    k = 2/(n+1); e = sum(vals[:n])/n
    for v in vals[n:]: e = v*k + e*(1-k)
    return e

def rsi(closes, n=14):
    if len(closes) < n+1: return None
    gains, losses = [], []
    for i in range(1, len(closes)):
        d = closes[i]-closes[i-1]; gains.append(max(d,0)); losses.append(max(-d,0))
    ag, al = sum(gains[-n:])/n, sum(losses[-n:])/n
    if al == 0: return 100.0
    return 100 - 100/(1 + ag/al)

def macd(closes):
    if len(closes) < 35: return (None, None)
    # EMA series
    def series(n):
        k=2/(n+1); out=[]; e=sum(closes[:n])/n; out.append(e)
        for v in closes[n:]: e=v*k+e*(1-k); out.append(e)
        return out
    s12, s26 = series(12), series(26)
    m = [a-b for a,b in zip(s12[-len(s26):], s26)]
    sig = ema(m, 9) if len(m) >= 9 else None
    return (m[-1], sig)

async def klines(symbol, interval="1h", limit=220):
    async with httpx.AsyncClient(timeout=15) as c:
        r = await c.get(f"{BINANCE}/api/v3/klines", params={"symbol": symbol+"USDT", "interval": interval, "limit": limit})
        r.raise_for_status(); return r.json()

async def analyse_symbol(symbol, interval="1h"):
    k = await klines(symbol, interval)
    closes = [float(x[4]) for x in k]; vols = [float(x[7]) for x in k]  # quote volume
    highs = [float(x[2]) for x in k]; lows = [float(x[3]) for x in k]
    price = closes[-1]
    e20, e50, e200 = ema(closes,20), ema(closes,50), ema(closes,200)
    r = rsi(closes); m, msig = macd(closes)
    n = min(20, len(closes)); mid = sum(closes[-n:])/n
    sd = math.sqrt(sum((x-mid)**2 for x in closes[-n:])/n) if n > 1 else 0
    boll_up, boll_dn = mid+2*sd, mid-2*sd
    vol_now, vol_avg = vols[-1], (sum(vols[-21:-1])/20 if len(vols) > 21 else vols[-1])
    vol_ratio = (vol_now/vol_avg) if vol_avg else 1.0
    support = min(lows[-50:]); resistance = max(highs[-50:])
    mom_24 = (closes[-1]/closes[-25]-1)*100 if len(closes) > 25 else 0.0
    vola = (sd/price*100) if price else 0.0
    score, reasons = 0, []
    def add(cond, pts, txt):
        nonlocal score
        if cond: score += pts; reasons.append(txt)
    add(e20 and price > e20, 1, f"Price above EMA20 ({e20:,.4g}) - short-term uptrend.")
    add(e20 and price < e20, -1, f"Price below EMA20 ({e20:,.4g}) - short-term downtrend.")
    add(e50 and e20 and e20 > e50, 1, "EMA20 above EMA50 - trend structure bullish.")
    add(e50 and e20 and e20 < e50, -1, "EMA20 below EMA50 - trend structure bearish.")
    if r is not None:
        if r < 30: score += 1; reasons.append(f"RSI {r:.0f} - oversold zone, bounce possible (not guaranteed).")
        elif r > 70: score -= 1; reasons.append(f"RSI {r:.0f} - overbought zone, pullback risk.")
        else: reasons.append(f"RSI {r:.0f} - neutral momentum.")
    if m is not None and msig is not None:
        add(m > msig, 1, "MACD above signal - bullish momentum.")
        add(m < msig, -1, "MACD below signal - bearish momentum.")
    add(vol_ratio > 1.8, 1 if mom_24 > 0 else -1, f"Unusual volume: {vol_ratio:.1f}x the 20-period average.")
    add(price >= resistance*0.995, 1, f"Testing 50-period resistance near {resistance:,.4g}.")
    add(price <= support*1.005, 0, f"Near 50-period support near {support:,.4g} - watch for reaction.")
    if score >= 3: signal = "BUY"
    elif score == 2: signal = "HOLD" if mom_24 > 0 else "WAIT"
    elif score <= -3: signal = "SELL"
    elif score == -2: signal = "WAIT"
    elif score in (-1, 0, 1): signal = "HOLD" if symbol in PORTFOLIO else "WAIT"
    else: signal = "WAIT"
    if r is None or e50 is None: signal, reasons = "WAIT", reasons + ["INSUFFICIENT MARKET CONFIRMATION - WAIT FOR MORE DATA."]
    confidence = min(85, 45 + abs(score)*9 + (10 if vol_ratio > 1.5 else 0))
    risk = "High" if vola > 3 else ("Moderate" if vola > 1.2 else "Low")
    atr_like = sd if sd else price*0.02
    out = {"symbol": symbol, "price": price, "signal": signal, "score": score,
      "direction": "Bullish" if score > 1 else ("Bearish" if score < -1 else "Sideways"),
      "rsi": round(r,1) if r else None,
      "ema20": e20, "ema50": e50, "ema200": e200, "macd": m, "macdSignal": msig,
      "bollingerUpper": boll_up, "bollingerLower": boll_dn,
      "support": support, "resistance": resistance, "volumeRatio": round(vol_ratio,2),
      "momentum24hPct": round(mom_24,2), "volatilityPct": round(vola,2),
      "entryRange": [round(price-0.5*atr_like,6), round(price+0.2*atr_like,6)] if signal=="BUY" else None,
      "target": round(price+2*atr_like,6) if signal in ("BUY","HOLD") else None,
      "stopLoss": round(price-1.5*atr_like,6) if signal in ("BUY","SELL") else None,
      "risk": risk, "confidencePct": confidence, "reasons": reasons,
      "timeframe": interval, "ts": int(time.time()*1000), "source": "Binance klines"}
    if symbol in PORTFOLIO:
        p = PORTFOLIO[symbol]; val = p["qty"]*price
        out["portfolio"] = {"qty": p["qty"], "buyPrice": p["buy"], "invested": p["inv"],
                            "value": round(val,2), "pnl": round(val-p["inv"],2),
                            "pnlPct": round((val-p["inv"])/p["inv"]*100,2)}
    return out

async def openai_narrative(prompt):
    key = os.environ.get("OPENAI_API_KEY")
    if not key: return None
    model = os.environ.get("OPENAI_MODEL", "gpt-4o-mini")
    try:
        async with httpx.AsyncClient(timeout=45) as c:
            r = await c.post("https://api.openai.com/v1/chat/completions",
                headers={"Authorization": f"Bearer {key}"},
                json={"model": model, "temperature": 0.3, "max_tokens": 500,
                      "messages": [{"role":"system","content":"You are Tawfiq AI, a cautious crypto market analyst. Use ONLY the supplied real Binance indicator data. Never promise profits. Clearly separate facts from interpretation. Simple language."},
                                   {"role":"user","content":prompt}]})
            r.raise_for_status(); return r.json()["choices"][0]["message"]["content"]
    except Exception:
        return None

def local_answer(question, analyses, prices):
    q = question.lower(); now = int(time.time()*1000)
    def fmt(a): return f"{a['symbol']}: ${a['price']:,.4g} - {a['signal']} ({a['direction']}, RSI {a['rsi']}, confidence {a['confidencePct']}%, risk {a['risk']}). " + " ".join(a["reasons"][:3])
    if "portfolio" in q or "my investment" in q or "performing" in q:
        tot_inv, tot_val, lines = 1200.0, 200.0, []
        for s,p in PORTFOLIO.items():
            a = next((x for x in analyses if x["symbol"]==s), None)
            if a: v=p["qty"]*a["price"]; tot_val+=v; lines.append(f"{s}: invested ${p['inv']:.0f} -> ${v:.2f} ({(v-p['inv'])/p['inv']*100:+.2f}%), signal {a['signal']}.")
        return {"answer": "Your portfolio (incl. $200 USDT emergency reserve, kept separate): $" + f"{tot_val:,.2f} vs $1,200 ({(tot_val-1200)/1200*100:+.2f}%). " + " ".join(lines) + " This is analysis, not financial advice.", "ts": now}
    for s in ["BTC","ETH","BNB","XRP","SOL","ADA","DOGE"]:
        if s.lower() in q or {"bitcoin":"BTC","ethereum":"ETH"}.get(q, "") == s:
            a = next((x for x in analyses if x["symbol"]==s), None)
            if a: return {"answer": fmt(a) + " Facts are Binance data above; the signal is AI interpretation, not a guarantee.", "ts": now}
    if "momentum" in q or "strongest" in q:
        a = max(analyses, key=lambda x: x["score"]); return {"answer": f"Strongest current momentum by indicator score: {fmt(a)}", "ts": now}
    if "volume" in q or "unusual" in q:
        a = max(analyses, key=lambda x: x["volumeRatio"]); return {"answer": f"Highest volume vs its average: {fmt(a)} Volume ratio {a['volumeRatio']}x.", "ts": now}
    if "risk" in q:
        return {"answer": "Main risks: market volatility (see each coin's volatility %), concentration - BTC is 50% of your $1,000 crypto investment, and crypto assets can fall quickly. Your $200 USDT reserve is separate and not invested. " + " ".join(fmt(a) for a in analyses if a["symbol"] in PORTFOLIO), "ts": now}
    if "compare" in q:
        return {"answer": "Comparison (live Binance indicators): " + " ".join(fmt(a) for a in analyses if a["symbol"] in ("BTC","BNB","XRP")), "ts": now}
    return {"answer": "Live snapshot: " + " ".join(fmt(a) for a in analyses[:4]) + " Ask me about a specific coin, your portfolio, momentum, volume or risk.", "ts": now}
