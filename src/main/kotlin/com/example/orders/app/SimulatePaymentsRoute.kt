package com.example.orders.app


import com.example.orders.adapters.kafka.EventPublisher
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID


data class SimPaymentRequest(val orderId: UUID, val status: String)


fun Route.simulatePaymentsRoute(publisher: EventPublisher) {
    val mapper = jacksonObjectMapper()
    post("/_sim/payments") {
        val req = try { call.receive<SimPaymentRequest>() } catch (_: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid JSON")); return@post
        }
        val evt = mapOf(
            "eventId" to UUID.randomUUID().toString(),
            "orderId" to req.orderId.toString(),
            "status" to req.status.uppercase()
        )
        val payload = mapper.writeValueAsString(evt)
        publisher.publish("payments.v1", req.orderId.toString(), payload)
        call.respond(HttpStatusCode.Accepted, evt)
    }
}