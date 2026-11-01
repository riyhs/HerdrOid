package dev.riyaldi.herdroid

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.riyaldi.herdroid.ui.HerdrOidApp
import dev.riyaldi.herdroid.ui.MainViewModel
import dev.riyaldi.herdroid.ui.theme.HerdrOidTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (!BuildConfig.DEBUG) {
      window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
      if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false)
    }

    if (Build.VERSION.SDK_INT >= 31) {
      window.setHideOverlayWindows(true)
    }
    window.decorView.filterTouchesWhenObscured = true

    if (Build.VERSION.SDK_INT >= 33 &&
      checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
      requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
    }

    enableEdgeToEdge()
    val container = (application as HerdrOidApplication).container
    setContent {
      HerdrOidTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background,
        ) {
          val vm: MainViewModel = viewModel(
            factory = MainViewModel.Factory(container.repository),
          )
          HerdrOidApp(vm)
        }
      }
    }
  }
}
