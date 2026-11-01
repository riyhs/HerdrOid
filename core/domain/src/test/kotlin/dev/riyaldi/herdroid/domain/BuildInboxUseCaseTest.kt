package dev.riyaldi.herdroid.domain

import dev.riyaldi.herdroid.domain.model.Agent
import dev.riyaldi.herdroid.domain.model.AgentState
import dev.riyaldi.herdroid.domain.model.Machine
import dev.riyaldi.herdroid.domain.model.MachineState
import dev.riyaldi.herdroid.domain.usecase.BuildInboxUseCase
import org.junit.Assert.assertEquals
import org.junit.Test

class BuildInboxUseCaseTest {
  private val useCase = BuildInboxUseCase()
  private val machineA = Machine("a", "Alpha", "a.example", user = "me")
  private val machineB = Machine("b", "Beta", "b.example", user = "me")

  private fun agent(id: String, machine: Machine, state: AgentState) = Agent(
    address = id,
    machineId = machine.id,
    machineName = machine.name,
    name = id,
    title = id,
    cwd = "/work/$id",
    state = state,
  )

  @Test
  fun groupsAcrossMachines() {
    val inbox = useCase(
      listOf(
        MachineState(
          machineA,
          agents = listOf(
            agent("a-idle", machineA, AgentState.IDLE),
            agent("a-blocked", machineA, AgentState.NEEDS_YOU),
          ),
        ),
        MachineState(
          machineB,
          agents = listOf(
            agent("b-work", machineB, AgentState.WORKING),
            agent("b-done", machineB, AgentState.FINISHED),
          ),
        ),
      ),
    )

    assertEquals(1, inbox.needsYou.size)
    assertEquals(1, inbox.working.size)
    assertEquals(1, inbox.idle.size)
    assertEquals(1, inbox.finished.size)
    assertEquals("a-blocked", inbox.needsYou.single().address)
  }

  @Test
  fun filtersToOneMachine() {
    val inbox = useCase(
      listOf(
        MachineState(machineA, agents = listOf(agent("a", machineA, AgentState.IDLE))),
        MachineState(machineB, agents = listOf(agent("b", machineB, AgentState.IDLE))),
      ),
      machineFilter = "b",
    )
    assertEquals(listOf("b"), inbox.idle.map { it.address })
  }
}
