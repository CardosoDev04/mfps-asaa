package com.group9.asaa

import org.jdbi.v3.core.Jdbi
import org.springframework.stereotype.Repository

data class MessageObject(
    val id: Int,
    val message: String
)

@Repository
class DemoRepo(
    private val systemReaderJdbi: Jdbi,
    private val systemWriterJdbi: Jdbi
) {
    fun getAwesomeMessage(): String {
        return systemReaderJdbi.withHandle<String, Exception> { handle ->
            handle.createQuery("""
                SELECT message
                FROM "DEMO"
                WHERE id = 1
            """.trimIndent())
                .mapTo(String::class.java)
                .one()
        }
    }

    fun getMessageObject(): MessageObject {
        return systemReaderJdbi.withHandle<MessageObject, Exception> { handle ->
            handle.createQuery("""
                SELECT id, message
                FROM "DEMO"
                WHERE id = 1
            """.trimIndent())
                .map { rs, _ ->
                    MessageObject(
                        id = rs.getInt("id"),
                        message = rs.getString("message")
                    )
                }
                .one()
        }
    }
}
