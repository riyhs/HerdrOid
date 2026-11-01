package dev.riyaldi.herdroid.ui.terminal

import java.net.URI

internal fun isSafeTerminalLink(url: String): Boolean = runCatching {
  val uri = URI(url)
  val scheme = uri.scheme?.lowercase()
  (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
}.getOrDefault(false)
