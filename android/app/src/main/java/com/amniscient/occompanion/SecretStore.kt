// Copyright 2026 Amniscient. SPDX-License-Identifier: MIT
package com.amniscient.occompanion

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Password bytes live in the Android Keystore (AES-GCM) and nowhere else.
 * The ciphertext file is excluded from backup. Callers wipe the returned
 * CharArray after the request headers are built.
 */
object SecretStore {
    private const val ALIAS = "occompanion.server-password"
    private const val FILE = "server-password.bin"

    fun save(context: Context, password: CharArray) {
        val key = key()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val plain = String(password).toByteArray(Charsets.UTF_8)
        val ct = cipher.doFinal(plain)
        plain.fill(0)
        val iv = cipher.iv
        context.getFileStreamPath(FILE).outputStream().use { out ->
            out.write(iv.size)
            out.write(iv)
            out.write(ct)
        }
    }

    fun load(context: Context): CharArray? {
        val file = context.getFileStreamPath(FILE)
        if (!file.isFile) return null
        val bytes = file.readBytes()
        if (bytes.size < 2) return null
        val ivLen = bytes[0].toInt() and 0xff
        if (ivLen <= 0 || bytes.size < 1 + ivLen + 1) return null
        val iv = bytes.copyOfRange(1, 1 + ivLen)
        val ct = bytes.copyOfRange(1 + ivLen, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        val plain = cipher.doFinal(ct)
        val chars = String(plain, Charsets.UTF_8).toCharArray()
        plain.fill(0)
        return chars
    }

    fun clear(context: Context) {
        context.getFileStreamPath(FILE).delete()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getKey(ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }
}
