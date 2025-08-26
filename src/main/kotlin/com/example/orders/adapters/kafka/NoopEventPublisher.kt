package com.example.orders.adapters.kafka

class NoopEventPublisher : EventPublisher {
    override fun publish(topic: String, key: String, value: String) { /* no-op */ }
    override fun close() {}
}
