package dev.riyaldi.herdroid.protocol

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class CoreInfo(val id: String, val name: String)

data class HelloResult(
  val core: CoreInfo,
  val version: String,
  val generation: Int,
  val framed: Boolean,
  val features: Set<String>,
  val methods: Set<String>,
)

data class TerminalInfo(
  val address: String,
  val name: String?,
  val title: String,
  val cwd: String,
  val state: String,
  val status: TerminalStatus?,
  val parent: String?,
  val labels: Map<String, String>,
  val keep: Boolean,
  val cols: Int,
  val rows: Int,
  val turn: Long,
)

data class TerminalStatus(
  val state: String,
  val app: String,
  val title: String,
  val message: String,
  val kind: String?,
  val progress: Int?,
)

data class DocValue(val value: JsonElement?, val version: Long)

data class SubscribeSnapshot(val seq: Long, val terminals: List<TerminalInfo>)

private const val MAX_ADDRESS = 256
private const val MAX_NAME = 512
private const val MAX_TITLE = 1024
private const val MAX_MESSAGE = 4096
private const val MAX_PATH = 4096
private const val MAX_STATE = 64
private const val MAX_KIND = 64
private const val MAX_LABELS = 64
private const val MAX_LABEL_KEY = 128
private const val MAX_LABEL_VALUE = 1024
private const val MAX_CORE_FIELD = 256
private const val MAX_VERSION = 128
private const val MAX_SET_ITEMS = 64
private const val MAX_SET_ITEM_LENGTH = 128

private fun String?.capped(max: Int): String = this?.take(max).orEmpty()

internal fun JsonObject.string(name: String): String? =
  this[name]?.jsonPrimitive?.contentOrNull

internal fun JsonObject.int(name: String): Int? =
  this[name]?.jsonPrimitive?.intOrNull

internal fun JsonObject.long(name: String): Long? =
  this[name]?.jsonPrimitive?.longOrNull

internal fun JsonObject.bool(name: String): Boolean? =
  this[name]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()

internal fun JsonObject.obj(name: String): JsonObject? =
  (this[name] as? JsonObject)

internal fun JsonObject.array(name: String): JsonArray? =
  this[name] as? JsonArray

internal fun JsonElement?.asStringSet(
  maxItems: Int = MAX_SET_ITEMS,
  maxLength: Int = MAX_SET_ITEM_LENGTH,
): Set<String> =
  (this as? JsonArray)
    ?.asSequence()
    ?.take(maxItems)
    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.take(maxLength) }
    ?.toSet()
    ?: emptySet()

fun parseHello(result: JsonElement): HelloResult {
  val root = result.jsonObject
  val core = root.obj("core") ?: JsonObject(emptyMap())
  return HelloResult(
    core = CoreInfo(
      core.string("id").capped(MAX_CORE_FIELD),
      core.string("name").capped(MAX_CORE_FIELD),
    ),
    version = root.string("version").capped(MAX_VERSION),
    generation = root.int("generation") ?: 0,
    framed = root.bool("framed") ?: false,
    features = root["features"].asStringSet(),
    methods = root["methods"].asStringSet(),
  )
}

fun parseTerminal(element: JsonElement): TerminalInfo {
  val o = element.jsonObject
  val statusObj = o.obj("status")
  val status = statusObj?.let {
    TerminalStatus(
      state = it.string("state").capped(MAX_STATE),
      app = it.string("app").capped(MAX_NAME),
      title = it.string("title").capped(MAX_TITLE),
      message = it.string("msg").capped(MAX_MESSAGE),
      kind = it.string("kind")?.take(MAX_KIND),
      progress = it.int("progress"),
    )
  }
  val labels = o.obj("labels")
    ?.entries
    ?.take(MAX_LABELS)
    ?.associate { (key, value) ->
      key.take(MAX_LABEL_KEY) to value.jsonPrimitive.contentOrNull.capped(MAX_LABEL_VALUE)
    }
    .orEmpty()
  return TerminalInfo(
    address = o.string("address").capped(MAX_ADDRESS),
    name = o.string("name")?.take(MAX_NAME),
    title = o.string("title").capped(MAX_TITLE),
    cwd = o.string("cwd").capped(MAX_PATH),
    state = o.string("state").capped(MAX_STATE),
    status = status,
    parent = o.string("parent")?.take(MAX_ADDRESS),
    labels = labels,
    keep = o.bool("keep") ?: false,
    cols = o.int("cols") ?: 0,
    rows = o.int("rows") ?: 0,
    turn = o.long("turn") ?: 0,
  )
}

fun parseTerminalList(result: JsonElement): List<TerminalInfo> {
  val terminals = (result as? JsonObject)?.array("terminals") ?: (result as? JsonArray)
    ?: return emptyList()
  return terminals.mapNotNull { runCatching { parseTerminal(it) }.getOrNull() }
}

fun parseDoc(result: JsonElement): DocValue {
  val o = result.jsonObject
  return DocValue(value = o["value"], version = o.long("version") ?: 0)
}
