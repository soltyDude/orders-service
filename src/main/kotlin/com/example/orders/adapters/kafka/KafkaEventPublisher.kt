package com.example.orders.adapters.kafka

import com.example.orders.infra.KafkaConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class KafkaEventPublisher : EventPublisher, AutoCloseable {
    private val producer = KafkaProducer<String, String>(KafkaConfig.producerProps())

    override fun publish(topic: String, key: String, value: String) {
        producer.send(ProducerRecord(topic, key, value)).get()
    }

    override fun close() {
        try { producer.flush() } catch (_: Throwable) {}
        producer.close()
    }
}

