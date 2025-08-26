package com.example.orders.infra

import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

private fun propOrEnv(name: String, default: String): String =
    System.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: default

object DatabaseConfig {
    fun init() {
        val url  = propOrEnv("DB_URL", "jdbc:postgresql://127.0.0.1:5432/orders")
        val user = propOrEnv("DB_USER", "postgres")
        val pass = propOrEnv("DB_PASSWORD", "postgres")

        println("DB connect -> url=$url, user=$user, pass.len=${pass.length}")

        Flyway.configure()
            .dataSource(url, user, pass)
            .load()
            .migrate()

        Database.connect(url = url, driver = "org.postgresql.Driver", user = user, password = pass)
    }
}
