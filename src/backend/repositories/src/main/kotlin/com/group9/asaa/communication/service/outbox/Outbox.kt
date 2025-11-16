package com.group9.asaa.communication.service.outbox

import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Outbox record for pending deliveries.
 * In production replace with durable store (Postgres / Redis).
 */
data class OutboxRecord(
    val messageId: String,
    val payload: String,                 // usually serialized CommunicationMessage
    val headers: Map<String, String>,
    val createdAt: Instant = Instant.now(),
    val dispatchedAt: Instant? = null
)

interface OutboxRepository {
    fun save(record: OutboxRecord)
    fun markDispatched(messageId: String)
    fun findPending(limit: Int = 100): List<OutboxRecord>
}

/** In-memory demo implementation. */
@Repository
class InMemoryOutboxRepository : OutboxRepository {
    private val storage = ConcurrentHashMap<String, OutboxRecord>()

    override fun save(record: OutboxRecord) {
        // idempotent: don't overwrite existing record for same messageId
        storage.compute(record.messageId) { _, existing -> existing ?: record }
    }

    override fun markDispatched(messageId: String) {
        storage.computeIfPresent(messageId) { _, r ->
            r.copy(dispatchedAt = Instant.now())
        }
    }

    override fun findPending(limit: Int): List<OutboxRecord> =
        storage.values
            .filter { it.dispatchedAt == null }
            .sortedBy { it.createdAt }
            .take(limit)
}

/** Deduplication gate preventing concurrent processing of same messageId. */
interface DuplicationGate {
    suspend fun <T> withLock(messageId: String, block: suspend () -> T): T
}

@Component
class InMemoryDuplicationGate : DuplicationGate {
    private val locks = ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()

    override suspend fun <T> withLock(messageId: String, block: suspend () -> T): T {
        val mutex = locks.computeIfAbsent(messageId) { kotlinx.coroutines.sync.Mutex() }
        return try {
            mutex.lock()
            block()
        } finally {
            mutex.unlock()
        }
    }
}
