package dev.riyaldi.herdroid.terminal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.riyaldi.herdroid.domain.model.OpenTerminal
import dev.riyaldi.herdroid.domain.model.TerminalEvent
import dev.riyaldi.herdroid.domain.model.TerminalHandle
import dev.riyaldi.herdroid.ui.screens.TerminalScreen
import dev.riyaldi.herdroid.ui.theme.HerdrOidTheme
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TerminalAttachInstrumentedTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun liveDataIsWrittenAndAcknowledged() {
    val handle = FakeTerminalHandle()
    val bytes = "hello from attach\r\n".toByteArray()

    compose.setContent {
      HerdrOidTheme {
        TerminalScreen(
          terminal = OpenTerminal(
            machineId = "test",
            address = "test-host",
            title = "test",
            handle = handle,
          ),
          machineName = "test-host",
          onBack = {},
        )
      }
    }

    compose.runOnIdle { handle.emit(TerminalEvent.Data(bytes)) }
    compose.waitUntil(5_000) { handle.acknowledged.get() == bytes.size }
    assertEquals(bytes.size, handle.acknowledged.get())
  }

  @Test
  fun mouseDragSendsWheelInput() {
    val handle = FakeTerminalHandle()
    compose.setContent {
      HerdrOidTheme {
        TerminalScreen(
          terminal = OpenTerminal(
            machineId = "test",
            address = "test-host",
            title = "test",
            handle = handle,
          ),
          machineName = "test-host",
          onBack = {},
        )
      }
    }

    compose.runOnIdle {
      handle.emit(TerminalEvent.Data("\u001b[?1000h\u001b[?1006h".toByteArray()))
    }
    compose.waitForIdle()
    compose.onRoot().performTouchInput {
      swipe(start = center, end = center + Offset(0f, -360f), durationMillis = 400)
    }

    compose.waitUntil(5_000) {
      handle.writes.any { bytes ->
        String(bytes, Charsets.US_ASCII) == "\u001b[<65;1;1M"
      }
    }
    assertEquals(
      "\u001b[<65;1;1M",
      handle.writes.map { bytes -> String(bytes, Charsets.US_ASCII) }
        .firstOrNull { it == "\u001b[<65;1;1M" },
    )
  }
}

private class FakeTerminalHandle : TerminalHandle {
  private val eventStream = MutableSharedFlow<TerminalEvent>(
    replay = 1,
    extraBufferCapacity = 8,
  )
  val acknowledged = AtomicInteger()
  val writes = CopyOnWriteArrayList<ByteArray>()

  override val events: Flow<TerminalEvent> = eventStream

  fun emit(event: TerminalEvent) {
    eventStream.tryEmit(event)
  }

  override fun write(bytes: ByteArray) {
    writes += bytes
  }

  override fun acknowledge(bytes: Int) {
    acknowledged.addAndGet(bytes)
  }

  override fun resize(cols: Int, rows: Int) = Unit

  override fun close() = Unit
}
