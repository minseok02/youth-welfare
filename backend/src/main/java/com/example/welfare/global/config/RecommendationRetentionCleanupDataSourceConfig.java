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
public class RecommendationRetentionCleanupDataSourceConfig {

    @Bean
    @ConfigurationProperties("app.datasource.recommendation-retention-cleanup")
    public DataSourceProperties recommendationRetentionCleanupDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "recommendationRetentionCleanupDataSource")
    @ConfigurationProperties("app.datasource.recommendation-retention-cleanup.hikari")
    public DataSource recommendationRetentionCleanupDataSource(
            @Qualifier("recommendationRetentionCleanupDataSourceProperties") DataSourceProperties properties
    ) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "recommendationRetentionCleanupNamedParameterJdbcTemplate")
    public NamedParameterJdbcTemplate recommendationRetentionCleanupNamedParameterJdbcTemplate(
            @Qualifier("recommendationRetentionCleanupDataSource") DataSource dataSource
    ) {
        return new NamedParameterJdbcTemplate(dataSource);
    }
}
