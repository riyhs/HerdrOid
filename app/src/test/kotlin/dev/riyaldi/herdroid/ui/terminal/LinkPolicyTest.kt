package dev.riyaldi.herdroid.ui.terminal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkPolicyTest {
  @Test
  fun acceptsHttpLinksWithHosts() {
    assertTrue(isSafeTerminalLink("https://example.com/path"))
    assertTrue(isSafeTerminalLink("http://example.com"))
    assertTrue(isSafeTerminalLink("HTTPS://EXAMPLE.COM"))
  }

  @Test
  fun rejectsNonWebAndMalformedLinks() {
    for (url in listOf(
      "intent://scan/#Intent;scheme=zxing;end",
      "file:///sdcard/secret",
      "javascript:alert(1)",
      "mailto:user@example.com",
      "https://",
      "https:///path",
      "not a url",
    )) {
      assertFalse(url, isSafeTerminalLink(url))
    }
  }
}
