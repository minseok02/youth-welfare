package com.example.welfare.reference.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OfficialCodebookReadServiceTest {

    private final OfficialCodebookReadService service = new OfficialCodebookReadService(new ObjectMapper());

    @Test
    void listsAllLocalCodebooks() {
        assertThat(service.listCodebooks()).hasSize(15);
        assertThat(service.listCodebooks())
                .extracting(codebook -> codebook.codeSetKey())
                .contains("LOCAL_HOUSING_TYPE", "LOCAL_ADMIN_INSTITUTION", "LOCAL_LEGAL_DISTRICT");
    }

    @Test
    void returnsFilteredSmallCodebookRows() {
        var detail = service.getCodebook("LOCAL_HOUSING_TYPE", "아파트", 10);

        assertThat(detail.codeSetKey()).isEqualTo("LOCAL_HOUSING_TYPE");
        assertThat(detail.rowCount()).isEqualTo(8);
        assertThat(detail.matchedRowCount()).isEqualTo(1);
        assertThat(detail.rows()).singleElement().satisfies(row -> {
            assertThat(row.get("코드값")).isEqualTo("4");
            assertThat(row.get("코드값의미")).isEqualTo("아파트");
        });
    }

    @Test
    void returnsLargeCodebookMetadataWithoutRows() {
        var detail = service.getCodebook("LOCAL_ADMIN_INSTITUTION", null, null);

        assertThat(detail.rows()).isNull();
        assertThat(detail.metadata()).containsKey("activeRowCount");
        assertThat(detail.rowCount()).isGreaterThan(100000);
    }

    @Test
    void containsCodeChecksKnownCodeValues() {
        assertThat(service.containsCode("LOCAL_HOUSING_TYPE", "4")).isTrue();
        assertThat(service.containsCode("LOCAL_HOUSING_TYPE", "404")).isFalse();
    }

    @Test
    void throwsNotFoundForUnknownCodeSet() {
        assertThatThrownBy(() -> service.getCodebook("UNKNOWN_CODE_SET", null, null))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REFERENCE_NOT_FOUND);
    }
}
