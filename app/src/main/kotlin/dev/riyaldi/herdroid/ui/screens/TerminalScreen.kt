package dev.riyaldi.herdroid.ui.screens

import android.os.Trace
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imeAnimationSource
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.riyaldi.herdroid.domain.model.OpenTerminal
import dev.riyaldi.herdroid.domain.model.TerminalEvent
import dev.riyaldi.herdroid.ui.terminal.MouseModeTracker
import dev.riyaldi.herdroid.ui.terminal.SensitiveClipboardManager
import dev.riyaldi.herdroid.ui.terminal.isSafeTerminalLink
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import org.connectbot.terminal.Terminal
import org.connectbot.terminal.TerminalEmulatorFactory
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(
  terminal: OpenTerminal,
  machineName: String,
  onBack: () -> Unit,
  autoDetectUrls: Boolean = true,
) {
  val clipboard = LocalClipboardManager.current
  val uriHandler = LocalUriHandler.current
  val context = LocalContext.current
  val sensitiveClipboard = remember(context) { SensitiveClipboardManager(context) }
  val attach = terminal.handle
  val currentAttach by rememberUpdatedState(attach)
  val mouseTracker = remember(attach) { MouseModeTracker() }
  var mouseWheelEnabled by remember(attach) { mutableStateOf(false) }
  val density = LocalDensity.current
  val wheelStepPx = with(density) { 18.dp.toPx() }.coerceAtLeast(1f)
  val imeAnimating =
    WindowInsets.imeAnimationSource.getBottom(density) !=
      WindowInsets.imeAnimationTarget.getBottom(density)
  var status by remember { mutableStateOf(if (terminal.demo) "demo" else "attached") }
  var keyboardVisible by rememberSaveable { mutableStateOf(false) }
  var showHint by rememberSaveable { mutableStateOf(true) }
  val demoInput = remember { MutableSharedFlow<ByteArray>(extraBufferCapacity = 64) }

  val emulator = remember(terminal, autoDetectUrls) {
    TerminalEmulatorFactory.create(
      initialRows = 30,
      initialCols = 100,
      defaultForeground = Color(0xFFD8DEE9),
      defaultBackground = Color(0xFF0D1117),
      onKeyboardInput = { bytes ->
        if (terminal.demo) demoInput.tryEmit(bytes) else currentAttach?.write(bytes)
      },
      onResize = { dimensions -> currentAttach?.resize(dimensions.columns, dimensions.rows) },
      autoDetectUrls = autoDetectUrls,
    )
  }

  val terminalLock = remember(emulator) { Any() }
  val terminalClosed = remember(emulator) { AtomicBoolean(false) }

  fun writeEmulator(bytes: ByteArray): Boolean = synchronized(terminalLock) {
    if (terminalClosed.get()) {
      false
    } else {
      emulator.writeInput(bytes)
      true
    }
  }

  fun pasteEmulator(text: String) = synchronized(terminalLock) {
    if (!terminalClosed.get()) emulator.pasteText(text)
  }

  DisposableEffect(emulator) {
    onDispose {
      synchronized(terminalLock) {
        terminalClosed.set(true)
        emulator.close()
      }
    }
  }

  LaunchedEffect(showHint) {
    if (showHint) {
      delay(4_000)
      showHint = false
    }
  }

  LaunchedEffect(terminal.demo, emulator) {
    if (terminal.demo) {
      demoInput.collect { bytes -> writeEmulator(bytes) }
    }
  }

  LaunchedEffect(terminal.address, attach) {
    if (terminal.demo) {
      writeEmulator(DEMO_SCREEN.toByteArray())
      return@LaunchedEffect
    }
    if (attach == null) {
      status = "not attached"
      return@LaunchedEffect
    }
    try {
      withContext(Dispatchers.Default) {
        attach.events.collect { event ->
          when (event) {
            is TerminalEvent.Data -> {
              val bytes = event.bytes
              Trace.beginSection("HerdrOid.terminal.mouseFeed")
              val wheelEnabled = try {
                mouseTracker.feed(bytes)
              } finally {
                Trace.endSection()
              }
              if (wheelEnabled != mouseWheelEnabled) {
                withContext(Dispatchers.Main.immediate) {
                  mouseWheelEnabled = wheelEnabled
                }
              }
              Trace.beginSection("HerdrOid.terminal.writeInput")
              val written = try {
                writeEmulator(bytes)
              } finally {
                Trace.endSection()
              }
              if (written) {
                Trace.beginSection("HerdrOid.terminal.ack")
                try {
                  attach.acknowledge(bytes.size)
                } finally {
                  Trace.endSection()
                }
              }
            }
            is TerminalEvent.Closed -> {
              withContext(Dispatchers.Main.immediate) {
                status = event.reason?.takeIf { it.isNotBlank() }?.let { "closed: $it" } ?: "closed"
                keyboardVisible = false
              }
            }
          }
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      withContext(Dispatchers.Main.immediate) {
        status = e.message ?: "attach failed"
      }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              terminal.title.ifBlank { terminal.address },
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
            )
            Text(
              listOf(machineName, terminal.address, status)
                .filter { it.isNotBlank() }
                .joinToString(" · "),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to inbox")
          }
        },
        actions = {
          if (!terminal.demo && attach != null) {
            TextButton(onClick = { keyboardVisible = !keyboardVisible }) {
              Text(if (keyboardVisible) "Hide keys" else "Keyboard")
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface,
        ),
      )
    },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .imePadding(),
    ) {
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .then(
            if (mouseWheelEnabled && attach != null && !terminal.demo) {
              Modifier.pointerInput(attach, wheelStepPx) {
                terminalWheelScroll(wheelStepPx) { up ->
                  currentAttach?.write(mouseTracker.wheel(up))
                }
              }
            } else {
              Modifier
            },
          ),
      ) {
        if (attach == null && !terminal.demo) {
          Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text("Terminal not attached", style = MaterialTheme.typography.titleMedium)
            Text(
              "The attach request did not complete. Return to the inbox and try again.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(status, style = MaterialTheme.typography.bodySmall)
          }
        } else {
          CompositionLocalProvider(LocalClipboardManager provides sensitiveClipboard) {
            Terminal(
              terminalEmulator = emulator,
              modifier = Modifier.fillMaxSize(),
              initialFontSize = 12.sp,
              minFontSize = 7.sp,
              maxFontSize = 28.sp,
              backgroundColor = Color(0xFF0D1117),
              foregroundColor = Color(0xFFD8DEE9),
              keyboardEnabled = true,
              showSoftKeyboard = keyboardVisible,
              onTerminalTap = {
                keyboardVisible = true
                showHint = false
              },
              onImeVisibilityChanged = { visible ->
                if (!visible) keyboardVisible = false
              },
              onHyperlinkClick = { url ->
                if (isSafeTerminalLink(url)) {
                  runCatching { uriHandler.openUri(url) }
                }
              },
              onPasteRequest = {
                clipboard.getText()?.text?.let { pasteEmulator(it) }
              },
              resizeSuspended = imeAnimating,
            )
          }
        }

        androidx.compose.animation.AnimatedVisibility(
          visible = showHint,
          enter = fadeIn(),
          exit = fadeOut(),
          modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        ) {
          Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
          ) {
            Text(
              "Tap to type · Drag to scroll · Long-press to select",
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              style = MaterialTheme.typography.labelSmall,
            )
          }
        }
      }

      if (attach != null || terminal.demo) {
        TerminalKeyBar(
          keyboardVisible = keyboardVisible,
          demo = terminal.demo,
          onToggleKeyboard = { keyboardVisible = !keyboardVisible },
          onSend = { text ->
            if (terminal.demo) writeEmulator(text.toByteArray()) else attach?.write(text.toByteArray())
          },
          onPaste = {
            clipboard.getText()?.text?.let { text ->
              if (terminal.demo) writeEmulator(text.toByteArray()) else pasteEmulator(text)
            }
          },
        )
      }
    }
  }
}

@Composable
private fun TerminalKeyBar(
  keyboardVisible: Boolean,
  demo: Boolean,
  onToggleKeyboard: () -> Unit,
  onSend: (String) -> Unit,
  onPaste: () -> Unit,
) {
  val keys = listOf(
    "Esc" to "\u001b",
    "Tab" to "\t",
    "Ctrl-C" to "\u0003",
    "Ctrl-D" to "\u0004",
    "PgUp" to "\u001b[5~",
    "PgDn" to "\u001b[6~",
    "↑" to "\u001b[A",
    "↓" to "\u001b[B",
    "→" to "\u001b[C",
    "←" to "\u001b[D",
    "Home" to "\u001b[H",
    "End" to "\u001b[F",
    "Enter" to "\r",
  )
  LazyRow(
    modifier = Modifier
      .fillMaxWidth()
      .height(48.dp),
    contentPadding = PaddingValues(horizontal = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    item(key = "keyboard") {
      KeyCap(
        label = if (keyboardVisible) "Hide" else "Keys",
        selected = keyboardVisible,
        onClick = onToggleKeyboard,
      )
    }
    items(keys, key = { it.first }) { (label, value) ->
      KeyCap(label = label, onClick = { onSend(value) })
    }
    item(key = "paste") {
      KeyCap(label = "Paste", onClick = onPaste)
    }
  }
}

@Composable
private fun KeyCap(
  label: String,
  selected: Boolean = false,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = MaterialTheme.shapes.small,
    color = if (selected) {
      MaterialTheme.colorScheme.primaryContainer
    } else {
      MaterialTheme.colorScheme.surfaceContainerHigh
    },
  ) {
    Box(
      modifier = Modifier.height(48.dp).padding(horizontal = 10.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(label, style = MaterialTheme.typography.labelMedium)
    }
  }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.terminalWheelScroll(
  stepPx: Float,
  onWheel: (Boolean) -> Unit,
) {
  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    var lastX = down.position.x
    var lastY = down.position.y
    var totalX = 0f
    var totalY = 0f
    var scrolling = false
    var residual = 0f
    while (true) {
      val event = awaitPointerEvent(PointerEventPass.Initial)
      val change = event.changes.firstOrNull { it.id == down.id } ?: break
      if (!change.pressed) {
        if (scrolling) change.consume()
        break
      }
      val dx = change.position.x - lastX
      val dy = change.position.y - lastY
      lastX = change.position.x
      lastY = change.position.y
      totalX += dx
      totalY += dy
      if (!scrolling && abs(totalY) > viewConfiguration.touchSlop && abs(totalY) > abs(totalX)) {
        scrolling = true
        residual = totalY
      }
      if (scrolling) {
        residual += dy
        while (residual >= stepPx) {
          onWheel(true)
          residual -= stepPx
        }
        while (residual <= -stepPx) {
          onWheel(false)
          residual += stepPx
        }
        change.consume()
      }
    }
  }
}

private val DEMO_SCREEN: String = buildString {
  append("\u001b[1;36mHerdrOid\u001b[0m demo terminal\r\n")
  append("\u001b[32m✓\u001b[0m native libvterm renderer\r\n")
  append("\u001b[32m✓\u001b[0m drag to scroll, long-press to select\r\n")
  append("\u001b[33m!\u001b[0m connect a real machine to run agents\r\n\r\n")
  repeat(80) { line ->
    append("history ")
    append(line.toString().padStart(2, '0'))
    append("  The quick brown fox jumps over the lazy dog.\r\n")
  }
  append("$ ")
}
