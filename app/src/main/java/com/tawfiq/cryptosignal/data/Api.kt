package com.tawfiq.cryptosignal.data
import com.google.gson.annotations.SerializedName
import retrofit2.http.*
import retrofit2.Retrofit; import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient; import java.util.concurrent.TimeUnit
data class BinanceTicker(val symbol:String?, val lastPrice:String?, val priceChange:String?, val priceChangePercent:String?,
  val highPrice:String?, val lowPrice:String?, val volume:String?, val quoteVolume:String?)
data class GeckoMarket(val id:String?, val symbol:String?, val name:String?,
  @SerializedName("current_price") val currentPrice:Double?, @SerializedName("price_change_percentage_24h") val pct24h:Double?,
  @SerializedName("price_change_24h") val change24h:Double?, @SerializedName("high_24h") val high24h:Double?,
  @SerializedName("low_24h") val low24h:Double?, @SerializedName("total_volume") val volume:Double?)
interface BinanceApi {
  @GET("api/v3/ticker/24hr") suspend fun ticker(@Query("symbol") s:String): BinanceTicker
  @GET("api/v3/ticker/24hr") suspend fun allTickers(): List<BinanceTicker>
}
interface GeckoApi {
  @GET("api/v3/coins/markets") suspend fun markets(@Query("vs_currency") vs:String="usd",
    @Query("ids") ids:String, @Query("price_change_percentage") pc:String="24h"): List<GeckoMarket>
  @GET("api/v3/coins/markets") suspend fun topMarkets(@Query("vs_currency") vs:String="usd",
    @Query("per_page") perPage:Int=50, @Query("page") page:Int=1, @Query("price_change_percentage") pc:String="24h"): List<GeckoMarket>
}
interface BackendApi {
  @GET("api/prices") suspend fun prices(): Map<String, QuoteDto>
  @GET("api/market") suspend fun market(): List<GeckoMarket>
}
data class QuoteDto(val symbol:String?, val price:Double?, val change24hPct:Double?, val change24hUsd:Double?,
  val high24h:Double?, val low24h:Double?, val volume24h:Double?, val source:String?, val ts:Long?)
object Net {
  private val client = OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS)
    .addInterceptor{ c -> c.proceed(c.request().newBuilder().header("User-Agent","TawfiqCryptoSignal/1.0").build()) }.build()
  private fun <T> make(base:String, cls:Class<T>):T = Retrofit.Builder().baseUrl(base).client(client)
    .addConverterFactory(GsonConverterFactory.create()).build().create(cls)
  val binance: BinanceApi get() = make("https://data-api.binance.vision/", BinanceApi::class.java)
  val gecko: GeckoApi get() = make("https://api.coingecko.com/", GeckoApi::class.java)
  fun backend(url:String): BackendApi? = try { if(url.isBlank()) null else make(if(url.endsWith("/")) url else "$url/", BackendApi::class.java) } catch(e:Exception){ null }
  fun wsClient(): OkHttpClient = client
}
