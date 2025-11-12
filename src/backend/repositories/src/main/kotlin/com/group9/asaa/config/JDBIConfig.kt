package com.group9.asaa.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jdbi.v3.core.Jdbi
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
class JDBIConfig {

    @Value("\${POSTGRES_URL}")
    private lateinit var postgresUrl: String

    fun createDataSource(connectionString: String): DataSource {
        val config = HikariConfig().apply {
            jdbcUrl = connectionString
            maximumPoolSize = 10
        }
        return HikariDataSource(config)
    }

    @Bean
    fun systemReaderJdbi(): Jdbi = Jdbi.create(createDataSource(postgresUrl))

    @Bean
    fun systemWriterJdbi(): Jdbi = Jdbi.create(createDataSource(postgresUrl))
}
