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
public class RecommendationReviewGateCommandDataSourceConfig {

    @Bean
    @ConfigurationProperties("app.datasource.recommendation-review-gate-command")
    public DataSourceProperties recommendationReviewGateCommandDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "recommendationReviewGateCommandDataSource")
    @ConfigurationProperties("app.datasource.recommendation-review-gate-command.hikari")
    public DataSource recommendationReviewGateCommandDataSource(
            @Qualifier("recommendationReviewGateCommandDataSourceProperties") DataSourceProperties properties
    ) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "recommendationReviewGateCommandNamedParameterJdbcTemplate")
    public NamedParameterJdbcTemplate recommendationReviewGateCommandNamedParameterJdbcTemplate(
            @Qualifier("recommendationReviewGateCommandDataSource") DataSource dataSource
    ) {
        return new NamedParameterJdbcTemplate(dataSource);
    }
}
