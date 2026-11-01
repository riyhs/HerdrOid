package dev.riyaldi.herdroid.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.riyaldi.herdroid.MainActivity

object Notifications {
  const val WATCH_CHANNEL = "herdroid-watch"
  const val NEEDS_CHANNEL = "herdroid-needs-you"
  const val WATCH_ID = 41

  fun ensureChannels(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
      NotificationChannel(
        WATCH_CHANNEL,
        "Connection watcher",
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = "Keeps the SSH connection alive while HerdrOid watches agents."
        setShowBadge(false)
      },
    )
    manager.createNotificationChannel(
      NotificationChannel(
        NEEDS_CHANNEL,
        "Agent needs you",
        NotificationManager.IMPORTANCE_HIGH,
      ).apply {
        description = "An agent is blocked on a question or permission."
      },
    )
  }

  fun watch(context: Context, text: String): Notification =
    NotificationCompat.Builder(context, WATCH_CHANNEL)
      .setSmallIcon(android.R.drawable.stat_notify_chat)
      .setContentTitle("HerdrOid is watching")
      .setContentText(text)
      .setContentIntent(openApp(context))
      .setOngoing(true)
      .setSilent(true)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .build()

  fun postNeedsYou(context: Context, id: Int, title: String, text: String) {
    if (Build.VERSION.SDK_INT >= 33 &&
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
      PackageManager.PERMISSION_GRANTED
    ) {
      return
    }
    val manager = NotificationManagerCompat.from(context)
    if (!manager.areNotificationsEnabled()) return
    ensureChannels(context)
    val notification = NotificationCompat.Builder(context, NEEDS_CHANNEL)
      .setSmallIcon(android.R.drawable.stat_notify_chat)
      .setContentTitle(title)
      .setContentText(text)
      .setStyle(NotificationCompat.BigTextStyle().bigText(text))
      .setContentIntent(openApp(context))
      .setAutoCancel(true)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .setCategory(NotificationCompat.CATEGORY_MESSAGE)
      .build()
    runCatching { manager.notify(id, notification) }
  }

  private fun openApp(context: Context): PendingIntent {
    val intent = Intent(context, MainActivity::class.java)
      .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    return PendingIntent.getActivity(
      context,
      0,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }
}
