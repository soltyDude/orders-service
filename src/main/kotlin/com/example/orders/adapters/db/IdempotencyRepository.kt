package com.example.orders.adapters.db

import com.example.orders.adapters.db.tables.IdempotencyKeysTable
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.and

class IdempotencyRepository {
    data class Record(
        val responseBody: String?,
        val statusCode: Int?,
        val requestHash: String
    )

    suspend fun find(key: String, path: String): Record? = newSuspendedTransaction {
        IdempotencyKeysTable
            .select { (IdempotencyKeysTable.key eq key) and (IdempotencyKeysTable.path eq path) }
            .limit(1)
            .map { it.toRecord() }
            .firstOrNull()
    }

    suspend fun save(
        key: String,
        path: String,
        requestHash: String,
        responseBody: String,
        statusCode: Int
    ) = newSuspendedTransaction {
        try {
            IdempotencyKeysTable.insert { row ->
                row[IdempotencyKeysTable.key] = key
                row[IdempotencyKeysTable.path] = path
                row[IdempotencyKeysTable.requestHash] = requestHash
                row[IdempotencyKeysTable.responseBody] = responseBody
                row[IdempotencyKeysTable.statusCode] = statusCode
            }
        } catch (_: ExposedSQLException) {
        }
    }

    private fun ResultRow.toRecord() = Record(
        responseBody = this[IdempotencyKeysTable.responseBody],
        statusCode   = this[IdempotencyKeysTable.statusCode],
        requestHash  = this[IdempotencyKeysTable.requestHash]
    )
}
