package dev.riyaldi.herdroid.data

import dev.riyaldi.herdroid.domain.model.TerminalEvent
import dev.riyaldi.herdroid.domain.model.TerminalHandle
import dev.riyaldi.herdroid.protocol.HcClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class HcTerminalHandle(
  private val attach: HcClient.AttachStream,
) : TerminalHandle {
  override val events: Flow<TerminalEvent> = attach.events.map { event ->
    when (event) {
      is HcClient.AttachEvent.Data -> TerminalEvent.Data(event.bytes)
      is HcClient.AttachEvent.Closed -> TerminalEvent.Closed(event.reason)
    }
  }

  override fun write(bytes: ByteArray) = attach.write(bytes)

  override fun acknowledge(bytes: Int) = attach.acknowledge(bytes)

  override fun resize(cols: Int, rows: Int) = attach.resize(cols, rows)

  override fun close() = attach.close()
}
