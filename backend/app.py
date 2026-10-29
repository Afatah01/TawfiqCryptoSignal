"""Tawfiq CryptoSignal backend for Render.com - public market-data proxy + cache.
No API keys, no trading, no user portfolio data stored server-side.
Endpoints: GET /health | GET /api/prices | GET /api/market | GET /api/ticker/{symbol}
"""
import os, time, httpx
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
BINANCE="https://data-api.binance.vision"  # public market-data host
GECKO="https://api.coingecko.com"
SYMBOLS=["BTC","BNB","XRP"]
GECKO_IDS={"BTC":"bitcoin","BNB":"binancecoin","XRP":"ripple","ETH":"ethereum","SOL":"solana","DOGE":"dogecoin","USDT":"tether","USDC":"usd-coin"}
app=FastAPI(title="Tawfiq CryptoSignal API", version="1.0.0")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["GET"], allow_headers=["*"])
_cache={"prices":(0,None),"market":(0,None)}
def fresh(key,ttl=20):
    t,v=_cache.get(key,(0,None)); return v if v and time.time()-t<ttl else None
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
    if (v:=fresh("market",60)) is not None: return v
    try:
        async with httpx.AsyncClient(timeout=15) as c:
            r=await c.get(f"{GECKO}/api/v3/coins/markets",params={"vs_currency":"usd","per_page":50,"page":1,"price_change_percentage":"24h"})
            r.raise_for_status(); data=r.json()
    except Exception:
        data=[]
    _cache["market"]=(time.time(),data); return data
if __name__=="__main__":
    import uvicorn; uvicorn.run(app,host="0.0.0.0",port=int(os.environ.get("PORT","10000")))
