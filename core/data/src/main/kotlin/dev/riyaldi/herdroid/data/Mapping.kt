package dev.riyaldi.herdroid.data

import dev.riyaldi.herdroid.domain.model.Agent
import dev.riyaldi.herdroid.domain.model.AgentState
import dev.riyaldi.herdroid.domain.model.Machine
import dev.riyaldi.herdroid.domain.model.Workspace
import dev.riyaldi.herdroid.domain.model.WorkspaceTab
import dev.riyaldi.herdroid.mirror.Mirror
import dev.riyaldi.herdroid.protocol.TerminalInfo

internal fun TerminalInfo.toAgent(machine: Machine): Agent {
  val agentState = when {
    state == "finished" -> AgentState.FINISHED
    status?.state == "blocked" -> AgentState.NEEDS_YOU
    status?.state == "working" -> AgentState.WORKING
    else -> AgentState.IDLE
  }
  return Agent(
    address = address,
    machineId = machine.id,
    machineName = machine.name,
    name = name.orEmpty(),
    title = title,
    cwd = cwd,
    state = agentState,
    message = status?.message.orEmpty(),
    progress = status?.progress,
  )
}

internal fun TerminalInfo.toAgentOrNull(machine: Machine): Agent? =
  runCatching { toAgent(machine) }.getOrNull()

internal fun Mirror.toWorkspaces(): List<Workspace> = spaces.map { space ->
  Workspace(
    name = space.displayName,
    tabs = space.tabs.map { tab ->
      WorkspaceTab(
        name = tab.displayName,
        agentAddresses = tab.terminals,
      )
    },
  )
}
