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
public class ClusterAiCleanupDataSourceConfig {

    @Bean
    @ConfigurationProperties("app.datasource.cluster-ai-cleanup")
    public DataSourceProperties clusterAiCleanupDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "clusterAiCleanupDataSource")
    @ConfigurationProperties("app.datasource.cluster-ai-cleanup.hikari")
    public DataSource clusterAiCleanupDataSource(
            @Qualifier("clusterAiCleanupDataSourceProperties") DataSourceProperties properties
    ) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "clusterAiCleanupNamedParameterJdbcTemplate")
    public NamedParameterJdbcTemplate clusterAiCleanupNamedParameterJdbcTemplate(
            @Qualifier("clusterAiCleanupDataSource") DataSource dataSource
    ) {
        return new NamedParameterJdbcTemplate(dataSource);
    }
}
