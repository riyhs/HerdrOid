package dev.riyaldi.herdroid.domain.model

import java.security.MessageDigest
import java.util.Base64

object HostKeyFingerprint {
  fun fromOpenSsh(hostKeyBase64: String): String? {
    val raw = runCatching { Base64.getDecoder().decode(hostKeyBase64) }.getOrNull() ?: return null
    val digest = MessageDigest.getInstance("SHA-256").digest(raw)
    return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
  }
}
