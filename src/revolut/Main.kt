package revolut

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.LoggerFactory


fun main() {
    val service = AccountService()
    val server = createServer(service, 8080)
    server.start(wait = true)
}

fun createServer(service: AccountService, port: Int): ApplicationEngine {
    val log = LoggerFactory.getLogger("main")
    return embeddedServer(Netty, port = port) {
        install(ContentNegotiation) {
            jackson {
                enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            }
        }
        install(StatusPages) {
            exception<ClientException> { exception ->
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (exception.message ?: "")))
            }
            exception<Exception> { exception ->
                log.error("", exception)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to ("Internal Server Error")))
            }
        }
        routing {
            post("/accounts") {
                val request = try {
                    call.receive<AccountCreateRequest>()
                } catch (e: JsonProcessingException) {
                    throw ClientException("Wrong JSON request")
                }
                val id = service.create(request.amount)
                call.respond(HttpStatusCode.Created, AccountCreateResponse(id))
            }
            get("/accounts/{id}") {
                val id = call.parameters["id"]!!.let {
                    try {
                        it.toInt()
                    } catch (e: NumberFormatException) {
                        throw ClientException("Wrong account id: $it")
                    }
                }
                val amount = service.get(id)
                if (amount == null) {
                    call.respond(HttpStatusCode.NotFound, emptyMap<Any, Any>())
                } else {
                    call.respond(AccountResponse(amount))
                }
            }
            post("/transfer") {
                val request = try {
                    call.receive<TransferRequest>()
                } catch (e: JsonProcessingException) {
                    throw ClientException("Wrong JSON request")
                }
                service.transfer(request.from, request.to, request.amount)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

class AccountCreateRequest(
    val amount: Long
) {
    constructor() : this(0L)
}

class AccountCreateResponse(
    val id: Int
)

class AccountResponse(
    val amount: Long
)

class TransferRequest(
    val from: Int,
    val to: Int,
    val amount: Long
) {
    constructor() : this(0, 0, 0L)
}
