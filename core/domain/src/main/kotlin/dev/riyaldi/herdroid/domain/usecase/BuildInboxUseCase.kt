package dev.riyaldi.herdroid.domain.usecase

import dev.riyaldi.herdroid.domain.model.Agent
import dev.riyaldi.herdroid.domain.model.AgentState
import dev.riyaldi.herdroid.domain.model.Inbox
import dev.riyaldi.herdroid.domain.model.MachineState

class BuildInboxUseCase {
  operator fun invoke(machines: List<MachineState>, machineFilter: String? = null): Inbox {
    val agents = machines
      .asSequence()
      .filter { machineFilter == null || it.machine.id == machineFilter }
      .flatMap { it.agents.asSequence() }
      .sortedWith(
        compareBy<Agent>({ it.machineName.lowercase() }, { it.folder.lowercase() }, { it.address }),
      )
      .toList()

    return Inbox(
      needsYou = agents.filter { it.state == AgentState.NEEDS_YOU },
      working = agents.filter { it.state == AgentState.WORKING },
      idle = agents.filter { it.state == AgentState.IDLE },
      finished = agents.filter { it.state == AgentState.FINISHED },
    )
  }
}
