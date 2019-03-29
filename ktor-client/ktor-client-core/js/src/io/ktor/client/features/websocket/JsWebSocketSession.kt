package io.ktor.client.features.websocket

import io.ktor.http.cio.websocket.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.io.core.*
import org.khronos.webgl.*
import org.w3c.dom.*
import kotlin.coroutines.*

internal class JsWebSocketSession(
    override val coroutineContext: CoroutineContext,
    private val websocket: WebSocket
) : DefaultWebSocketSession {
    private val _closeReason: CompletableDeferred<CloseReason> = CompletableDeferred()
    private val _incoming: Channel<Frame> = Channel(Channel.UNLIMITED)
    private val _outgoing: Channel<Frame> = Channel(Channel.UNLIMITED)

    override val incoming: ReceiveChannel<Frame> = _incoming
    override val outgoing: SendChannel<Frame> = _outgoing

    override val closeReason: Deferred<CloseReason?> = _closeReason

    init {
        websocket.binaryType = BinaryType.ARRAYBUFFER

        websocket.onmessage = { event ->
            launch {
                val data = event.data

                val frame: Frame = when (data) {
                    is ArrayBuffer -> Frame.Binary(false, Int8Array(data) as ByteArray)
                    is String -> Frame.Text(event.data as String)
                    else -> error("Unknown frame type: ${event.type}")
                }

                _incoming.offer(frame)
            }
        }

        websocket.onerror = {
            _incoming.close(WebSocketException("$it"))
            _outgoing.cancel()
        }

        websocket.onclose = {
            launch {
                val event = it as CloseEvent
                _incoming.send(Frame.Close(CloseReason(event.code, event.reason)))
                _incoming.close()

                _outgoing.cancel()
            }
        }

        launch {
            _outgoing.consumeEach {
                when (it.frameType) {
                    FrameType.TEXT -> {
                        val text = it.data
                        websocket.send(String(text))
                    }
                    FrameType.BINARY -> {
                        val source = it.data as Int8Array
                        val frameData = source.buffer.slice(
                            source.byteOffset, source.byteOffset + source.byteLength
                        )

                        websocket.send(frameData)
                    }
                    FrameType.CLOSE -> {
                        val data = buildPacket { it.data }
                        websocket.close(data.readShort(), data.readText())
                    }
                }
            }
        }
    }

    override suspend fun flush() {
    }

    override fun terminate() {
        _incoming.cancel()
        _outgoing.cancel()
        websocket.close()
    }

    @KtorExperimentalAPI
    override suspend fun close(cause: Throwable?) {
        val reason = cause?.let {
            CloseReason(CloseReason.Codes.UNEXPECTED_CONDITION, cause.message ?: "")
        } ?: CloseReason(CloseReason.Codes.NORMAL, "OK")

        _incoming.send(Frame.Close(reason))
    }
}
