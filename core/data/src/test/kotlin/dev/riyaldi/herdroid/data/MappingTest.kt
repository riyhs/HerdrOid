package dev.riyaldi.herdroid.data

import dev.riyaldi.herdroid.domain.model.AgentState
import dev.riyaldi.herdroid.domain.model.Machine
import dev.riyaldi.herdroid.protocol.TerminalInfo
import dev.riyaldi.herdroid.protocol.TerminalStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MappingTest {
  private val machine = Machine("m", "Host", "example", user = "me")

  private fun info(state: String, status: String? = null) = TerminalInfo(
    address = "t1",
    name = "agent",
    title = "Agent",
    cwd = "/work/project",
    state = state,
    status = status?.let {
      TerminalStatus(it, "app", "Agent", "message", "permission", 40)
    },
    parent = null,
    labels = emptyMap(),
    keep = true,
    cols = 80,
    rows = 24,
    turn = 3,
  )

  @Test
  fun mapsBlockedAndFinishedStates() {
    assertEquals(AgentState.NEEDS_YOU, info("live", "blocked").toAgent(machine).state)
    assertEquals(AgentState.WORKING, info("live", "working").toAgent(machine).state)
    assertEquals(AgentState.IDLE, info("live", "idle").toAgent(machine).state)
    assertEquals(AgentState.FINISHED, info("finished", "done").toAgent(machine).state)
  }

  @Test
  fun mapsFolderAndMachine() {
    val agent = info("live").toAgent(machine)
    assertEquals("project", agent.folder)
    assertEquals("Host", agent.machineName)
    assertEquals("agent", agent.displayName)
  }
}
