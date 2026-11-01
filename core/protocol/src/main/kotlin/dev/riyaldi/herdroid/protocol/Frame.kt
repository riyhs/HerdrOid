package dev.riyaldi.herdroid.protocol

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

const val FRAME_HEADER_SIZE = 9
const val MAX_FRAME_PAYLOAD = 16 * 1024 * 1024

enum class FrameKind(val wire: Int) {
  DATA(0),
  CREDIT(1),
  CLOSE(2),
  RESIZE(3),
  FOCUS(4),
  FACTS(5);

  companion object {
    fun fromWire(value: Int): FrameKind =
      entries.firstOrNull { it.wire == value }
        ?: throw ProtocolException("unknown frame kind $value")
  }
}

data class Frame(val channel: Int, val kind: FrameKind, val payload: ByteArray)

class ProtocolException(message: String) : Exception(message)

fun readFrame(input: InputStream, maxPayload: Int = MAX_FRAME_PAYLOAD): Frame {
  val header = ByteArray(FRAME_HEADER_SIZE)
  var read = 0
  while (read < header.size) {
    val n = input.read(header, read, header.size - read)
    if (n < 0) throw EOFException("stream ended before frame header")
    read += n
  }
  val channel = readU32(header, 0).toInt()
  val kind = FrameKind.fromWire(header[4].toInt() and 0xff)
  val length = readU32(header, 5)
  if (length > maxPayload) throw ProtocolException("frame payload $length exceeds $maxPayload")
  val payload = ByteArray(length.toInt())
  var payloadRead = 0
  while (payloadRead < payload.size) {
    val n = input.read(payload, payloadRead, payload.size - payloadRead)
    if (n < 0) throw EOFException("stream ended inside frame payload")
    payloadRead += n
  }
  return Frame(channel, kind, payload)
}

fun writeFrame(output: OutputStream, frame: Frame) {
  val header = ByteArray(FRAME_HEADER_SIZE)
  writeU32(header, 0, frame.channel.toLong())
  header[4] = frame.kind.wire.toByte()
  writeU32(header, 5, frame.payload.size.toLong())
  synchronized(output) {
    output.write(header)
    output.write(frame.payload)
    output.flush()
  }
}

fun encodeCredit(bytes: Int): ByteArray = ByteArray(4).also { writeU32(it, 0, bytes.toLong()) }

fun encodeResize(cols: Int, rows: Int): ByteArray = ByteArray(4).also {
  it[0] = (cols and 0xff).toByte()
  it[1] = ((cols ushr 8) and 0xff).toByte()
  it[2] = (rows and 0xff).toByte()
  it[3] = ((rows ushr 8) and 0xff).toByte()
}

internal fun readU32(bytes: ByteArray, offset: Int): Long =
  (bytes[offset].toLong() and 0xff) or
    ((bytes[offset + 1].toLong() and 0xff) shl 8) or
    ((bytes[offset + 2].toLong() and 0xff) shl 16) or
    ((bytes[offset + 3].toLong() and 0xff) shl 24)

internal fun writeU32(bytes: ByteArray, offset: Int, value: Long) {
  bytes[offset] = (value and 0xff).toByte()
  bytes[offset + 1] = ((value ushr 8) and 0xff).toByte()
  bytes[offset + 2] = ((value ushr 16) and 0xff).toByte()
  bytes[offset + 3] = ((value ushr 24) and 0xff).toByte()
}
