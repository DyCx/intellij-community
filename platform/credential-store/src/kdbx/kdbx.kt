// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore.kdbx

import com.google.common.io.LittleEndianDataInputStream
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.io.inputStream
import org.bouncycastle.crypto.SkippingStreamCipher
import org.bouncycastle.crypto.engines.Salsa20Engine
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.io.InputStream
import java.nio.file.Path
import java.security.MessageDigest
import java.util.*
import java.util.zip.GZIPInputStream

// https://gist.github.com/lgg/e6ccc6e212d18dd2ecd8a8c116fb1e45

@Throws(IncorrectMasterPasswordException::class)
internal fun loadKdbx(file: Path, credentials: KeePassCredentials): KeePassDatabase {
  return file.inputStream().buffered().use { readKeePassDatabase(credentials, it) }
}

private fun readKeePassDatabase(credentials: KeePassCredentials, inputStream: InputStream): KeePassDatabase {
  val kdbxHeader = KdbxHeader()
  kdbxHeader.readKdbxHeader(inputStream)
  val decryptedInputStream = kdbxHeader.createDecryptedStream(credentials.key, inputStream)

  val startBytes = ByteArray(32)
  LittleEndianDataInputStream(decryptedInputStream).readFully(startBytes)
  if (!Arrays.equals(startBytes, kdbxHeader.streamStartBytes)) {
    throw IncorrectMasterPasswordException()
  }

  var resultInputStream: InputStream = HashedBlockInputStream(decryptedInputStream)
  if (kdbxHeader.compressionFlags == KdbxHeader.CompressionFlags.GZIP) {
    resultInputStream = GZIPInputStream(resultInputStream)
  }
  val element = JDOMUtil.load(resultInputStream)
  element.getChild(KdbxDbElementNames.root)?.let { rootElement ->
    XmlProtectedValueTransformer(createSalsa20StreamCipher(kdbxHeader.protectedStreamKey)).processEntries(rootElement)
  }
  return KeePassDatabase(element)
}

internal class KdbxPassword(password: ByteArray) : KeePassCredentials {
  override val key: ByteArray

  init {
    val md = sha256MessageDigest()
    key = md.digest(md.digest(password))
  }
}

internal fun sha256MessageDigest() = MessageDigest.getInstance("SHA-256")

// 0xE830094B97205D2A
private val SALSA20_IV = byteArrayOf(-24, 48, 9, 75, -105, 32, 93, 42)

internal fun createSalsa20StreamCipher(key: ByteArray): SkippingStreamCipher {
  val streamCipher = Salsa20Engine()
  val keyParameter = KeyParameter(sha256MessageDigest().digest(key))
  streamCipher.init(true /* doesn't matter, Salsa20 encryption and decryption is completely symmetrical */, ParametersWithIV(keyParameter, SALSA20_IV))
  return streamCipher
}