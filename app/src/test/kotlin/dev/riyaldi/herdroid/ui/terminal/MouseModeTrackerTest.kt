package dev.riyaldi.herdroid.ui.terminal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MouseModeTrackerTest {
  @Test
  fun splitModeSequenceIsApplied() {
    val tracker = MouseModeTracker()
    tracker.feed("\u001b[?10".toByteArray())
    assertFalse(tracker.mouseTracking)
    tracker.feed("00h\u001b[?1006h".toByteArray())
    assertTrue(tracker.mouseTracking)
    assertTrue(tracker.sgrEncoding)
  }

  @Test
  fun disablingOneModeKeepsOtherTrackingModes() {
    val tracker = MouseModeTracker()
    tracker.feed("\u001b[?1000h\u001b[?1002h".toByteArray())
    tracker.feed("\u001b[?1000l".toByteArray())
    assertTrue(tracker.mouseTracking)
    tracker.feed("\u001b[?1002l".toByteArray())
    assertFalse(tracker.mouseTracking)
  }

  @Test
  fun sgrWheelEncodingIsUsedWhenEnabled() {
    val tracker = MouseModeTracker()
    tracker.feed("\u001b[?1000h\u001b[?1006h".toByteArray())
    assertTrue(String(tracker.wheel(up = true), Charsets.US_ASCII) == "\u001b[<64;1;1M")
    assertTrue(String(tracker.wheel(up = false), Charsets.US_ASCII) == "\u001b[<65;1;1M")
  }

  @Test
  fun legacyWheelEncodingIsUsedWithoutSgr() {
    val tracker = MouseModeTracker()
    tracker.feed("\u001b[?1000h".toByteArray())
    val up = tracker.wheel(up = true)
    assertTrue(up.contentEquals(byteArrayOf(0x1b, '['.code.toByte(), 'M'.code.toByte(), 96, 33, 33)))
  }
}
