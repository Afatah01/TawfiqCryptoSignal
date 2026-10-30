"""Tawfiq CryptoSignal backend for Render.com - public market-data proxy + cache.
No API keys, no trading, no user portfolio data stored server-side.
Endpoints: GET /health | GET /api/prices | GET /api/market | GET /api/ticker/{symbol}
"""
import os, time, httpx
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse
import os as _os
BINANCE="https://data-api.binance.vision"  # public market-data host
GECKO="https://api.coingecko.com"
SYMBOLS=["BTC","BNB","XRP"]
GECKO_IDS={"BTC":"bitcoin","BNB":"binancecoin","XRP":"ripple","ETH":"ethereum","SOL":"solana","DOGE":"dogecoin","USDT":"tether","USDC":"usd-coin"}
app=FastAPI(title="Tawfiq CryptoSignal API", version="1.0.0")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["GET"], allow_headers=["*"])
_static=_os.path.join(_os.path.dirname(__file__),"static")
if _os.path.isdir(_static): app.mount("/static", StaticFiles(directory=_static), name="static")
_cache={"prices":(0,None),"market":(0,None)}
def fresh(key,ttl=20):
    t,v=_cache.get(key,(0,None)); return v if v and time.time()-t<ttl else None
@app.get("/api")
def api_home(): return {"service":"Tawfiq CryptoSignal API","status":"live","docs":"/docs","endpoints":["/health","/api/prices","/api/market","/api/ticker/BTC"]}
@app.get("/")
def home(): return FileResponse(_os.path.join(_os.path.dirname(__file__),"static","index.html"))
@app.get("/health")
def health(): return {"status":"ok","service":"tawfiq-cryptosignal","ts":int(time.time()*1000)}
@app.get("/api/ticker/{symbol}")
async def ticker(symbol:str):
    symbol=symbol.upper().removesuffix("USDT")
    pair=f"{symbol}USDT"
    try:
        async with httpx.AsyncClient(timeout=12) as c:
            r=await c.get(f"{BINANCE}/api/v3/ticker/24hr",params={"symbol":pair}); r.raise_for_status(); d=r.json()
        return {"symbol":symbol,"price":float(d["lastPrice"]),"change24hPct":float(d["priceChangePercent"]),
                "change24hUsd":float(d["priceChange"]),"high24h":float(d["highPrice"]),"low24h":float(d["lowPrice"]),
                "volume24h":float(d.get("quoteVolume") or 0),"source":"Binance","ts":int(time.time()*1000)}
    except Exception:
        gid=GECKO_IDS.get(symbol)
        if not gid: return {"error":"unknown symbol"}
        async with httpx.AsyncClient(timeout=12) as c:
            r=await c.get(f"{GECKO}/api/v3/coins/markets",params={"vs_currency":"usd","ids":gid}); r.raise_for_status(); d=r.json()[0]
        return {"symbol":symbol,"price":d["current_price"],"change24hPct":d.get("price_change_percentage_24h") or 0,
                "change24hUsd":d.get("price_change_24h") or 0,"high24h":d.get("high_24h") or 0,"low24h":d.get("low_24h") or 0,
                "volume24h":d.get("total_volume") or 0,"source":"CoinGecko","ts":int(time.time()*1000)}
@app.get("/api/prices")
async def prices():
    if (v:=fresh("prices")) is not None: return v
    out={}
    for s in SYMBOLS:
        try: out[s]=await ticker(s)
        except Exception as e: out[s]={"symbol":s,"error":str(e)}
    _cache["prices"]=(time.time(),out); return out
@app.get("/api/market")
async def market():
    # ALL market data from Binance (real-time 24h tickers, USDT pairs), CoinGecko only as emergency fallback.
    if (v:=fresh("market",20)) is not None: return v
    NAMES={"BTC":"Bitcoin","ETH":"Ethereum","BNB":"BNB","XRP":"XRP","SOL":"Solana","DOGE":"Dogecoin","ADA":"Cardano","TRX":"TRON","AVAX":"Avalanche","LINK":"Chainlink","DOT":"Polkadot","MATIC":"Polygon","LTC":"Litecoin","SHIB":"Shiba Inu","BCH":"Bitcoin Cash","NEAR":"NEAR Protocol","UNI":"Uniswap","XLM":"Stellar","ETC":"Ethereum Classic","FIL":"Filecoin","APT":"Aptos","ARB":"Arbitrum","OP":"Optimism","INJ":"Injective","SUI":"Sui","PEPE":"Pepe","WIF":"dogwifhat","FET":"Artificial Superintelligence Alliance","RENDER":"Render","ATOM":"Cosmos"}
    try:
        async with httpx.AsyncClient(timeout=15) as c:
            r=await c.get(f"{BINANCE}/api/v3/ticker/24hr"); r.raise_for_status(); all_t=r.json()
        rows=[]
        for t in all_t:
            sym=t.get("symbol","")
            if not sym.endswith("USDT"): continue
            base=sym[:-4]
            if base not in NAMES: continue
            rows.append({"id":base.lower(),"symbol":base.lower(),"name":NAMES[base],
              "current_price":float(t["lastPrice"]),"price_change_percentage_24h":float(t["priceChangePercent"]),
              "price_change_24h":float(t["priceChange"]),"high_24h":float(t["highPrice"]),"low_24h":float(t["lowPrice"]),
              "total_volume":float(t.get("quoteVolume") or 0),"source":"Binance"})
        rows.sort(key=lambda x:x["total_volume"], reverse=True)
        rows.insert(0,{"id":"usdt","symbol":"usdt","name":"Tether","current_price":1.0,"price_change_percentage_24h":0.0,
                       "price_change_24h":0.0,"high_24h":1.0,"low_24h":1.0,"total_volume":0.0,"source":"Pegged (display)"})
        _cache["market"]=(time.time(),rows); return rows
    except Exception:
        try:
            async with httpx.AsyncClient(timeout=15) as c:
                r=await c.get(f"{GECKO}/api/v3/coins/markets",params={"vs_currency":"usd","per_page":50,"page":1,"price_change_percentage":"24h"})
                r.raise_for_status(); data=r.json()
            for d in data: d["source"]="CoinGecko (Binance unavailable - fallback)"
            _cache["market"]=(time.time(),data); return data
        except Exception:
            return []

# ---- Tawfiq AI Market Intelligence ----
from ai_engine import analyse_symbol, openai_narrative, local_answer, AI_SYMBOLS
import asyncio as _asyncio
_ai_cache = {"analysis": (0, None), "ask": {}}
_ai_hits = {}
def _rate_ok(ip, limit=20, window=600):
    now = time.time(); arr = [t for t in _ai_hits.get(ip, []) if now-t < window]
    if len(arr) >= limit: return False
    arr.append(now); _ai_hits[ip] = arr; return True

@app.get("/api/ai/status")
def ai_status():
    return {"openaiConfigured": bool(os.environ.get("OPENAI_API_KEY")),
            "mode": "OpenAI + Binance indicators" if os.environ.get("OPENAI_API_KEY") else "Binance indicators (add OPENAI_API_KEY on Render for OpenAI narratives)",
            "model": os.environ.get("OPENAI_MODEL", "gpt-4o-mini"),
            "symbols": AI_SYMBOLS, "note": "Analysis only. No trading. Not financial advice."}

@app.get("/api/ai/analysis")
async def ai_analysis(interval: str = "1h"):
    if interval not in ("5m","15m","1h","4h","1d","1w"): interval = "1h"
    t, v = _ai_cache["analysis"]
    if v and time.time()-t < 300 and v.get("interval") == interval: return v
    results = []
    for s in AI_SYMBOLS:
        try: results.append(await analyse_symbol(s, interval))
        except Exception as e: results.append({"symbol": s, "error": str(e), "signal": "WAIT"})
    alerts = []
    for a in results:
        if "error" in a: continue
        if abs(a.get("momentum24hPct") or 0) >= 5: alerts.append({"symbol": a["symbol"], "type": "Significant price move", "detail": f"{a['momentum24hPct']:+.2f}% momentum, signal {a['signal']}", "risk": a["risk"], "ts": a["ts"]})
        if (a.get("volumeRatio") or 0) >= 1.8: alerts.append({"symbol": a["symbol"], "type": "Unusual volume", "detail": f"Volume {a['volumeRatio']}x average", "risk": a["risk"], "ts": a["ts"]})
        if a.get("rsi") is not None and (a["rsi"] >= 70 or a["rsi"] <= 30): alerts.append({"symbol": a["symbol"], "type": "RSI extreme", "detail": f"RSI {a['rsi']}", "risk": a["risk"], "ts": a["ts"]})
    narrative = await openai_narrative("Give a short market overview (max 120 words) from this real Binance indicator data: " + str([{k: a.get(k) for k in ('symbol','price','signal','rsi','direction','risk')} for a in results])) if os.environ.get("OPENAI_API_KEY") else None
    out = {"analyses": results, "alerts": alerts, "narrative": narrative,
           "generatedAt": int(time.time()*1000), "interval": interval,
           "provider": "OpenAI + Binance" if narrative else "Binance indicators (OpenAI not configured)"}
    _ai_cache["analysis"] = (time.time(), out); return out

@app.get("/api/ai/opportunities")
async def ai_opportunities():
    data = await ai_analysis()
    ok = [a for a in data["analyses"] if "error" not in a]
    return {"buy": [a for a in ok if a["signal"]=="BUY"], "sell": [a for a in ok if a["signal"]=="SELL"],
            "hold": [a for a in ok if a["signal"]=="HOLD"], "wait": [a for a in ok if a["signal"]=="WAIT"],
            "strongMomentum": sorted(ok, key=lambda x: x["score"], reverse=True)[:3],
            "unusualVolume": sorted(ok, key=lambda x: x["volumeRatio"], reverse=True)[:3],
            "generatedAt": data["generatedAt"]}

from fastapi import Request as _Request
from pydantic import BaseModel as _BM
class _Ask(_BM): question: str
@app.post("/api/ai/ask")
async def ai_ask(body: _Ask, request: _Request):
    ip = request.client.host if request.client else "unknown"
    if not _rate_ok(ip): return {"answer": "Rate limit reached - please wait a few minutes.", "ts": int(time.time()*1000)}
    q = (body.question or "").strip()[:500]
    if not q: return {"answer": "Please ask a question.", "ts": int(time.time()*1000)}
    data = await ai_analysis()
    prices = {a["symbol"]: a.get("price") for a in data["analyses"]}
    if os.environ.get("OPENAI_API_KEY"):
        ans = await openai_narrative(f"User question: {q}\nReal Binance indicator data: {str([{k: a.get(k) for k in ('symbol','price','signal','rsi','ema20','ema50','support','resistance','volumeRatio','risk','portfolio')} for a in data['analyses']])}\nPortfolio baseline: BTC $500/0.006198@80642.26, BNB $300/0.398894@752.08, XRP $200/151.52@1.32, USDT $200 emergency reserve (separate). Answer in simple language, show facts vs interpretation.")
        if ans: return {"answer": ans, "ts": int(time.time()*1000), "provider": "OpenAI + Binance", "dataTs": data["generatedAt"]}
    r = local_answer(q, data["analyses"], prices); r["provider"] = "Binance indicators (add OPENAI_API_KEY for OpenAI chat)"; r["dataTs"] = data["generatedAt"]; return r
