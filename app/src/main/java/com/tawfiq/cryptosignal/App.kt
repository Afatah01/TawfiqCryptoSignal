package com.tawfiq.cryptosignal
import android.app.Application
import androidx.work.*; import com.tawfiq.cryptosignal.work.PriceCheckWorker; import java.util.concurrent.TimeUnit
class App: Application(){
  override fun onCreate(){ super.onCreate()
    // WorkManager minimum periodic interval is 15 min - background alerts are checked on this schedule.
    val req = PeriodicWorkRequestBuilder<PriceCheckWorker>(15,TimeUnit.MINUTES)
      .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,10,TimeUnit.MINUTES).build()
    WorkManager.getInstance(this).enqueueUniquePeriodicWork("tawfiq-price-checks",ExistingPeriodicWorkPolicy.KEEP,req)
  }
}
