package com.tawfiq.cryptosignal.util
import android.app.*; import android.content.*; import android.os.Build
import androidx.core.app.NotificationCompat; import com.tawfiq.cryptosignal.*
object Notify {
  const val CH_ALERTS="tawfiq_alerts"; const val CH_STATUS="tawfiq_status"
  fun ensureChannels(c:Context){
    if(Build.VERSION.SDK_INT>=26){ val nm=c.getSystemService(NotificationManager::class.java)
      nm.createNotificationChannel(NotificationChannel(CH_ALERTS,"Investment Alerts",NotificationManager.IMPORTANCE_HIGH).apply{description="Portfolio and coin price alerts"})
      nm.createNotificationChannel(NotificationChannel(CH_STATUS,"Status",NotificationManager.IMPORTANCE_LOW))
    }
  }
  fun send(c:Context, id:Int, title:String, body:String){
    ensureChannels(c)
    val intent = c.packageManager.getLaunchIntentForPackage(c.packageName)
    val pi = PendingIntent.getActivity(c,id,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val n = NotificationCompat.Builder(c,CH_ALERTS).setSmallIcon(R.mipmap.ic_launcher).setContentTitle(title)
      .setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
      .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(pi).build()
    try { c.getSystemService(NotificationManager::class.java).notify(id,n) } catch(_:SecurityException){}
  }
}
