package com.example.orders.domain.ports


import com.example.orders.domain.model.Order
import com.example.orders.domain.model.OrderItem
import com.example.orders.domain.model.OrderStatus
import java.util.UUID


interface OrderRepository {
    suspend fun create(order: Order, items: List<OrderItem>)
    suspend fun findById(id: UUID): Order?
    suspend fun updateStatus(id: UUID, status: OrderStatus)
}