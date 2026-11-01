package dev.riyaldi.herdroid.mirror

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HerdrAppMirrorTest {
  @Test
  fun parsesSpacesTabsAndTerminals() {
    val value = Json.parseToJsonElement(
      """
      {"spaces":[
        {"id":"sp1","name":"android","folder":"C:\\Users\\me",
         "tabs":[
           {"id":"tb1","name":"Agent","root":{
             "id":"tb1","type":"row","children":[
               {"id":"p1","type":"block","content":{"terminal":"t1w"}},
               {"id":"p2","type":"column","children":[
                 {"id":"p3","type":"block","content":{"terminal":"t7"}}
               ]}
             ]}}
         ]}
      ]}
      """.trimIndent(),
    )

    val mirror = HerdrAppMirror.parse(value)
    assertEquals(1, mirror.spaces.size)
    val space = mirror.spaces.single()
    assertEquals("android", space.displayName)
    assertEquals("Agent", space.tabs.single().displayName)
    assertEquals(listOf("t1w", "t7"), space.tabs.single().terminals)
  }

  @Test
  fun ignoresUnknownFieldsAndNonTerminalPanes() {
    val value = Json.parseToJsonElement(
      """
      {"spaces":[{"id":"sp","name":"","folder":"/work/repo","future":true,
        "tabs":[{"id":"tb","name":"","root":{"id":"tb","type":"row","children":[
          {"id":"p1","type":"block","content":{"mod":"files","props":{"x":1}}},
          {"id":"p2","type":"block","content":{"terminal":"t3"},"hidden":true}
        ]}}]}]}
      """.trimIndent(),
    )
    val mirror = HerdrAppMirror.parse(value)
    assertEquals("repo", mirror.spaces.single().displayName)
    assertEquals(listOf("t3"), mirror.spaces.single().tabs.single().terminals)
  }

  @Test
  fun emptyAndMalformedValuesAreSafe() {
    assertTrue(HerdrAppMirror.parse(null).spaces.isEmpty())
    assertTrue(HerdrAppMirror.parse(Json.parseToJsonElement("{}")).spaces.isEmpty())
    assertTrue(HerdrAppMirror.parse(Json.parseToJsonElement("[]")).spaces.isEmpty())
    assertTrue(HerdrAppMirror.parse(Json.parseToJsonElement("""{"spaces":[null,{"noId":true}]}""")).spaces.isEmpty())
  }

  @Test
  fun capsDisplayText() {
    val huge = "x".repeat(10_000)
    val value = buildJsonObject {
      putJsonArray("spaces") {
        add(buildJsonObject {
          put("id", "sp")
          put("name", huge)
          put("folder", huge)
        })
      }
    }
    val space = HerdrAppMirror.parse(value).spaces.single()
    assertEquals(2048, space.name.length)
    assertEquals(2048, space.folder.length)
  }

  @Test
  fun stopsAtDepthLimitInsteadOfOverflowing() {
    var node = buildJsonObject {
      put("id", "leaf")
      put("type", "block")
      putJsonObject("content") { put("terminal", "t1") }
    }
    repeat(200) {
      node = buildJsonObject {
        put("type", "row")
        putJsonArray("children") { add(node) }
      }
    }
    val value = buildJsonObject {
      putJsonArray("spaces") {
        add(buildJsonObject {
          put("id", "sp")
          putJsonArray("tabs") {
            add(buildJsonObject {
              put("id", "tb")
              put("root", node)
            })
          }
        })
      }
    }

    val mirror = HerdrAppMirror.parse(value)
    assertEquals(1, mirror.spaces.size)
    assertTrue(mirror.spaces.single().tabs.single().terminals.size <= 4096)
  }
}
