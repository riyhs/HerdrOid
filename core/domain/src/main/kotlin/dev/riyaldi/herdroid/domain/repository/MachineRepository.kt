package dev.riyaldi.herdroid.domain.repository

import dev.riyaldi.herdroid.domain.model.AppSnapshot
import dev.riyaldi.herdroid.domain.model.Machine
import kotlinx.coroutines.flow.StateFlow

interface MachineRepository {
  val state: StateFlow<AppSnapshot>

  fun addMachine(machine: Machine)

  fun removeMachine(machineId: String)

  fun connect(machineId: String)

  fun disconnect(machineId: String)

  fun trustHost()

  fun rejectHost()

  fun forgetHostKey(machineId: String)

  fun openTerminal(machineId: String, address: String, title: String)

  fun closeTerminal()

  fun startTerminal(
    machineId: String,
    command: String,
    cwd: String,
    spaceId: String = "",
    tabId: String = "",
  )

  fun loadDemo()

  fun resetDeviceKey()

  fun deleteAllData()

  fun clearError()

  fun close()
}
