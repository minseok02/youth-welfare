package com.example.welfare.user.controller;

import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;
    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("마이페이지 북마크 목록 조회는 성공 응답을 반환한다")
    void getBookmarksReturnsSuccessResponse() throws Exception {
        given(userService.getBookmarks(isNull())).willReturn(List.of(
                PolicySummaryResponse.builder()
                        .id(11L)
                        .title("청년 월세 지원")
                        .description("월세 부담 완화")
                        .unifiedCategory("HOUSING")
                        .status("ACTIVE")
                        .youthMidLabel("전월세 및 주거급여 지원")
                        .provisionMethodLabel("온라인")
                        .build()
        ));

        mockMvc.perform(get("/api/users/me/bookmarks")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthenticatedUser(1L, "user-key-1"),
                                null,
                                Collections.emptyList()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(11))
                .andExpect(jsonPath("$.data[0].title").value("청년 월세 지원"))
                .andExpect(jsonPath("$.data[0].youthMidLabel").value("전월세 및 주거급여 지원"))
                .andExpect(jsonPath("$.data[0].provisionMethodLabel").value("온라인"));

        then(userService).should().getBookmarks(isNull());
    }
}
