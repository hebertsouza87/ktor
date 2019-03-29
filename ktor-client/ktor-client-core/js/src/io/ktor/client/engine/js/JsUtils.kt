package io.ktor.client.engine.js

import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.http.content.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import kotlinx.io.core.*
import org.khronos.webgl.*
import org.w3c.fetch.*
import kotlin.coroutines.*

internal suspend fun HttpRequestData.toRaw(callContext: CoroutineContext): RequestInit {
    val jsHeaders = js("({})")
    mergeHeaders(this@toRaw.headers, this@toRaw.body) { key, value ->
        jsHeaders[key] = value
    }

    val bodyBytes = when (val content = body) {
        is OutgoingContent.ByteArrayContent -> content.bytes()
        is OutgoingContent.ReadChannelContent -> content.readFrom().readRemaining().readBytes()
        is OutgoingContent.WriteChannelContent -> {
            GlobalScope.writer(callContext) {
                content.writeTo(channel)
            }.channel.readRemaining().readBytes()
        }
        else -> null
    }

    return buildObject {
        method = this@toRaw.method.value
        headers = jsHeaders

        bodyBytes?.let { body = Uint8Array(it.toTypedArray()) }
    }
}

internal fun <T> buildObject(block: T.() -> Unit): T = (js("{}") as T).apply(block)
