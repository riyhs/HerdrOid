package dev.riyaldi.herdroid.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.riyaldi.herdroid.domain.model.Agent
import dev.riyaldi.herdroid.domain.model.AgentState
import dev.riyaldi.herdroid.domain.model.ConnectionStatus

@Composable
fun AgentCard(
  agent: Agent,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val needsYou = agent.state == AgentState.NEEDS_YOU
  val colors = if (needsYou) {
    CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
      contentColor = MaterialTheme.colorScheme.onErrorContainer,
    )
  } else {
    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
  }

  Card(
    modifier = modifier
      .fillMaxWidth()
      .then(if (agent.isLive) Modifier.clickable(onClick = onClick) else Modifier),
    colors = colors,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.Top,
    ) {
      StatusDot(
        status = if (needsYou) ConnectionStatus.ERROR else ConnectionStatus.ONLINE,
        modifier = Modifier.padding(top = 6.dp),
      )
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = agent.displayName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          if (needsYou) {
            Text(
              text = "NEEDS YOU",
              modifier = Modifier.padding(start = 8.dp),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
        Text(
          text = listOf(agent.machineName, agent.folder).filter { it.isNotBlank() }.joinToString(" · "),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (agent.message.isNotBlank()) {
          Text(
            text = agent.message,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
        if (agent.state == AgentState.WORKING && agent.progress != null) {
          LinearProgressIndicator(
            progress = { agent.progress!! / 100f },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
          )
        }
      }
      Text(
        text = when (agent.state) {
          AgentState.NEEDS_YOU -> "Reply"
          AgentState.WORKING -> "View"
          AgentState.IDLE -> "Open"
          AgentState.FINISHED -> "Finished"
        },
        style = MaterialTheme.typography.labelMedium,
        color = if (agent.isLive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
      )
    }
  }
}
