package com.example.orders.domain.ports


import java.time.Instant
import java.util.UUID


interface OutboxWriter {
    suspend fun save(
        aggregateId: UUID,
        eventType: String,
        payloadJson: String,
        aggregateType: String = "order",
        availableAt: Instant = Instant.now()
    )
}