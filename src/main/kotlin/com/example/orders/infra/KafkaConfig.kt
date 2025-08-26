package com.example.orders.infra


import java.util.Properties


object KafkaConfig {
    private fun bootstrap() = System.getenv("KAFKA_BOOTSTRAP") ?: "127.0.0.1:9092"


    fun producerProps(): Properties = Properties().apply {
        put("bootstrap.servers", bootstrap())
        put("acks", "all")
        put("linger.ms", "0")
        put("enable.idempotence", "true")
        put("max.in.flight.requests.per.connection", "1")
        put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
        put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    }


    fun consumerProps(groupId: String): Properties = Properties().apply {
        put("bootstrap.servers", bootstrap())
        put("group.id", groupId)
        put("enable.auto.commit", "false")
        put("auto.offset.reset", "earliest")
        put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
        put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    }
}