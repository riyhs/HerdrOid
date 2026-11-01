package dev.riyaldi.herdroid.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FrameTest {
  @Test
  fun readsTwoFrames() {
    val one = Frame(0, FrameKind.DATA, "{\"jsonrpc\":\"2.0\"}".toByteArray())
    val two = Frame(7, FrameKind.CLOSE, "bye".toByteArray())
    val input = ByteArrayInputStream(encode(one) + encode(two))

    val first = readFrame(input)
    assertEquals(0, first.channel)
    assertEquals(FrameKind.DATA, first.kind)
    assertEquals("{\"jsonrpc\":\"2.0\"}", first.payload.toString(Charsets.UTF_8))

    val second = readFrame(input)
    assertEquals(7, second.channel)
    assertEquals(FrameKind.CLOSE, second.kind)
  }

  @Test
  fun rejectsOversizedPayload() {
    val header = ByteArray(FRAME_HEADER_SIZE)
    header[0] = 1
    header[4] = FrameKind.DATA.wire.toByte()
    header[5] = 0xff.toByte()
    header[6] = 0xff.toByte()
    header[7] = 0xff.toByte()
    header[8] = 0x7f
    try {
      readFrame(ByteArrayInputStream(header), maxPayload = 1024)
      fail("expected ProtocolException")
    } catch (_: ProtocolException) {
    }
  }

  @Test
  fun creditAndResizePayloadsAreLittleEndian() {
    assertEquals(
      listOf(4, 3, 2, 1),
      encodeCredit(0x01020304).map { it.toInt() and 0xff },
    )
    val resize = encodeResize(0x0102, 0x0304)
    assertEquals(2, resize[0].toInt())
    assertEquals(1, resize[1].toInt())
    assertEquals(4, resize[2].toInt())
    assertEquals(3, resize[3].toInt())
  }

  @Test
  fun parsesHello() {
    val hello = parseHello(
      Json.parseToJsonElement(
        """
        {"core":{"id":"abc","name":"demo-core"},"version":"0.1.15","generation":1,
         "framed":true,"features":["framed","attach"],"methods":["hello","doc.get"]}
        """.trimIndent(),
      ),
    )
    assertEquals("abc", hello.core.id)
    assertEquals("demo-core", hello.core.name)
    assertEquals(1, hello.generation)
    assertTrue("framed" in hello.features)
    assertTrue("doc.get" in hello.methods)
  }

  @Test
  fun docSetIsNotAllowed() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val client = HcClient(ByteArrayInputStream(ByteArray(0)), ByteArrayOutputStream(), scope)
    try {
      client.call("doc.set", Json.parseToJsonElement("{}").jsonObject)
      fail("doc.set must not be callable")
    } catch (_: SecurityException) {
      // Expected: the client exposes doc.get only.
    } finally {
      client.close()
      scope.cancel()
    }
  }

  @Test
  fun parsesDocVersion() {
    val doc = parseDoc(
      Json.parseToJsonElement("""{"value":{"spaces":[]},"version":93}"""),
    )
    assertEquals(93L, doc.version)
    assertTrue(doc.value!!.jsonObject.containsKey("spaces"))
    assertEquals("2.0", Json.parseToJsonElement("""{"jsonrpc":"2.0"}""").jsonObject["jsonrpc"]!!.jsonPrimitive.content)
  }

  private fun encode(frame: Frame): ByteArray {
    val out = ByteArrayOutputStream()
    writeFrame(out, frame)
    return out.toByteArray()
  }
}
