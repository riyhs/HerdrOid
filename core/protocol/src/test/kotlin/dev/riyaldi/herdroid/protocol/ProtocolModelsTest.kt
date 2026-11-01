package dev.riyaldi.herdroid.protocol

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolModelsTest {
  @Test
  fun capsTerminalTextAndLabels() {
    val huge = "x".repeat(20_000)
    val labels = buildJsonObject {
      repeat(100) { index ->
        put("k$index" + "x".repeat(200), huge)
      }
    }
    val value = buildJsonObject {
      put("address", huge)
      put("name", huge)
      put("title", huge)
      put("cwd", huge)
      put("state", huge)
      put("parent", huge)
      put("labels", labels)
      put(
        "status",
        buildJsonObject {
          put("state", huge)
          put("app", huge)
          put("title", huge)
          put("msg", huge)
          put("kind", huge)
        },
      )
    }

    val terminal = parseTerminal(value)
    assertEquals(256, terminal.address.length)
    assertEquals(512, terminal.name?.length)
    assertEquals(1024, terminal.title.length)
    assertEquals(4096, terminal.cwd.length)
    assertEquals(64, terminal.state.length)
    assertEquals(256, terminal.parent?.length)
    assertEquals(64, terminal.labels.size)
    assertTrue(terminal.labels.keys.all { it.length <= 128 })
    assertTrue(terminal.labels.values.all { it.length <= 1024 })
    assertEquals(64, terminal.status?.state?.length)
    assertEquals(512, terminal.status?.app?.length)
    assertEquals(1024, terminal.status?.title?.length)
    assertEquals(4096, terminal.status?.message?.length)
    assertEquals(64, terminal.status?.kind?.length)
  }

  @Test
  fun capsHelloFields() {
    val huge = "x".repeat(20_000)
    val methods = buildJsonArray {
      repeat(100) { index -> add(JsonPrimitive("m$index" + "x".repeat(200))) }
    }
    val value = buildJsonObject {
      put("version", huge)
      put("methods", methods)
      put("features", methods)
      put(
        "core",
        buildJsonObject {
          put("id", huge)
          put("name", huge)
        },
      )
    }

    val hello = parseHello(value)
    assertEquals(128, hello.version.length)
    assertEquals(256, hello.core.id.length)
    assertEquals(256, hello.core.name.length)
    assertEquals(64, hello.methods.size)
    assertEquals(64, hello.features.size)
    assertTrue(hello.methods.all { it.length <= 128 })
  }
}
