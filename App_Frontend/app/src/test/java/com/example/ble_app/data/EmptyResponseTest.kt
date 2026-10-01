package com.example.ble_app.data

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.PUT
import java.net.InetSocketAddress

/** Endpoints like PUT /api/devices/me reply 204 with no body; the app must treat that as success. */
class EmptyResponseTest {
    private interface Api {
        @PUT("no-content")
        suspend fun asUnit(): Unit

        @PUT("no-content")
        suspend fun asResponse(): Response<Unit>

        @PUT("conflict")
        suspend fun conflict(): Response<Unit>
    }

    private lateinit var server: HttpServer
    private lateinit var api: Api

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/no-content") { exchange -> exchange.sendResponseHeaders(204, -1); exchange.close() }
            createContext("/conflict") { exchange ->
                val body = """{"message":"This phone is already linked to another account"}""".toByteArray()
                exchange.sendResponseHeaders(409, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        api = Retrofit.Builder()
            .baseUrl("http://127.0.0.1:${server.address.port}/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(Api::class.java)
    }

    @After
    fun stop() = server.stop(0)

    @Test
    fun plainUnitFailsOn204() = runBlocking {
        // Documents the original bug: Retrofit 2.9 returns a null body for 204, which Unit rejects.
        try {
            api.asUnit()
            fail("Expected Retrofit to reject the empty 204 body")
        } catch (e: KotlinNullPointerException) {
            assertTrue(e.message!!.contains("was null"))
        }
    }

    @Test
    fun responseUnitAccepts204() = runBlocking {
        api.asResponse().requireSuccess()
    }

    @Test
    fun requireSuccessThrowsHttpExceptionOnError() = runBlocking {
        try {
            api.conflict().requireSuccess()
            fail("Expected HttpException")
        } catch (e: HttpException) {
            assertTrue(e.code() == 409)
        }
    }
}
