package revolut

import io.ktor.server.engine.ApplicationEngine
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.client.response.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import org.testng.Assert.assertEquals
import org.testng.annotations.AfterClass
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test
import java.util.concurrent.TimeUnit


class ServiceTest {

    private lateinit var accountService: AccountService
    private lateinit var server: ApplicationEngine
    private lateinit var client: HttpClient

    private val port = 8081
    private val baseUrl = "http://localhost:$port"

    @BeforeClass
    fun init() {
        accountService = AccountService()
        server = createServer(accountService, port)
        server.start(wait = false)

        client = HttpClient {
            install(JsonFeature)
        }
    }

    @AfterClass
    fun close() {
        try {
            client.close()
        } finally {
            server.stop(100L, 3000L, TimeUnit.MILLISECONDS)
        }
    }

    @Test
    fun getWrongId() = runBlocking {
        val response = client.get<HttpResponse>("$baseUrl/accounts/x")
        assertEquals(response.status.value, 400)
    }

    @Test
    fun getNonExistent() = runBlocking {
        val response = client.get<HttpResponse>("$baseUrl/accounts/1000000")
        assertEquals(response.status.value, 404)
    }

    @Test
    fun getExistent() = runBlocking {
        val id1 = accountService.create(1000L)
        val response = client.get<HttpResponse>("$baseUrl/accounts/$id1")
        assertEquals(response.status.value, 200)
        val account = response.receive<AccountResponse>()
        assertEquals(account.amount, 1000L)
    }

    @Test
    fun createWrongBody() = runBlocking {
        val response = client.post<HttpResponse> {
            url("$baseUrl/accounts")
            contentType(ContentType.Application.Json)
            body = mapOf<String, Any>()
        }
        assertEquals(response.status.value, 400)
    }

    @Test
    fun create() = runBlocking {
        val response = client.post<HttpResponse> {
            url("$baseUrl/accounts")
            contentType(ContentType.Application.Json)
            body = AccountCreateRequest(2000L)
        }
        assertEquals(response.status.value, 201)
        val account = response.receive<AccountCreateResponse>()
        assertEquals(accountService.get(account.id), 2000L)
    }

    @Test
    fun transferWrongBody() = runBlocking {
        val response = client.post<HttpResponse> {
            url("$baseUrl/transfer")
            contentType(ContentType.Application.Json)
            body = mapOf<String, Any>()
        }
        assertEquals(response.status.value, 400)
    }

    @Test
    fun transfer() = runBlocking {
        val id1 = accountService.create(100L)
        val id2 = accountService.create(100L)
        val response = client.post<HttpResponse> {
            url("$baseUrl/transfer")
            contentType(ContentType.Application.Json)
            body = TransferRequest(id1, id2, 50L)
        }
        assertEquals(response.status.value, 204)
    }
}