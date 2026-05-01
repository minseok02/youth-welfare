package com.example.welfare.collect.entity.converter;

import com.example.welfare.collect.entity.ApiSyncLog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiSyncLogStatusConverterTest {

    private final ApiSyncLogStatusConverter converter = new ApiSyncLogStatusConverter();

    @Test
    void convertToDatabaseColumn_writesLowercaseEnumValue() {
        assertThat(converter.convertToDatabaseColumn(ApiSyncLog.SyncStatus.PARTIAL_SUCCESS))
                .isEqualTo("partial_success");
    }

    @Test
    void convertToEntityAttribute_readsLowercaseEnumValue() {
        assertThat(converter.convertToEntityAttribute("running"))
                .isEqualTo(ApiSyncLog.SyncStatus.RUNNING);
    }
}
