package dev.riyaldi.herdroid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.riyaldi.herdroid.domain.model.ConnectionStatus

@Composable
fun StatusDot(status: ConnectionStatus, modifier: Modifier = Modifier) {
  val color = when (status) {
    ConnectionStatus.ONLINE -> Color(0xFF5ED6A0)
    ConnectionStatus.CONNECTING -> MaterialTheme.colorScheme.tertiary
    ConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
    ConnectionStatus.OFFLINE -> MaterialTheme.colorScheme.outline
  }
  Box(
    modifier = modifier
      .size(8.dp)
      .background(color, CircleShape),
  )
}

@Composable
fun StatusText(status: ConnectionStatus, modifier: Modifier = Modifier) {
  Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
    StatusDot(status)
    Text(
      text = when (status) {
        ConnectionStatus.ONLINE -> "Online"
        ConnectionStatus.CONNECTING -> "Connecting…"
        ConnectionStatus.ERROR -> "Needs attention"
        ConnectionStatus.OFFLINE -> "Offline"
      },
      modifier = Modifier,
      style = MaterialTheme.typography.labelMedium,
      color = when (status) {
        ConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
      },
    )
  }
}
