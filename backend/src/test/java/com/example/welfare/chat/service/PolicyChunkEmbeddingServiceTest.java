package com.example.welfare.chat.service;

import com.example.welfare.chat.gateway.ChatEmbeddingGateway;
import com.example.welfare.chat.repository.PolicyChunkVectorRepository;
import com.example.welfare.global.util.HashSupport;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PolicyChunkEmbeddingServiceTest {

    @Mock
    private ChatGroundingService chatGroundingService;
    @Mock
    private ChatEmbeddingGateway chatEmbeddingGateway;
    @Mock
    private PolicyChunkVectorRepository policyChunkVectorRepository;
    @Mock
    private WelfareServiceRepository welfareServiceRepository;

    private PolicyChunkEmbeddingService service;

    @BeforeEach
    void setUp() {
        service = new PolicyChunkEmbeddingService(
                chatGroundingService,
                chatEmbeddingGateway,
                policyChunkVectorRepository,
                welfareServiceRepository
        );
        ReflectionTestUtils.setField(service, "embeddingModel", "text-embedding-3-small");
    }

    @Test
    @DisplayName("text hash 와 embedding model 이 모두 같으면 재임베딩하지 않는다")
    void skipsFreshTargets() {
        String chunkText = "청년 주거비 지원";
        given(policyChunkVectorRepository.findEmbeddingTargets(List.of(11L))).willReturn(List.of(
                new PolicyChunkVectorRepository.PolicyChunkEmbeddingTarget(
                        1L,
                        11L,
                        chunkText,
                        "text-embedding-3-small",
                        HashSupport.sha256Hex(chunkText)
                )
        ));

        PolicyChunkEmbeddingService.EmbeddingRefreshResult result = service.refreshEmbeddingsForServiceIds(List.of(11L));

        assertThat(result.requestedServiceCount()).isEqualTo(1);
        assertThat(result.scannedChunkCount()).isEqualTo(1);
        assertThat(result.refreshedChunkCount()).isZero();
        verify(chatGroundingService).syncChunksForServiceIds(List.of(11L));
        verify(chatEmbeddingGateway, never()).embedDocuments(any());
        verify(policyChunkVectorRepository, never()).updateEmbedding(any(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("embedding model 이 바뀌면 동일 text hash 여도 재임베딩한다")
    void refreshesWhenEmbeddingModelChanges() {
        String chunkText = "청년 취업 역량 강화";
        given(policyChunkVectorRepository.findEmbeddingTargets(List.of(22L))).willReturn(List.of(
                new PolicyChunkVectorRepository.PolicyChunkEmbeddingTarget(
                        2L,
                        22L,
                        chunkText,
                        "legacy-model",
                        HashSupport.sha256Hex(chunkText)
                )
        ));
        given(chatEmbeddingGateway.embedDocuments(List.of(chunkText))).willReturn(List.of(new float[]{0.1f, 0.2f}));

        PolicyChunkEmbeddingService.EmbeddingRefreshResult result = service.refreshEmbeddingsForServiceIds(List.of(22L));

        assertThat(result.requestedServiceCount()).isEqualTo(1);
        assertThat(result.scannedChunkCount()).isEqualTo(1);
        assertThat(result.refreshedChunkCount()).isEqualTo(1);
        verify(policyChunkVectorRepository).updateEmbedding(any(), any(), anyString(), anyString(), any());
    }
}
