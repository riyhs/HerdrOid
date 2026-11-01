package dev.riyaldi.herdroid.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

class HerdrWatchService : Service() {
  override fun onCreate() {
    super.onCreate()
    Notifications.ensureChannels(this)
    startForeground(
      Notifications.WATCH_ID,
      Notifications.watch(this, "Connecting to your hc host…"),
    )
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Watching your hc host"
    startForeground(Notifications.WATCH_ID, Notifications.watch(this, text))
    return START_NOT_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  companion object {
    const val EXTRA_TEXT = "text"
  }
}
