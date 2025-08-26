package com.example.orders.adapters.kafka

interface EventPublisher {
    fun publish(topic: String, key: String, value: String)
    fun close() {}
}
