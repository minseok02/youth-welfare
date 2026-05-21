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
public class WebPushSubscriptionCleanupDataSourceConfig {

    @Bean
    @ConfigurationProperties("app.datasource.web-push-subscription-cleanup")
    public DataSourceProperties webPushSubscriptionCleanupDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "webPushSubscriptionCleanupDataSource")
    @ConfigurationProperties("app.datasource.web-push-subscription-cleanup.hikari")
    public DataSource webPushSubscriptionCleanupDataSource(
            @Qualifier("webPushSubscriptionCleanupDataSourceProperties") DataSourceProperties properties
    ) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "webPushSubscriptionCleanupNamedParameterJdbcTemplate")
    public NamedParameterJdbcTemplate webPushSubscriptionCleanupNamedParameterJdbcTemplate(
            @Qualifier("webPushSubscriptionCleanupDataSource") DataSource dataSource
    ) {
        return new NamedParameterJdbcTemplate(dataSource);
    }
}
