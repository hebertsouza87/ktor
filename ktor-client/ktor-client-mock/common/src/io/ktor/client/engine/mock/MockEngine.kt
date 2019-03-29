package io.ktor.client.engine.mock

import io.ktor.client.engine.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * [HttpClientEngine] for writing tests without network.
 */
class MockEngine(
    override val config: MockEngineConfig
) : HttpClientEngine {
    private var invocationCount = 0
    private val _requestsHistory: MutableList<HttpRequestData> = mutableListOf()
    private val _responseHistory: MutableList<HttpResponseData> = mutableListOf()
    private val contextState = CompletableDeferred<Unit>()

    init {
        check(config.requestHandlers.size > 0) {
            "No request handler provided in [MockEngineConfig], please provide at least one."
        }
    }

    /**
     * History of executed requests.
     */
    val requestHistory: List<HttpRequestData> get() = _requestsHistory

    /**
     * History of sent responses.
     */
    val responseHistory: List<HttpResponseData> get() = _responseHistory

    override val dispatcher: CoroutineDispatcher = Dispatchers.Unconfined

    override val coroutineContext: CoroutineContext = dispatcher + contextState

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        if (invocationCount >= config.requestHandlers.size) error("Unhandled ${data.url}")
        val handler = config.requestHandlers[invocationCount]

        invocationCount += 1
        if (config.reuseHandlers) {
            invocationCount %= config.requestHandlers.size
        }


        val response = handler(data)

        _requestsHistory.add(data)
        _responseHistory.add(response)

        return response
    }

    @Suppress("KDocMissingDocumentation")
    override fun close() {
        contextState.complete(Unit)
    }

    companion object : HttpClientEngineFactory<MockEngineConfig> {
        override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine =
            MockEngine(MockEngineConfig().apply(block))

        /**
         * Create [MockEngine] instance with single request handler.
         */
        operator fun invoke(handler: suspend (HttpRequestData) -> HttpResponseData): MockEngine =
            MockEngine(MockEngineConfig().apply {
                requestHandlers.add(handler)
            })
    }
}
