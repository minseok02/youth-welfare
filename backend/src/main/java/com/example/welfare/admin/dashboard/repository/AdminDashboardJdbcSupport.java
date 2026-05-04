package com.example.welfare.admin.dashboard.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

final class AdminDashboardJdbcSupport {

    private AdminDashboardJdbcSupport() {
    }

    static LocalDateTime getLocalDateTime(ResultSet rs, String columnName) throws SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(columnName);
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }

    static Boolean getNullableBoolean(ResultSet rs, String columnName) throws SQLException {
        boolean value = rs.getBoolean(columnName);
        return rs.wasNull() ? null : value;
    }
}
