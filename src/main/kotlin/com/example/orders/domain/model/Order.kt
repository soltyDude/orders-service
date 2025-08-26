package com.example.orders.domain.model


import java.util.UUID


enum class OrderStatus { PENDING, CONFIRMED, CANCELED }


data class OrderItem(val sku: String, val qty: Int, val priceCents: Long)


data class Order(
    val id: UUID,
    val customerId: UUID,
    val status: OrderStatus,
    val totalAmountCents: Long,
    val currency: String
)