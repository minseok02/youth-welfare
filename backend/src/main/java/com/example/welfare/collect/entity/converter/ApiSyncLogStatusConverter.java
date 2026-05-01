package com.example.welfare.collect.entity.converter;

import com.example.welfare.collect.entity.ApiSyncLog;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

@Converter(autoApply = false)
public class ApiSyncLogStatusConverter implements AttributeConverter<ApiSyncLog.SyncStatus, String> {

    @Override
    public String convertToDatabaseColumn(ApiSyncLog.SyncStatus attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public ApiSyncLog.SyncStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return ApiSyncLog.SyncStatus.valueOf(dbData.toUpperCase(Locale.ROOT));
    }
}
