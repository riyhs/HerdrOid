package dev.riyaldi.herdroid.benchmarkapp

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
import dev.riyaldi.herdroid.BuildConfig
import dev.riyaldi.herdroid.domain.model.OpenTerminal
import dev.riyaldi.herdroid.domain.model.TerminalEvent
import dev.riyaldi.herdroid.domain.model.TerminalHandle
import dev.riyaldi.herdroid.ui.screens.TerminalScreen
import dev.riyaldi.herdroid.ui.theme.HerdrOidTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class BenchmarkTerminalActivity : ComponentActivity() {
  private lateinit var handle: BenchmarkTerminalHandle

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
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    val workload = intent.getStringExtra(EXTRA_WORKLOAD) ?: WORKLOAD_STREAM
    val autoDetectUrls = intent.getBooleanExtra(EXTRA_AUTO_DETECT_URLS, true)
    val chunkMs = intent.getLongExtra(EXTRA_CHUNK_MS, 16L).coerceAtLeast(1L)
    configureRefreshRate(intent.getFloatExtra(EXTRA_REFRESH_HZ, 60f))
    enableEdgeToEdge()
    handle = BenchmarkTerminalHandle(workload, chunkMs)
    setContent {
      HerdrOidTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background,
        ) {
          TerminalScreen(
            terminal = OpenTerminal(
              machineId = "benchmark",
              address = "benchmark",
              title = "Terminal benchmark",
              handle = handle,
            ),
            machineName = "Benchmark host",
            onBack = { finish() },
            autoDetectUrls = autoDetectUrls,
          )
        }
      }
    }
  }

  override fun onDestroy() {
    handle.close()
    super.onDestroy()
  }

  @Suppress("DEPRECATION")
  private fun configureRefreshRate(refreshHz: Float) {
    val display = windowManager.defaultDisplay
    val currentMode = display.mode
    val mode = display.supportedModes.firstOrNull {
      abs(it.refreshRate - refreshHz) < 0.1f &&
        it.physicalWidth == currentMode.physicalWidth &&
        it.physicalHeight == currentMode.physicalHeight
    }
    window.attributes = window.attributes.apply {
      preferredRefreshRate = refreshHz
      if (mode != null) preferredDisplayModeId = mode.modeId
    }
  }

  private companion object {
    const val EXTRA_WORKLOAD = "workload"
    const val EXTRA_AUTO_DETECT_URLS = "autoDetectUrls"
    const val EXTRA_CHUNK_MS = "chunkMs"
    const val EXTRA_REFRESH_HZ = "refreshHz"
    const val WORKLOAD_STREAM = "stream"
  }
}

private class BenchmarkTerminalHandle(
  private val workload: String,
  private val chunkMs: Long,
) : TerminalHandle {
  private val eventsChannel = Channel<TerminalEvent>(capacity = 64)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val job = scope.launch { emitWorkload() }

  override val events: Flow<TerminalEvent> = eventsChannel.receiveAsFlow()

  override fun write(bytes: ByteArray) = Unit

  override fun acknowledge(bytes: Int) = Unit

  override fun resize(cols: Int, rows: Int) = Unit

  override fun close() {
    job.cancel()
    eventsChannel.close()
    scope.cancel()
  }

  private suspend fun emitWorkload() {
    when (workload) {
      "idle" -> {
        eventsChannel.send(TerminalEvent.Data("\u001b[?25h\u001b[?12h".toByteArray()))
        repeat(2) { eventsChannel.send(TerminalEvent.Data(chunks[it])) }
        awaitCancellation()
      }
      "scroll" -> {
        repeat(80) { eventsChannel.send(TerminalEvent.Data(chunks[it % chunks.size])) }
        awaitCancellation()
      }
      "ime" -> {
        repeat(8) { eventsChannel.send(TerminalEvent.Data(chunks[it % chunks.size])) }
        awaitCancellation()
      }
      else -> {
        var index = 0
        while (scope.isActive) {
          eventsChannel.send(TerminalEvent.Data(chunks[index % chunks.size]))
          index++
          delay(chunkMs)
        }
      }
    }
  }

  private companion object {
    const val LINES_PER_CHUNK = 48
    val COLORS = intArrayOf(31, 32, 33, 34, 35, 36, 37, 90, 92, 96)
    val chunks: List<ByteArray> = List(16) { buildChunk(it) }

    fun buildChunk(index: Int): ByteArray = buildString(4 * 1024) {
      repeat(LINES_PER_CHUNK) { line ->
        val number = index * LINES_PER_CHUNK + line
        append("\u001b[")
        append(COLORS[number % COLORS.size])
        append('m')
        append(number.toString().padStart(6, '0'))
        append("  agent output · streaming terminal payload  ")
        if (number % 8 == 0) append("┌─┬─┐ │ └─┴─┘  ")
        if (number % 16 == 0) append("✓  ")
        append("\u001b[0m\r\n")
      }
    }.toByteArray()
  }
}
