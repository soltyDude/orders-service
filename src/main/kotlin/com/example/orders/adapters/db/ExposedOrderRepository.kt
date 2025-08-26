package com.example.orders.adapters.db


import com.example.orders.adapters.db.tables.OrderItemsTable
import com.example.orders.adapters.db.tables.OrdersTable
import com.example.orders.domain.model.Order
import com.example.orders.domain.model.OrderItem
import com.example.orders.domain.model.OrderStatus
import com.example.orders.domain.ports.OrderRepository
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID
import org.jetbrains.exposed.sql.update


class ExposedOrderRepository : OrderRepository {
    override suspend fun create(order: Order, items: List<OrderItem>) {
        newSuspendedTransaction {
            OrdersTable.insert { row ->
                row[id] = order.id
                row[customerId] = order.customerId
                row[status] = order.status.name
                row[totalAmountCents] = order.totalAmountCents
                row[currency] = order.currency.uppercase()
            }
            items.forEach { it ->
                OrderItemsTable.insert { row ->
                    row[orderId] = order.id
                    row[sku] = it.sku
                    row[qty] = it.qty
                    row[priceCents] = it.priceCents
                }
            }
        }
    }


    override suspend fun findById(id: UUID): Order? = newSuspendedTransaction {
        OrdersTable.select { OrdersTable.id eq id }
            .limit(1)
            .map { rs ->
                Order(
                    id = id,
                    customerId = rs[OrdersTable.customerId],
                    status = OrderStatus.valueOf(rs[OrdersTable.status]),
                    totalAmountCents = rs[OrdersTable.totalAmountCents],
                    currency = rs[OrdersTable.currency].toString()
                )
            }
            .firstOrNull()
    }


    override suspend fun updateStatus(id: UUID, status: OrderStatus) {
        newSuspendedTransaction {
            OrdersTable.update({ OrdersTable.id eq id }) { row ->
                row[OrdersTable.status] = status.name
            }
        }
    }
}