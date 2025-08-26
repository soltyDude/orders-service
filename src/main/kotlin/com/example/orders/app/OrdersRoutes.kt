package com.example.orders.app

import com.example.orders.adapters.db.ExposedOrderRepository
import com.example.orders.adapters.db.ExposedOutboxWriter
import com.example.orders.adapters.db.IdempotencyRepository
import com.example.orders.domain.model.Order
import com.example.orders.domain.model.OrderItem
import com.example.orders.domain.model.OrderStatus
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.security.MessageDigest
import java.util.UUID

fun Route.orderRoutes() {
    val ordersRepo = ExposedOrderRepository()
    val outbox = ExposedOutboxWriter()
    val idemRepo = IdempotencyRepository()
    val mapper = jacksonObjectMapper()

    route("/orders") {
        post {
            val idemKey = call.request.headers["Idempotency-Key"]
            val req = try {
                call.receive<CreateOrderRequest>()
            } catch (_: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid JSON"))
                return@post
            }

            // validation
            if (req.items.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "items must not be empty")); return@post
            }
            if (req.currency.length != 3) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "currency must be 3-letter code")); return@post
            }
            if (req.items.any { it.qty <= 0 || it.priceCents < 0 }) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "qty>0 and price>=0 required")); return@post
            }

            val path = "/orders"
            val requestHash = sha256(mapper.writeValueAsString(req))

            if (idemKey != null) {
                val existing = idemRepo.find(idemKey, path)
                if (existing != null) {
                    if (existing.requestHash != requestHash) {
                        call.respond(HttpStatusCode.Conflict, mapOf("error" to "Idempotency-Key replay with different payload"))
                        return@post
                    }
                    if (existing.responseBody != null && existing.statusCode != null) {
                        call.respond(
                            HttpStatusCode.fromValue(existing.statusCode),
                            TextContent(existing.responseBody, ContentType.Application.Json)
                        )
                        return@post
                    }
                }
            }

            val orderId = UUID.randomUUID()
            val total = req.items.fold(0L) { acc, item -> acc + item.qty.toLong() * item.priceCents }
            val order = Order(
                id = orderId,
                customerId = req.customerId,
                status = OrderStatus.PENDING,
                totalAmountCents = total,
                currency = req.currency.uppercase()
            )

            // order + items
            ordersRepo.create(order, req.items.map { OrderItem(it.sku, it.qty, it.priceCents) })

            // outbox
            val eventPayload = mapper.writeValueAsString(
                mapOf(
                    "eventId" to UUID.randomUUID().toString(),
                    "orderId" to orderId.toString(),
                    "totalAmountCents" to total,
                    "currency" to order.currency,
                    "items" to req.items
                )
            )
            outbox.save(orderId, eventType = "OrderCreated", payloadJson = eventPayload)

            // response (+ save to idempotency if key present)
            val response = mapOf(
                "orderId" to orderId,
                "status" to order.status.name,
                "totalAmountCents" to total,
                "currency" to order.currency
            )
            val json = mapper.writeValueAsString(response)
            if (idemKey != null) {
                idemRepo.save(idemKey, path, requestHash, json, HttpStatusCode.Created.value)
            }
            call.respond(HttpStatusCode.Created, TextContent(json, ContentType.Application.Json))
        }

        get("/{id}") {
            val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            if (id == null) { call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid UUID")); return@get }
            val found = ordersRepo.findById(id)
            if (found == null) call.respond(HttpStatusCode.NotFound) else call.respond(found)
        }
    }
}

data class CreateOrderRequest(
    val customerId: UUID,
    val currency: String,
    val items: List<CreateOrderItem>
)

data class CreateOrderItem(
    val sku: String,
    val qty: Int,
    val priceCents: Long
)

private fun sha256(s: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray())
        .joinToString("") { "%02x".format(it) }
