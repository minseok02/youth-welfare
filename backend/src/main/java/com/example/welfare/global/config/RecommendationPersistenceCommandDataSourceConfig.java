package com.example.welfare.global.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
public class RecommendationPersistenceCommandDataSourceConfig {

    @Bean
    @ConfigurationProperties("app.datasource.recommendation-persistence-command")
    public DataSourceProperties recommendationPersistenceCommandDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "recommendationPersistenceCommandDataSource")
    @ConfigurationProperties("app.datasource.recommendation-persistence-command.hikari")
    public DataSource recommendationPersistenceCommandDataSource(
            @Qualifier("recommendationPersistenceCommandDataSourceProperties") DataSourceProperties properties
    ) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "recommendationPersistenceCommandNamedParameterJdbcTemplate")
    public NamedParameterJdbcTemplate recommendationPersistenceCommandNamedParameterJdbcTemplate(
            @Qualifier("recommendationPersistenceCommandDataSource") DataSource dataSource
    ) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean(name = "recommendationPersistenceCommandTransactionManager")
    public PlatformTransactionManager recommendationPersistenceCommandTransactionManager(
            @Qualifier("recommendationPersistenceCommandDataSource") DataSource dataSource
    ) {
        return new DataSourceTransactionManager(dataSource);
    }
}
