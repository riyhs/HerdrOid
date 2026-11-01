@file:Suppress("DEPRECATION", "InlinedApi")

package dev.riyaldi.herdroid.ui.terminal

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager as AndroidClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString

@Suppress("DEPRECATION", "InlinedApi")
class SensitiveClipboardManager(context: Context) : ClipboardManager {
  private val manager = context.getSystemService(AndroidClipboardManager::class.java)

  override fun setText(annotatedString: AnnotatedString) {
    val clip = ClipData.newPlainText("plain text", annotatedString.text)
    clip.description.extras = PersistableBundle().apply {
      putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
    }
    manager.setPrimaryClip(clip)
  }

  override fun getText(): AnnotatedString? =
    manager.primaryClip
      ?.takeIf { it.itemCount > 0 }
      ?.getItemAt(0)
      ?.text
      ?.let { AnnotatedString(it.toString()) }
}
