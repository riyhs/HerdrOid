package dev.riyaldi.herdroid.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import com.jcraft.jsch.UserInfo
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

data class SshTarget(
  val host: String,
  val port: Int = 22,
  val user: String,
  val command: String,
  val knownHostKey: String? = null,
)

class SshException(message: String, cause: Throwable? = null) : Exception(message, cause)
class HostKeyChangedException(val fingerprint: String) :
  Exception("host key changed; expected a different key, got $fingerprint")

class SshConnection internal constructor(
  private val session: Session,
  private val channel: ChannelExec,
  val input: InputStream,
  val output: OutputStream,
  private val stderr: InputStream,
  private val closeSocket: () -> Unit,
) : AutoCloseable {
  init {
    // Drain stderr so a chatty remote command cannot block the SSH channel.
    thread(name = "herdroid-ssh-stderr", isDaemon = true) {
      val buffer = ByteArray(4096)
      runCatching {
        while (stderr.read(buffer) >= 0) Unit
      }
    }
  }

  fun abort() {
    // A blocked PipedInputStream read only wakes on the writer-side EOF signal.
    runCatching {
      val receivedLast = java.io.PipedInputStream::class.java
        .getDeclaredMethod("receivedLast")
        .apply { isAccessible = true }
      receivedLast.invoke(input)
    }
    runCatching { input.close() }
    runCatching { output.close() }
    runCatching { closeSocket() }
    runCatching { channel.disconnect() }
    runCatching { session.disconnect() }
  }

  override fun close() {
    abort()
  }
}

object SshConnector {
  private const val CONNECT_TIMEOUT_MS = 20_000

  fun command(hcPath: String, session: String): String {
    val path = hcPath.ifBlank { "hc" }
    require(path.matches(Regex("[A-Za-z0-9_./~+-]+"))) { "invalid hc path" }
    require(session.matches(Regex("[A-Za-z0-9_.-]{1,64}"))) { "invalid hc session" }
    // Both values are restricted to shell-safe characters, so this works in
    // POSIX shells and in Windows OpenSSH's default cmd.exe shell.
    return "$path connect --session $session"
  }

  fun probeHostKey(
    target: SshTarget,
    privateKey: ByteArray,
  ): Pair<String, String> {
    JschConfig.configure()
    val seen = AtomicReference<String?>(null)
    val socketFactory = NoDelaySocketFactory()
    val jsch = JSch().apply {
      addIdentity("herdroid", privateKey, null, null)
      hostKeyRepository = PinnedHostKey(target.knownHostKey) { seen.set(it) }
    }
    val session = jsch.getSession(target.user, target.host, target.port)
    configureSession(session, socketFactory)
    try {
      session.connect(CONNECT_TIMEOUT_MS)
      val key = seen.get() ?: throw SshException("server did not present a host key")
      return key to fingerprint(key)
    } catch (e: JSchException) {
      if (target.knownHostKey != null && seen.get() != null && seen.get() != target.knownHostKey) {
        throw HostKeyChangedException(fingerprint(seen.get()!!))
      }
      throw SshException(e.message ?: "ssh probe failed", e)
    } finally {
      runCatching { session.disconnect() }
    }
  }

  fun connect(
    target: SshTarget,
    privateKey: ByteArray,
  ): SshConnection {
    JschConfig.configure()
    val seen = AtomicReference<String?>(null)
    val socketFactory = NoDelaySocketFactory()
    val jsch = JSch().apply {
      addIdentity("herdroid", privateKey, null, null)
      hostKeyRepository = PinnedHostKey(target.knownHostKey) { seen.set(it) }
    }
    val session = jsch.getSession(target.user, target.host, target.port)
    configureSession(session, socketFactory)
    try {
      session.connect(CONNECT_TIMEOUT_MS)
    } catch (e: JSchException) {
      if (target.knownHostKey != null && seen.get() != null && seen.get() != target.knownHostKey) {
        throw HostKeyChangedException(fingerprint(seen.get()!!))
      }
      throw SshException(e.message ?: "ssh connect failed", e)
    }

    val channel = try {
      (session.openChannel("exec") as ChannelExec).apply {
        setCommand(target.command)
        connect(CONNECT_TIMEOUT_MS)
      }
    } catch (e: Throwable) {
      runCatching { session.disconnect() }
      throw SshException(e.message ?: "ssh exec failed", e)
    }

    seen.get() ?: run {
      runCatching { channel.disconnect() }
      runCatching { session.disconnect() }
      throw SshException("server did not present a host key")
    }
    // connect(timeout) leaves SO_TIMEOUT set on some JSch versions; clear it
    // for the long-lived framed connection.
    runCatching { session.setTimeout(0) }
    return SshConnection(
      session = session,
      channel = channel,
      input = channel.inputStream,
      output = channel.outputStream,
      stderr = channel.errStream,
      closeSocket = socketFactory::close,
    )
  }

  fun fingerprint(hostKeyBase64: String): String {
    val raw = runCatching { Base64.getDecoder().decode(hostKeyBase64) }.getOrElse { return "SHA256:?" }
    val digest = MessageDigest.getInstance("SHA-256").digest(raw)
    return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
  }

  private fun configureSession(session: Session, socketFactory: SocketFactory) {
    session.setConfig("PreferredAuthentications", "publickey")
    session.setConfig("StrictHostKeyChecking", "yes")
    session.userInfo = SilentUserInfo
    session.setServerAliveInterval(15_000)
    session.serverAliveCountMax = 3
    session.setSocketFactory(socketFactory)
  }

  private class NoDelaySocketFactory : SocketFactory {
    @Volatile
    private var socket: Socket? = null

    override fun createSocket(host: String, port: Int): Socket =
      Socket().apply {
        tcpNoDelay = true
        keepAlive = true
        connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        this@NoDelaySocketFactory.socket = this
      }

    fun close() {
      runCatching { socket?.close() }
    }

    override fun getInputStream(socket: Socket): InputStream = socket.getInputStream()
    override fun getOutputStream(socket: Socket): OutputStream = socket.getOutputStream()
  }

  private class PinnedHostKey(
    private val expected: String?,
    private val seen: (String) -> Unit,
  ) : HostKeyRepository {
    override fun check(host: String?, key: ByteArray?): Int {
      if (key == null) return HostKeyRepository.NOT_INCLUDED
      val encoded = Base64.getEncoder().encodeToString(key)
      seen(encoded)
      return if (expected == null || expected == encoded) HostKeyRepository.OK
      else HostKeyRepository.CHANGED
    }

    override fun add(hostkey: HostKey?, ui: UserInfo?) = Unit
    override fun remove(host: String?, type: String?) = Unit
    override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
    override fun getKnownHostsRepositoryID(): String = "herdroid"
    override fun getHostKey(): Array<HostKey> = emptyArray()
    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
  }

  private object SilentUserInfo : UserInfo {
    override fun getPassphrase(): String? = null
    override fun getPassword(): String? = null
    override fun promptPassword(message: String?): Boolean = false
    override fun promptPassphrase(message: String?): Boolean = false
    override fun promptYesNo(message: String?): Boolean = false
    override fun showMessage(message: String?) = Unit
  }
}
