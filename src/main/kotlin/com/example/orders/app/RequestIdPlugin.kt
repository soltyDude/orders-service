package com.example.orders.app


import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.util.*
import java.util.*



object RequestIdPlugin : BaseApplicationPlugin<ApplicationCallPipeline, Unit, Unit> {
    override val key = AttributeKey<Unit>("RequestIdPlugin")


    override fun install(pipeline: ApplicationCallPipeline, configure: Unit.() -> Unit) {
        pipeline.intercept(ApplicationCallPipeline.Setup) {
            val existing = call.request.headers["X-Request-Id"]
            val rid = existing ?: UUID.randomUUID().toString()
            call.response.headers.append("X-Request-Id", rid, false)
            proceed()
        }
    }
}