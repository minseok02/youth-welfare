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
public class CollectExecutionLockCleanupDataSourceConfig {

    @Bean
    @ConfigurationProperties("app.datasource.collect-execution-lock-cleanup")
    public DataSourceProperties collectExecutionLockCleanupDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "collectExecutionLockCleanupDataSource")
    @ConfigurationProperties("app.datasource.collect-execution-lock-cleanup.hikari")
    public DataSource collectExecutionLockCleanupDataSource(
            @Qualifier("collectExecutionLockCleanupDataSourceProperties") DataSourceProperties properties
    ) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "collectExecutionLockCleanupNamedParameterJdbcTemplate")
    public NamedParameterJdbcTemplate collectExecutionLockCleanupNamedParameterJdbcTemplate(
            @Qualifier("collectExecutionLockCleanupDataSource") DataSource dataSource
    ) {
        return new NamedParameterJdbcTemplate(dataSource);
    }
}
