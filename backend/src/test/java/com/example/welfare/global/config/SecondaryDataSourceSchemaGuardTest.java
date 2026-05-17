package com.example.welfare.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecondaryDataSourceSchemaGuardTest {

    @Test
    void allowsPiiSchemaUrls() throws Exception {
        SecondaryDataSourceSchemaGuard guard = new SecondaryDataSourceSchemaGuard(
                properties("jdbc:postgresql://127.0.0.1:5433/youth_welfare?sslmode=disable&currentSchema=youth_welfare_pii"),
                properties("jdbc:postgresql://127.0.0.1:5433/youth_welfare?sslmode=disable&currentSchema=youth_welfare_pii")
        );

        guard.afterPropertiesSet();
    }

    @Test
    void rejectsCoreSchemaUrl() {
        SecondaryDataSourceSchemaGuard guard = new SecondaryDataSourceSchemaGuard(
                properties("jdbc:postgresql://127.0.0.1:5433/youth_welfare?sslmode=disable&currentSchema=public"),
                properties("jdbc:postgresql://127.0.0.1:5433/youth_welfare?sslmode=disable&currentSchema=youth_welfare_pii")
        );

        assertThatThrownBy(guard::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.datasource.pii-rw.url")
                .hasMessageContaining("youth_welfare_pii")
                .hasMessageContaining("youth_welfare");
    }

    @Test
    void extractsDatabaseNameFromJdbcUrl() {
        assertThat(SecondaryDataSourceSchemaGuard.extractDatabaseName(
                "app.datasource.pii-rw.url",
                "jdbc:postgresql://db:5432/youth_welfare?sslmode=require&currentSchema=youth_welfare_pii"
        )).isEqualTo("youth_welfare_pii");
    }

    @Test
    void rejectsBlankJdbcUrl() {
        assertThatThrownBy(() -> SecondaryDataSourceSchemaGuard.extractDatabaseName(
                "app.datasource.pii-rw.url",
                ""
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void rejectsMysqlJdbcUrl() {
        assertThatThrownBy(() -> SecondaryDataSourceSchemaGuard.extractDatabaseName(
                "app.datasource.pii-rw.url",
                "jdbc:mysql://db:3306/youth_welfare_pii"
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be a jdbc:postgresql: URL");
    }

    private DataSourceProperties properties(String url) {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl(url);
        return properties;
    }
}
