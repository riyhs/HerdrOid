package dev.riyaldi.herdroid.benchmark

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class TerminalBenchmark {
  @get:Rule val benchmarkRule = MacrobenchmarkRule()

  private val iterations =
    InstrumentationRegistry.getArguments().getString(ARG_ITERATIONS)?.toIntOrNull() ?: DEFAULT_ITERATIONS
  private val warmupMs =
    InstrumentationRegistry.getArguments().getString(ARG_WARMUP_MS)?.toLongOrNull() ?: DEFAULT_WARMUP_MS
  private val measureMs =
    InstrumentationRegistry.getArguments().getString(ARG_MEASURE_MS)?.toLongOrNull() ?: DEFAULT_MEASURE_MS
  private val autoDetectUrls =
    InstrumentationRegistry.getArguments().getString(ARG_AUTO_DETECT_URLS)?.toBooleanStrictOrNull() ?: true
  private val refreshHz =
    InstrumentationRegistry.getArguments().getString(ARG_REFRESH_HZ)?.toFloatOrNull() ?: DEFAULT_REFRESH_HZ
  private val chunkMs =
    InstrumentationRegistry.getArguments().getString(ARG_CHUNK_MS)?.toLongOrNull() ?: DEFAULT_CHUNK_MS

  @Test
  fun stream() = runTerminalBenchmark(workload = "stream") {
    Thread.sleep(measureMs)
  }

  @Test
  fun idle() = runTerminalBenchmark(workload = "idle") {
    Thread.sleep(measureMs)
  }

  @Test
  fun scroll() = runTerminalBenchmark(workload = "scroll") {
    val x = device.displayWidth / 2
    val top = (device.displayHeight * 0.30f).toInt()
    val bottom = (device.displayHeight * 0.70f).toInt()
    repeat(6) {
      device.swipe(x, bottom, x, top, 20)
      Thread.sleep(150)
    }
  }

  @Test
  fun ime() = runTerminalBenchmark(workload = "ime") {
    device.click(device.displayWidth / 2, device.displayHeight / 2)
    Thread.sleep(1_500)
    device.pressBack()
    Thread.sleep(500)
  }

  private fun runTerminalBenchmark(
    workload: String,
    measureBlock: MacrobenchmarkScope.() -> Unit,
  ) = benchmarkRule.measureRepeated(
    packageName = TARGET_PACKAGE,
    metrics = listOf(
      FrameTimingMetric(),
      TraceSectionMetric("HerdrOid.terminal.mouseFeed", TraceSectionMetric.Mode.Sum),
      TraceSectionMetric("HerdrOid.terminal.writeInput", TraceSectionMetric.Mode.Sum),
      TraceSectionMetric("HerdrOid.terminal.ack", TraceSectionMetric.Mode.Sum),
    ),
    compilationMode = CompilationMode.Partial(),
    startupMode = StartupMode.WARM,
    iterations = iterations,
    setupBlock = {
      startActivityAndWait(
        Intent().apply {
          component = ComponentName(TARGET_PACKAGE, BENCHMARK_ACTIVITY)
          putExtra(EXTRA_WORKLOAD, workload)
          putExtra(EXTRA_AUTO_DETECT_URLS, autoDetectUrls)
          putExtra(EXTRA_REFRESH_HZ, refreshHz)
          putExtra(EXTRA_CHUNK_MS, chunkMs)
        },
      )
      Thread.sleep(warmupMs)
    },
    measureBlock = measureBlock,
  )

  private companion object {
    const val TARGET_PACKAGE = "dev.riyaldi.herdroid"
    const val BENCHMARK_ACTIVITY = "dev.riyaldi.herdroid.benchmarkapp.BenchmarkTerminalActivity"
    const val EXTRA_WORKLOAD = "workload"
    const val EXTRA_AUTO_DETECT_URLS = "autoDetectUrls"
    const val EXTRA_REFRESH_HZ = "refreshHz"
    const val EXTRA_CHUNK_MS = "chunkMs"
    const val ARG_ITERATIONS = "iterations"
    const val ARG_WARMUP_MS = "warmupMs"
    const val ARG_MEASURE_MS = "measureMs"
    const val ARG_AUTO_DETECT_URLS = "autoDetectUrls"
    const val ARG_REFRESH_HZ = "refreshHz"
    const val ARG_CHUNK_MS = "chunkMs"
    const val DEFAULT_ITERATIONS = 5
    const val DEFAULT_WARMUP_MS = 4_000L
    const val DEFAULT_MEASURE_MS = 10_000L
    const val DEFAULT_REFRESH_HZ = 60f
    const val DEFAULT_CHUNK_MS = 16L
  }
}
