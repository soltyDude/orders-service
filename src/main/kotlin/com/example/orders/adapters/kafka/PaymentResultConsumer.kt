package com.example.orders.adapters.kafka


import com.example.orders.adapters.db.tables.ProcessedEventsTable
import com.example.orders.domain.model.OrderStatus
import com.example.orders.domain.ports.OrderRepository
import com.example.orders.infra.KafkaConfig
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Duration
import java.util.UUID


class PaymentResultConsumer(
    private val orders: OrderRepository,
    private val topic: String = "payments.v1"
) : AutoCloseable {
    private val mapper = jacksonObjectMapper()
    private val consumer = KafkaConsumer<String, String>(KafkaConfig.consumerProps("orders-service"))


    fun start(scope: CoroutineScope): Job {
        consumer.subscribe(listOf(topic))
        return scope.launch {
            while (isActive) {
                val records = consumer.poll(Duration.ofMillis(500))
                for (rec in records) {
                    try {
                        val evt = mapper.readValue(rec.value(), PaymentResult::class.java)
                        handle(evt)
                    } catch (_: Exception) {
                    }
                }
                consumer.commitSync()
            }
        }
    }


    private suspend fun handle(evt: PaymentResult) {
        val eventId = evt.eventId
        val orderId = UUID.fromString(evt.orderId)
        newSuspendedTransaction {
            val seen = ProcessedEventsTable.select { ProcessedEventsTable.eventId eq eventId }.empty().not()
            if (!seen) {
                val newStatus = if (evt.status.equals("SUCCESS", ignoreCase = true)) OrderStatus.CONFIRMED else OrderStatus.CANCELED
                orders.updateStatus(orderId, newStatus)
                ProcessedEventsTable.insert { it[ProcessedEventsTable.eventId] = eventId }
            }
        }
    }


    override fun close() { consumer.close() }
}


data class PaymentResult(
    val eventId: String,
    val orderId: String,
    val status: String,
    val reason: String? = null
)