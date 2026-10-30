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
