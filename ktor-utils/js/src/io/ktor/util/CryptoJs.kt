package io.ktor.util

import kotlinx.coroutines.*
import org.khronos.webgl.*
import kotlin.js.*

private const val NONCE_SIZE_IN_BYTES = 8

/**
 * Generates a nonce string.
 */
@InternalAPI
actual fun generateNonce(): String {
    val buffer = ByteArray(NONCE_SIZE_IN_BYTES)
    return hex(buffer) // crypto.getRandomValues(buffer))
}

/**
 * Create [Digest] from specified hash [name].
 */
@InternalAPI
actual fun Digest(name: String): Digest = object : Digest {
    private val state = mutableListOf<ByteArray>()
    override fun plusAssign(bytes: ByteArray) {
        state += bytes
    }

    override fun reset() {
        state.clear()
    }

    override suspend fun build(): ByteArray {
        val snapshot = state.reduce { a, b -> a + b }
        val digestBuffer = crypto.subtle.digest(name, snapshot).asDeferred().await()
        val digestView = DataView(digestBuffer)
        return ByteArray(digestView.byteLength) { digestView.getUint8(it) }
    }
}

private val crypto: Crypto = if (PlatformUtils.IS_NODE) js("require('crypto')") else js("crypto")

private external class Crypto {
    val subtle: SubtleCrypto

    fun getRandomValues(array: ByteArray): ByteArray
}

private external class SubtleCrypto {
    fun digest(algoName: String, buffer: ByteArray): Promise<ArrayBuffer>
}

/**
 * Compute SHA-1 hash for the specified [bytes]
 */
@KtorExperimentalAPI
actual fun sha1(bytes: ByteArray): ByteArray = error("sha1 currently is not supported in ktor-js")
