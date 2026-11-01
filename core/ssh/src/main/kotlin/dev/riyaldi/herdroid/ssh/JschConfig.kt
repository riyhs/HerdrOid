package dev.riyaldi.herdroid.ssh

import com.jcraft.jsch.JSch

internal object JschConfig {
  fun configure() {
    // Android has no JDK EdDSA/XDH providers; use BouncyCastle through JSch.
    JSch.setConfig("keypairgen.eddsa", "com.jcraft.jsch.bc.KeyPairGenEdDSA")
    JSch.setConfig("keypairgen_fromprivate.eddsa", "com.jcraft.jsch.bc.KeyPairGenEdDSA")
    JSch.setConfig("ssh-ed25519", "com.jcraft.jsch.bc.SignatureEd25519")
    JSch.setConfig("xdh", "com.jcraft.jsch.bc.XDH")
  }
}
