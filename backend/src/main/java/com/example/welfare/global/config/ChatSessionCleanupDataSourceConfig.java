package com.example.welfare.global.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class ChatSessionCleanupDataSourceConfig {

    @Bean
    @ConfigurationProperties("app.datasource.chat-session-cleanup")
    public DataSourceProperties chatSessionCleanupDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "chatSessionCleanupDataSource")
    @ConfigurationProperties("app.datasource.chat-session-cleanup.hikari")
    public DataSource chatSessionCleanupDataSource(
            @Qualifier("chatSessionCleanupDataSourceProperties") DataSourceProperties properties
    ) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "chatSessionCleanupNamedParameterJdbcTemplate")
    public NamedParameterJdbcTemplate chatSessionCleanupNamedParameterJdbcTemplate(
            @Qualifier("chatSessionCleanupDataSource") DataSource dataSource
    ) {
        return new NamedParameterJdbcTemplate(dataSource);
    }
}
