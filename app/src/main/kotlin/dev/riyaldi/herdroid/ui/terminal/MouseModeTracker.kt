package dev.riyaldi.herdroid.ui.terminal

/**
 * Tracks DEC private mouse modes seen in terminal output. Mouse-aware TUIs enable
 * these modes and then expect wheel events instead of local scrollback.
 */
class MouseModeTracker {
  private val trackingModes = mutableSetOf<Int>()
  private var carry = ""

  var mouseTracking: Boolean = false
    private set

  var sgrEncoding: Boolean = false
    private set

  var urxvtEncoding: Boolean = false
    private set

  fun feed(bytes: ByteArray): Boolean {
    val data = carry + String(bytes, Charsets.ISO_8859_1)
    carry = ""
    var offset = 0
    while (offset < data.length) {
      val start = data.indexOf("\u001b[?", offset)
      if (start < 0) {
        carry = incompletePrefix(data)
        return mouseTracking
      }
      var end = start + 3
      while (end < data.length && data[end] != 'h' && data[end] != 'l') end++
      if (end >= data.length) {
        carry = if (data.length - start <= MAX_SEQUENCE) data.substring(start) else ""
        return mouseTracking
      }
      val enabled = data[end] == 'h'
      data.substring(start + 3, end)
        .split(';')
        .mapNotNull(String::toIntOrNull)
        .forEach { mode -> apply(mode, enabled) }
      offset = end + 1
    }
    carry = incompletePrefix(data)
    return mouseTracking
  }

  fun wheel(up: Boolean): ByteArray {
    val button = if (up) 64 else 65
    return when {
      sgrEncoding -> "\u001b[<$button;1;1M".toByteArray(Charsets.US_ASCII)
      urxvtEncoding -> "\u001b[$button;1;1M".toByteArray(Charsets.US_ASCII)
      else -> byteArrayOf(
        0x1b,
        '['.code.toByte(),
        'M'.code.toByte(),
        (32 + button).toByte(),
        (32 + 1).toByte(),
        (32 + 1).toByte(),
      )
    }
  }

  private fun apply(mode: Int, enabled: Boolean) {
    when (mode) {
      9, 1000, 1001, 1002, 1003 -> {
        if (enabled) trackingModes += mode else trackingModes -= mode
        mouseTracking = trackingModes.isNotEmpty()
      }
      1006 -> sgrEncoding = enabled
      1015 -> urxvtEncoding = enabled
    }
  }

  private fun incompletePrefix(data: String): String = when {
    data.endsWith("\u001b[") -> "\u001b["
    data.endsWith("\u001b") -> "\u001b"
    else -> ""
  }

  private companion object {
    const val MAX_SEQUENCE = 64
  }
}
