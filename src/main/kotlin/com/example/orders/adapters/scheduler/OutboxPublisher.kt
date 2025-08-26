package com.example.orders.adapters.scheduler

import com.example.orders.adapters.kafka.EventPublisher
import com.example.orders.adapters.db.tables.OutboxTable
import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

class OutboxPublisher(
    private val publisher: EventPublisher,
    private val topic: String = "orders.v1",
    private val pollMs: Long = 1000,
    private val batchSize: Int = 100
) {
    fun start(scope: CoroutineScope): Job = scope.launch {
        while (isActive) {
            try {
                val batch = fetchNew(batchSize)
                for (evt in batch) {
                    try {
                        publisher.publish(topic, evt.aggregateId, evt.payload)
                        markSent(evt.id)
                    } catch (_: Exception) {
                        markFailed(evt.id)
                    }
                }
            } catch (_: Exception) {
            }
            delay(pollMs)
        }
    }

    private suspend fun fetchNew(limit: Int): List<OutboxRow> = newSuspendedTransaction {
        OutboxTable
            .select { OutboxTable.status eq "NEW" }
            .orderBy(OutboxTable.id)
            .limit(limit)
            .map { it.toOutboxRow() }
    }

    private suspend fun markSent(id: Long) = newSuspendedTransaction {
        OutboxTable.update({ OutboxTable.id eq id }) { it[status] = "SENT" }
    }

    private suspend fun markFailed(id: Long) = newSuspendedTransaction {
        OutboxTable.update({ OutboxTable.id eq id }) { it[status] = "FAILED" }
    }
}

data class OutboxRow(
    val id: Long,
    val aggregateId: String,
    val eventType: String,
    val payload: String,
    val createdAt: Instant
)

private fun ResultRow.toOutboxRow() = OutboxRow(
    id = this[OutboxTable.id],
    aggregateId = this[OutboxTable.aggregateId].toString(),
    eventType = this[OutboxTable.eventType],
    payload = this[OutboxTable.payload],
    createdAt = Instant.now()
)
