package com.example.welfare.reference.controller;

import com.example.welfare.reference.dto.OfficialCodebookDetailResponse;
import com.example.welfare.reference.dto.OfficialCodebookSummaryResponse;
import com.example.welfare.reference.service.OfficialCodebookReadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OfficialCodebookController.class)
@AutoConfigureMockMvc(addFilters = false)
class OfficialCodebookControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OfficialCodebookReadService officialCodebookReadService;
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("코드북 목록을 반환한다")
    void listsCodebooks() throws Exception {
        when(officialCodebookReadService.listCodebooks()).thenReturn(List.of(
                new OfficialCodebookSummaryResponse(
                        "LOCAL_HOUSING_TYPE",
                        "xlsx",
                        "주택유형구분코드 조회자료.xlsx",
                        "user-profile and housing policy normalization",
                        8,
                        true
                )
        ));

        mockMvc.perform(get("/api/reference/official-codes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].codeSetKey").value("LOCAL_HOUSING_TYPE"))
                .andExpect(jsonPath("$.data[0].rowCount").value(8));
    }

    @Test
    @DisplayName("코드북 상세를 반환한다")
    void getsCodebookDetail() throws Exception {
        when(officialCodebookReadService.getCodebook("LOCAL_HOUSING_TYPE", "아파트", 10)).thenReturn(
                new OfficialCodebookDetailResponse(
                        "LOCAL_HOUSING_TYPE",
                        "xlsx",
                        "주택유형구분코드 조회자료.xlsx",
                        "user-profile and housing policy normalization",
                        8,
                        1,
                        "주택유형구분코드 조회자료",
                        List.of("코드값", "코드값의미", "비고"),
                        List.of(Map.of("코드값", "4", "코드값의미", "아파트", "비고", "")),
                        null
                )
        );

        mockMvc.perform(get("/api/reference/official-codes/LOCAL_HOUSING_TYPE")
                        .param("q", "아파트")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.codeSetKey").value("LOCAL_HOUSING_TYPE"))
                .andExpect(jsonPath("$.data.matchedRowCount").value(1))
                .andExpect(jsonPath("$.data.rows[0].코드값").value("4"))
                .andExpect(jsonPath("$.data.rows[0].코드값의미").value("아파트"));
    }
}
