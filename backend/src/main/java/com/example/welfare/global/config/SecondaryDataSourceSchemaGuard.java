package com.example.welfare.global.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.stereotype.Component;

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

        if (!jdbcUrl.startsWith("jdbc:mysql:")) {
            throw new IllegalStateException(propertyName + " must be a MySQL JDBC URL: " + jdbcUrl);
        }

        int hostStart = jdbcUrl.indexOf("//");
        if (hostStart < 0) {
            throw new IllegalStateException(propertyName + " must contain //host/database: " + jdbcUrl);
        }

        int databaseStart = jdbcUrl.indexOf('/', hostStart + 2);
        if (databaseStart < 0 || databaseStart == jdbcUrl.length() - 1) {
            throw new IllegalStateException(propertyName + " must contain database/schema name: " + jdbcUrl);
        }

        int databaseEnd = jdbcUrl.indexOf('?', databaseStart + 1);
        String databaseName = databaseEnd >= 0
                ? jdbcUrl.substring(databaseStart + 1, databaseEnd)
                : jdbcUrl.substring(databaseStart + 1);

        if (databaseName.isBlank()) {
            throw new IllegalStateException(propertyName + " must contain database/schema name: " + jdbcUrl);
        }

        return databaseName;
    }
}
