package io.ktor.client.tests

import io.ktor.client.features.auth.*
import io.ktor.client.features.auth.providers.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.features.logging.*
import io.ktor.client.features.websocket.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import kotlinx.serialization.Serializable
import kotlin.test.*

@Serializable
class TestClass(val test: String)

class CombinationsTest {

    @Test
    @Ignore
    fun testAuthJsonLogging() = clientsTest {
        config {
            Auth {
                basic {
                    realm = "my-server"
                    username = "user1"
                    password = "Password1"
                }
            }
            Json {
                serializer = KotlinxSerializer()
            }
            Logging {
                level = LogLevel.ALL
                logger = Logger.EMPTY
            }
        }

        test { client ->
            val code = client.post<HttpStatusCode>("$TEST_SERVER/auth/basic") {
                contentType(ContentType.Application.Json)
                body = TestClass("text")
            }

            assertEquals(HttpStatusCode.OK, code)
        }
    }

    @Test
    @Ignore
    fun testWebsocketWithAuth() = clientsTest(skipMissingPlatforms = true) {
        config {
            Logging {
                level = LogLevel.INFO
                logger = Logger.EMPTY
            }
            Auth {
                basic {
                    realm = "my-server"
                    username = "user1"
                    password = "Password1"
                }
            }

            install(WebSockets)
        }

        test { client ->
            client.ws("ws://127.0.0.1:8080/auth/basic/ws/echo") {
                send("ping")

                val pong = incoming.receive()
                assertTrue(pong is Frame.Text)
                assertEquals("ping", pong.readText())
            }
        }
    }
}
