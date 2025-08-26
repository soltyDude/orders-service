package com.example.orders.app

import com.example.orders.infra.DatabaseConfig
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*

import io.ktor.serialization.jackson.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*

import kotlinx.coroutines.*

import com.example.orders.adapters.kafka.EventPublisher
import com.example.orders.adapters.kafka.KafkaEventPublisher
import com.example.orders.adapters.kafka.NoopEventPublisher
import com.example.orders.adapters.scheduler.OutboxPublisher
import com.example.orders.adapters.kafka.PaymentResultConsumer
import com.example.orders.adapters.db.ExposedOrderRepository

fun main() {
    val port = System.getProperty("PORT")?.toIntOrNull()
        ?: System.getenv("PORT")?.toIntOrNull()
        ?: 8080
    embeddedServer(Netty, port = port, module = Application::module).start(wait = true)
}

fun Application.module() {
    DatabaseConfig.init()

    install(ContentNegotiation) {
        jackson {
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            findAndRegisterModules()
        }
    }

    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("Idempotency-Key")
        allowNonSimpleContentTypes = true
        exposeHeader("X-Request-Id")
    }

    if (pluginOrNull(RequestIdPlugin) == null) install(RequestIdPlugin)

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun flag(name: String, default: String = "false") =
        (System.getProperty(name) ?: System.getenv(name) ?: default).toBoolean()

    val kafkaDisabled   = flag("APP_KAFKA_DISABLED", "false")
    val workersDisabled = flag("APP_WORKERS_DISABLED", "false")

    val kafkaPublisher: EventPublisher =
        if (kafkaDisabled) NoopEventPublisher() else KafkaEventPublisher()

    val outbox = OutboxPublisher(kafkaPublisher)
    val ordersRepo = ExposedOrderRepository()
    val payments = PaymentResultConsumer(ordersRepo)

    val outboxJob: Job?   = if (!workersDisabled && !kafkaDisabled) outbox.start(appScope) else null
    val paymentsJob: Job? = if (!workersDisabled && !kafkaDisabled) payments.start(appScope) else null

    environment.monitor.subscribe(ApplicationStopped) {
        try { outboxJob?.cancel() } catch (_: Throwable) {}
        try { paymentsJob?.cancel() } catch (_: Throwable) {}
        try { kafkaPublisher.close() } catch (_: Throwable) {}
        try { payments.close() } catch (_: Throwable) {}
        appScope.cancel()
    }

    routing {
        get("/health") { call.respondText("OK", ContentType.Text.Plain) }

        orderRoutes()

        val simEnabled = (System.getenv("SIM_ROUTES_ENABLED")
            ?: System.getProperty("SIM_ROUTES_ENABLED")
            ?: "true").toBoolean()
        if (simEnabled) {
            simulatePaymentsRoute(kafkaPublisher)
        }
    }
}
