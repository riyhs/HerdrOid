package dev.riyaldi.herdroid.protocol

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

class RpcException(
  val code: Int,
  val errorKind: String?,
  message: String,
) : Exception(message)

class HcClosedException(message: String, cause: Throwable? = null) : Exception(message, cause)

class HcClient(
  input: InputStream,
  private val output: OutputStream,
  private val scope: CoroutineScope,
) : AutoCloseable {
  private val input = BufferedInputStream(input)
  private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
  }

  private val nextId = AtomicLong(2)
  private val pending = ConcurrentHashMap<Long, CompletableDeferred<JsonElement>>()
  private val channelHandlers = ConcurrentHashMap<Int, (Frame) -> Unit>()
  private val orphanFrames = ConcurrentHashMap<Int, MutableList<Frame>>()
  private val orphanLock = Any()
  private var orphanBytes = 0L
  private val _events = MutableSharedFlow<JsonObject>(
    extraBufferCapacity = 1024,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )
  val events = _events.asSharedFlow()

  private var readerJob: Job? = null
  private var hello: HelloResult? = null
  private var closed = false

  suspend fun handshake(generation: Int = 1): HelloResult = withContext(Dispatchers.IO) {
    check(hello == null) { "handshake already completed" }
    val request = buildJsonObject {
      put("jsonrpc", "2.0")
      put("id", 1)
      put("method", "hello")
      put("params", buildJsonObject {
        put("framed", true)
        put("generation", generation)
      })
    }
    val line = runInterruptible {
      writeJsonLine(request)
      readLine()
    } ?: throw HcClosedException("connection closed during hello")
    val response = parseObject(line)
    response["error"]?.let { throw parseRpcError(it) }
    val result = response["result"] ?: throw ProtocolException("hello returned no result")
    val parsed = parseHello(result)
    if (parsed.generation != 1) {
      throw ProtocolException("unsupported protocol generation ${parsed.generation}")
    }
    if (!parsed.framed) throw ProtocolException("core did not switch to framed mode")
    hello = parsed
    readerJob = scope.launch(Dispatchers.IO) { readLoop() }
    parsed
  }

  suspend fun call(method: String, params: JsonObject = JsonObject(emptyMap())): JsonElement {
    if (method !in ALLOWED_METHODS) throw SecurityException("method not allowed: $method")
    if (hello == null) throw IllegalStateException("handshake not completed")
    if (closed) throw HcClosedException("client is closed")
    val id = nextId.getAndIncrement()
    val result = CompletableDeferred<JsonElement>()
    pending[id] = result
    try {
      writeJsonFrame(buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("method", method)
        put("params", params)
      })
      return withTimeout(30_000) { result.await() }
    } finally {
      pending.remove(id)
    }
  }

  suspend fun listTerminals(all: Boolean = false): List<TerminalInfo> {
    val params = buildJsonObject { put("all", all) }
    return parseTerminalList(call("terminal.list", params))
  }

  suspend fun getDoc(name: String, on: String? = null): DocValue {
    val params = buildJsonObject {
      put("name", name)
      if (on != null) put("on", on)
    }
    return parseDoc(call("doc.get", params))
  }

  suspend fun subscribe(): SubscribeSnapshot {
    val result = call("events.subscribe", buildJsonObject {})
    val o = result.jsonObject
    val seq = o["seq"]?.jsonPrimitive?.longOrNull ?: 0L
    val channel = o["channel"]?.jsonPrimitive?.intOrNull
    if (channel != null) {
      registerChannel(channel) { frame ->
        when (frame.kind) {
          FrameKind.DATA -> {
            val text = frame.payload.toString(StandardCharsets.UTF_8)
            for (line in text.lineSequence()) {
              if (line.isBlank()) continue
              runCatching { parseObject(line) }.onSuccess { event ->
                event["params"]?.let { p ->
                  if (p is JsonObject) _events.tryEmit(p)
                }
              }
            }
            writeFrameQuietly(Frame(channel, FrameKind.CREDIT, encodeCredit(frame.payload.size)))
          }
          FrameKind.CLOSE -> {
            channelHandlers.remove(channel)
            _events.tryEmit(buildJsonObject {
              put("type", "closed")
              put("reason", frame.payload.toString(StandardCharsets.UTF_8))
            })
          }
          else -> Unit
        }
      }
    }
    val terminals = o["terminals"] as? JsonArray
    return SubscribeSnapshot(
      seq = seq,
      terminals = terminals?.mapNotNull { runCatching { parseTerminal(it) }.getOrNull() }.orEmpty(),
    )
  }

  suspend fun attach(
    terminal: String,
    cols: Int,
    rows: Int,
    history: Int = 0,
  ): AttachStream {
    val result = call("terminal.attach", buildJsonObject {
      put("terminal", terminal)
      put("cols", cols)
      put("rows", rows)
      put("history", history)
    })
    val channel = result.jsonObject["channel"]?.jsonPrimitive?.intOrNull
      ?: throw ProtocolException("attach returned no channel")
    return AttachStream(channel)
  }

  private fun registerChannel(channel: Int, handler: (Frame) -> Unit) {
    channelHandlers[channel] = handler
    val buffered = synchronized(orphanLock) {
      orphanFrames.remove(channel)?.also { frames ->
        orphanBytes = (orphanBytes - frames.sumOf { it.payload.size }).coerceAtLeast(0)
      }
    }
    buffered?.forEach { frame -> runCatching { handler(frame) } }
  }

  private fun bufferOrphan(frame: Frame) {
    synchronized(orphanLock) {
      if (orphanFrames.size >= MAX_ORPHAN_CHANNELS && !orphanFrames.containsKey(frame.channel)) {
        return
      }
      val list = orphanFrames.getOrPut(frame.channel) { mutableListOf() }
      val total = list.sumOf { it.payload.size }
      if (total + frame.payload.size <= MAX_ORPHAN_BYTES_PER_CHANNEL &&
        orphanBytes + frame.payload.size <= MAX_ORPHAN_BYTES_TOTAL
      ) {
        list += frame
        orphanBytes += frame.payload.size
      }
    }
  }

  private fun readLoop() {
    try {
      while (scope.isActive && !closed) {
        val frame = readFrame(input)
        when (frame.kind) {
          FrameKind.DATA -> {
            if (frame.channel == 0) {
              handleJsonPayload(frame.payload)
            } else {
              val handler = channelHandlers[frame.channel]
              if (handler != null) handler(frame) else bufferOrphan(frame)
            }
          }
          FrameKind.CLOSE -> {
            val handler = channelHandlers.remove(frame.channel)
            if (handler != null) handler(frame) else bufferOrphan(frame)
          }
          else -> {
            val handler = channelHandlers[frame.channel]
            if (handler != null) handler(frame) else bufferOrphan(frame)
          }
        }
      }
    } catch (e: EOFException) {
      failAll(HcClosedException("connection closed", e))
    } catch (e: Throwable) {
      if (!closed) failAll(e)
    }
  }

  private fun handleJsonPayload(payload: ByteArray) {
    val text = payload.toString(StandardCharsets.UTF_8)
    for (line in text.lineSequence()) {
      if (line.isBlank()) continue
      val obj = runCatching { parseObject(line) }.getOrElse {
        failAll(ProtocolException("invalid JSON from core: ${it.message}"))
        return
      }
      val id = obj["id"]?.jsonPrimitive?.longOrNull
      if (id != null) {
        val waiter = pending.remove(id) ?: continue
        val error = obj["error"]
        if (error != null) waiter.completeExceptionally(parseRpcError(error))
        else waiter.complete(obj["result"] ?: JsonObject(emptyMap()))
      } else if (obj["method"]?.jsonPrimitive?.contentOrNull == "event") {
        (obj["params"] as? JsonObject)?.let { _events.tryEmit(it) }
      }
    }
  }

  private fun failAll(error: Throwable) {
    if (closed) return
    closed = true
    pending.values.forEach { it.completeExceptionally(error) }
    pending.clear()
    val closedFrame = error.message?.toByteArray() ?: ByteArray(0)
    channelHandlers.forEach { (channel, handler) ->
      runCatching { handler(Frame(channel, FrameKind.CLOSE, closedFrame)) }
    }
    channelHandlers.clear()
    synchronized(orphanLock) {
      orphanFrames.clear()
      orphanBytes = 0
    }
    _events.tryEmit(buildJsonObject {
      put("type", "closed")
      put("reason", error.message ?: error.toString())
    })
  }

  private fun parseObject(text: String): JsonObject =
    json.parseToJsonElement(text).jsonObject

  private fun parseRpcError(element: JsonElement): RpcException {
    val error = element.jsonObject
    val data = error["data"] as? JsonObject
    return RpcException(
      code = error["code"]?.jsonPrimitive?.intOrNull ?: -1,
      errorKind = data?.get("kind")?.jsonPrimitive?.contentOrNull,
      message = error["message"]?.jsonPrimitive?.contentOrNull ?: "rpc error",
    )
  }

  private fun writeJsonLine(element: JsonElement) {
    synchronized(output) {
      output.write(json.encodeToString(JsonElement.serializer(), element).toByteArray(StandardCharsets.UTF_8))
      output.write('\n'.code)
      output.flush()
    }
  }

  private fun writeJsonFrame(element: JsonElement) {
    val payload = (json.encodeToString(JsonElement.serializer(), element) + "\n")
      .toByteArray(StandardCharsets.UTF_8)
    writeFrame(output, Frame(0, FrameKind.DATA, payload))
  }

  private fun writeFrameQuietly(frame: Frame) {
    runCatching { writeFrame(output, frame) }
  }

  private fun readLine(): String? {
    val out = ByteArrayOutputStream()
    while (true) {
      val b = input.read()
      if (b < 0) return if (out.size() == 0) null else out.toString(StandardCharsets.UTF_8.name())
      if (b == '\n'.code) break
      if (b == '\r'.code) continue
      out.write(b)
      if (out.size() > 1_048_576) throw ProtocolException("line too long")
    }
    return out.toString(StandardCharsets.UTF_8.name())
  }

  private fun sendCredit(channel: Int, bytes: Int) {
    writeFrameQuietly(Frame(channel, FrameKind.CREDIT, encodeCredit(bytes)))
  }

  private fun writeChannel(channel: Int, bytes: ByteArray) {
    writeFrameQuietly(Frame(channel, FrameKind.DATA, bytes))
  }

  private fun resizeChannel(channel: Int, cols: Int, rows: Int) {
    writeFrameQuietly(Frame(channel, FrameKind.RESIZE, encodeResize(cols, rows)))
  }

  private fun closeChannel(channel: Int) {
    channelHandlers.remove(channel)
    writeFrameQuietly(Frame(channel, FrameKind.CLOSE, ByteArray(0)))
  }

  override fun close() {
    if (closed) return
    closed = true
    readerJob?.cancel()
    pending.values.forEach { it.completeExceptionally(HcClosedException("client closed")) }
    pending.clear()
    channelHandlers.clear()
    synchronized(orphanLock) {
      orphanFrames.clear()
      orphanBytes = 0
    }
    runCatching { output.close() }
    runCatching { input.close() }
  }

  private sealed class AttachOutbound {
    data class Data(val bytes: ByteArray) : AttachOutbound()
    data class Credit(val bytes: Int) : AttachOutbound()
    data class Resize(val cols: Int, val rows: Int) : AttachOutbound()
    data object Close : AttachOutbound()
  }

  inner class AttachStream internal constructor(val channel: Int) : AutoCloseable {
    private val messages = Channel<AttachEvent>(capacity = 1024)
    private val outbound = Channel<AttachOutbound>(capacity = 512)
    val events: Flow<AttachEvent> = messages.receiveAsFlow()

    init {
      registerChannel(channel) { frame ->
        when (frame.kind) {
          FrameKind.DATA -> {
            if (messages.trySend(AttachEvent.Data(frame.payload)).isFailure) {
              // Slow consumer: close rather than silently corrupting the stream.
              messages.trySend(AttachEvent.Closed("terminal renderer fell behind"))
            }
          }
          FrameKind.CLOSE -> {
            channelHandlers.remove(channel)
            messages.trySend(AttachEvent.Closed(frame.payload.toString(StandardCharsets.UTF_8).ifBlank { null }))
            messages.close()
          }
          FrameKind.RESIZE -> Unit
          FrameKind.FOCUS -> Unit
          FrameKind.FACTS -> Unit
          FrameKind.CREDIT -> Unit
        }
      }
      scope.launch(Dispatchers.IO) {
        var pending: AttachOutbound? = null
        while (true) {
          val item = pending ?: outbound.receiveCatching().getOrNull() ?: break
          pending = null
          try {
            when (item) {
              is AttachOutbound.Data -> {
                val batch = ByteArrayOutputStream()
                batch.write(item.bytes)
                while (true) {
                  val next = outbound.tryReceive().getOrNull() ?: break
                  if (next is AttachOutbound.Data) {
                    batch.write(next.bytes)
                  } else {
                    pending = next
                    break
                  }
                }
                writeChannel(channel, batch.toByteArray())
              }
              is AttachOutbound.Credit -> {
                var total = item.bytes
                while (true) {
                  val next = outbound.tryReceive().getOrNull() ?: break
                  if (next is AttachOutbound.Credit) {
                    total = (total + next.bytes).coerceAtMost(Int.MAX_VALUE)
                  } else {
                    pending = next
                    break
                  }
                }
                sendCredit(channel, total)
              }
              is AttachOutbound.Resize -> resizeChannel(channel, item.cols, item.rows)
              AttachOutbound.Close -> closeChannel(channel)
            }
          } catch (_: Throwable) {
            messages.trySend(AttachEvent.Closed("terminal write failed"))
            break
          }
        }
      }
    }

    fun write(bytes: ByteArray) {
      if (bytes.isEmpty()) return
      if (outbound.trySend(AttachOutbound.Data(bytes)).isFailure) {
        messages.trySend(AttachEvent.Closed("input queue is full"))
      }
    }

    fun acknowledge(bytes: Int) {
      if (bytes > 0) outbound.trySend(AttachOutbound.Credit(bytes))
    }

    fun resize(cols: Int, rows: Int) {
      if (cols in 1..65535 && rows in 1..65535) {
        outbound.trySend(AttachOutbound.Resize(cols, rows))
      }
    }

    override fun close() {
      outbound.trySend(AttachOutbound.Close)
      outbound.close()
      messages.close()
    }
  }

  sealed interface AttachEvent {
    data class Data(val bytes: ByteArray) : AttachEvent
    data class Closed(val reason: String?) : AttachEvent
  }

  companion object {
    private const val MAX_ORPHAN_BYTES_PER_CHANNEL = 8 * 1024 * 1024
    private const val MAX_ORPHAN_BYTES_TOTAL = 32 * 1024 * 1024
    private const val MAX_ORPHAN_CHANNELS = 1024

    val ALLOWED_METHODS: Set<String> = setOf(
      "events.subscribe",
      "doc.get",
      "terminal.list",
      "terminal.attach",
      "terminal.run",
    )
  }
}
