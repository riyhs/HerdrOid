package dev.riyaldi.herdroid.mirror

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class Mirror(
  val spaces: List<MirrorSpace>,
) {
  companion object {
    val Empty = Mirror(emptyList())
  }
}

data class MirrorSpace(
  val name: String,
  val folder: String,
  val tabs: List<MirrorTab>,
) {
  val displayName: String
    get() = name.ifBlank {
      folder.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\').ifBlank { "space" }
    }
}

data class MirrorTab(
  val name: String,
  val terminals: List<String>,
) {
  val displayName: String get() = name.ifBlank { "tab" }
}

object HerdrAppMirror {
  private const val MAX_SPACES = 128
  private const val MAX_TABS = 128
  private const val MAX_TEXT = 2048
  private const val MAX_TERMINALS = 4096
  private const val MAX_CHILDREN = 256
  private const val MAX_DEPTH = 64
  private const val MAX_NODES = 100_000

  fun parse(value: JsonElement?): Mirror {
    if (value == null || value !is JsonObject) return Mirror.Empty
    val spaces = (value["spaces"] as? JsonArray)
      ?.take(MAX_SPACES)
      ?.mapNotNull { parseSpace(it) }
      .orEmpty()
    return Mirror(spaces)
  }

  private fun parseSpace(element: JsonElement): MirrorSpace? {
    val o = element as? JsonObject ?: return null
    if (o.string("id") == null) return null
    val tabs = (o["tabs"] as? JsonArray)
      ?.take(MAX_TABS)
      ?.mapNotNull { parseTab(it) }
      .orEmpty()
    return MirrorSpace(
      name = o.string("name")?.take(MAX_TEXT).orEmpty(),
      folder = o.string("folder")?.take(MAX_TEXT).orEmpty(),
      tabs = tabs,
    )
  }

  private fun parseTab(element: JsonElement): MirrorTab? {
    val o = element as? JsonObject ?: return null
    if (o.string("id") == null) return null
    val found = mutableListOf<String>()
    o["root"]?.let { root -> collect(root, found, 0, Budget()) }
    return MirrorTab(
      name = o.string("name")?.take(MAX_TEXT).orEmpty(),
      terminals = found.distinct().take(MAX_TERMINALS),
    )
  }

  private fun collect(
    element: JsonElement,
    terminals: MutableList<String>,
    depth: Int,
    budget: Budget,
  ) {
    if (depth > MAX_DEPTH || budget.nodes >= MAX_NODES || terminals.size >= MAX_TERMINALS) return
    budget.nodes++
    when (element) {
      is JsonArray -> element
        .take(MAX_CHILDREN)
        .forEach { collect(it, terminals, depth + 1, budget) }
      is JsonObject -> {
        if (element.string("type") == "block") {
          val content = element["content"] as? JsonObject
          content?.string("terminal")?.take(MAX_TEXT)?.let { terminal ->
            if (terminal.isNotBlank() && terminals.size < MAX_TERMINALS) terminals += terminal
          }
        }
        (element["children"] as? JsonArray)
          ?.take(MAX_CHILDREN)
          ?.forEach { collect(it, terminals, depth + 1, budget) }
      }
      else -> Unit
    }
  }

  private class Budget(var nodes: Int = 0)

  private fun JsonObject.string(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull
}
