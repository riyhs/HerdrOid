package dev.riyaldi.herdroid

import android.app.Application
import dev.riyaldi.herdroid.di.AppContainer

class HerdrOidApplication : Application() {
  val container: AppContainer by lazy { AppContainer(this) }

  override fun onCreate() {
    super.onCreate()
    container
  }
}
