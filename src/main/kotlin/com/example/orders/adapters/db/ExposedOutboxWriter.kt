package com.example.orders.adapters.db


import com.example.orders.adapters.db.tables.OutboxTable
import com.example.orders.domain.ports.OutboxWriter
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.UUID


class ExposedOutboxWriter : OutboxWriter {
    override suspend fun save(
        aggregateId: UUID,
        eventType: String,
        payloadJson: String,
        aggregateType: String,
        availableAt: Instant
    ) {
        newSuspendedTransaction {
            OutboxTable.insert { row ->
                row[OutboxTable.aggregateType] = aggregateType
                row[OutboxTable.aggregateId] = aggregateId
                row[OutboxTable.eventType] = eventType
                row[OutboxTable.payload] = payloadJson
                row[OutboxTable.status] = "PENDING"
            }
        }
    }
}