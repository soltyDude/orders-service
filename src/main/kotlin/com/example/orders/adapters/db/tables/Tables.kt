package com.example.orders.adapters.db.tables


import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Table


object OrdersTable : UUIDTable("orders", "id") {
    val customerId = uuid("customer_id")
    val status = varchar("status", 16)
    val totalAmountCents = long("total_amount_cents")
    val currency = varchar("currency", 3)
}


object OrderItemsTable : Table("order_items") {
    val orderId = uuid("order_id")
    val sku = text("sku")
    val qty = integer("qty")
    val priceCents = long("price_cents")
    override val primaryKey = PrimaryKey(orderId, sku)
}


object IdempotencyKeysTable : Table("idempotency_keys") {
    val id = long("id").autoIncrement()
    val key = text("idempotency_key")
    val path = text("path")
    val requestHash = text("request_hash")
    val responseBody = text("response_body").nullable()
    val statusCode = integer("status_code").nullable()
    override val primaryKey = PrimaryKey(id)
    init { index(true, key, path) }
}


object OutboxTable : Table("outbox") {
    val id = long("id").autoIncrement()
    val aggregateType = text("aggregate_type")
    val aggregateId = uuid("aggregate_id")
    val eventType = text("event_type")
    val payload = text("payload")
    val status = text("status") // PENDING/SENT/FAILED (контролируется CHECK в БД)
    // available_at/created_at выставляются на стороне БД
    override val primaryKey = PrimaryKey(id)
}

object ProcessedEventsTable : Table("processed_events") {
    val eventId = text("event_id")
    override val primaryKey = PrimaryKey(eventId)
}
