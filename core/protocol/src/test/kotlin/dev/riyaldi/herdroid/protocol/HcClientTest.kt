package dev.riyaldi.herdroid.protocol

import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class HcClientTest {
  @Test
  fun handshakeThenRpcCallOverFramedTransport() = runBlocking {
    val clientToCore = PipedOutputStream()
    val coreInput = PipedInputStream(clientToCore, 64 * 1024)
    val coreToClient = PipedOutputStream()
    val clientInput = PipedInputStream(coreToClient, 64 * 1024)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val server = thread(name = "fake-hc") {
      check(readLine(coreInput).contains("\"hello\""))
      coreToClient.write(
        ("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"core\":{\"id\":\"fake\",\"name\":\"fake-core\"}," +
          "\"version\":\"0.1.15\",\"generation\":1,\"framed\":true," +
          "\"features\":[\"framed\",\"attach\",\"events\"]," +
          "\"methods\":[\"hello\",\"terminal.list\"]}}").toByteArray() +
          byteArrayOf('\n'.code.toByte()),
      )
      coreToClient.flush()

      val frame = readFrame(coreInput)
      val request = Json.parseToJsonElement(frame.payload.toString(Charsets.UTF_8)).jsonObject
      assertEquals("terminal.list", request["method"]!!.jsonPrimitive.content)
      val id = request["id"]!!.jsonPrimitive.content
      val response = """{"jsonrpc":"2.0","id":$id,"result":{"terminals":[]}}"""
      writeFrame(coreToClient, Frame(0, FrameKind.DATA, response.toByteArray()))
    }

    val client = HcClient(clientInput, clientToCore, scope)
    try {
      val hello = client.handshake()
      assertEquals("fake", hello.core.id)
      assertEquals("fake-core", hello.core.name)
      val result = client.call("terminal.list")
      assertEquals(0, parseTerminalList(result).size)
    } finally {
      client.close()
      scope.cancel()
      runCatching { coreInput.close() }
      runCatching { coreToClient.close() }
      server.join(2_000)
    }
  }

  private fun readLine(input: InputStream): String {
    val out = StringBuilder()
    while (true) {
      val b = input.read()
      if (b < 0 || b == '\n'.code) break
      if (b != '\r'.code) out.append(b.toChar())
    }
    return out.toString()
  }
}
