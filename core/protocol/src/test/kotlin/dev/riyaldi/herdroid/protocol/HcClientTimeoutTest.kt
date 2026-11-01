package dev.riyaldi.herdroid.protocol

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InterruptedIOException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HcClientTimeoutTest {
  @Test
  fun blockingHandshakeIsInterruptedByTimeout() = runBlocking {
    val stream = object : InputStream() {
      @Volatile var interrupted = false
      override fun read(): Int {
        try {
          Thread.sleep(60_000)
          return -1
        } catch (e: InterruptedException) {
          interrupted = true
          throw InterruptedIOException("interrupted")
        }
      }
    }
    val client = HcClient(stream, ByteArrayOutputStream(), this)
    val started = System.nanoTime()
    runCatching { withTimeoutOrNull(200) { client.handshake() } }
    val elapsedMs = (System.nanoTime() - started) / 1_000_000
    assertTrue("handshake did not terminate quickly: ${elapsedMs}ms", elapsedMs < 3_000)
    assertTrue("blocking read was not interrupted", stream.interrupted)
  }
}
