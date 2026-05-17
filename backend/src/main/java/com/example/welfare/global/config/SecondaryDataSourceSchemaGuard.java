package com.example.welfare.global.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class SecondaryDataSourceSchemaGuard implements InitializingBean {

    private static final String REQUIRED_PII_SCHEMA = "youth_welfare_pii";

    private final DataSourceProperties appPiiReadWriteDataSourceProperties;
    private final DataSourceProperties notificationPiiReadDataSourceProperties;

    public SecondaryDataSourceSchemaGuard(
            @Qualifier("appPiiReadWriteDataSourceProperties")
            DataSourceProperties appPiiReadWriteDataSourceProperties,
            @Qualifier("notificationPiiReadDataSourceProperties")
            DataSourceProperties notificationPiiReadDataSourceProperties
    ) {
        this.appPiiReadWriteDataSourceProperties = appPiiReadWriteDataSourceProperties;
        this.notificationPiiReadDataSourceProperties = notificationPiiReadDataSourceProperties;
    }

    @Override
    public void afterPropertiesSet() {
        validateSchema("app.datasource.pii-rw.url", appPiiReadWriteDataSourceProperties.getUrl());
        validateSchema("app.datasource.notification-pii-ro.url", notificationPiiReadDataSourceProperties.getUrl());
    }

    private void validateSchema(String propertyName, String jdbcUrl) {
        String databaseName = extractDatabaseName(propertyName, jdbcUrl);
        if (!REQUIRED_PII_SCHEMA.equals(databaseName)) {
            throw new IllegalStateException(
                    propertyName + " must point to `" + REQUIRED_PII_SCHEMA + "` but was `" + databaseName + "`: " + jdbcUrl
            );
        }
    }

    static String extractDatabaseName(String propertyName, String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalStateException(propertyName + " must not be blank");
        }

        if (jdbcUrl.startsWith("jdbc:postgresql:")) {
            String schemaName = extractCurrentSchema(jdbcUrl);
            if (schemaName == null || schemaName.isBlank()) {
                throw new IllegalStateException(propertyName + " must declare currentSchema for PostgreSQL URLs: " + jdbcUrl);
            }
            return schemaName;
        }

        throw new IllegalStateException(propertyName + " must be a jdbc:postgresql: URL: " + jdbcUrl);
    }

    private static String extractCurrentSchema(String jdbcUrl) {
        int queryStart = jdbcUrl.indexOf('?');
        if (queryStart < 0 || queryStart == jdbcUrl.length() - 1) {
            return null;
        }

        return Arrays.stream(jdbcUrl.substring(queryStart + 1).split("&"))
                .map(param -> param.split("=", 2))
                .filter(parts -> parts.length == 2 && parts[0].equals("currentSchema"))
                .map(parts -> parts[1])
                .filter(value -> !value.isBlank())
                .map(value -> value.split(",", 2)[0])
                .findFirst()
                .orElse(null);
    }
}
