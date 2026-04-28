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
public class AppPiiReadWriteDataSourceConfig {

    @Bean
    @ConfigurationProperties("app.datasource.pii-rw")
    public DataSourceProperties appPiiReadWriteDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "appPiiReadWriteDataSource")
    @ConfigurationProperties("app.datasource.pii-rw.hikari")
    public DataSource appPiiReadWriteDataSource(
            @Qualifier("appPiiReadWriteDataSourceProperties") DataSourceProperties properties
    ) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "appPiiReadWriteNamedParameterJdbcTemplate")
    public NamedParameterJdbcTemplate appPiiReadWriteNamedParameterJdbcTemplate(
            @Qualifier("appPiiReadWriteDataSource") DataSource dataSource
    ) {
        return new NamedParameterJdbcTemplate(dataSource);
    }
}
