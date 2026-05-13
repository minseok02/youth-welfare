package com.example.welfare.chat.entity;

import com.example.welfare.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "chat_retrieval_snapshots", indexes = {
        @Index(name = "idx_crs_snapshot_type_created", columnList = "snapshot_type, created_at DESC"),
        @Index(name = "idx_crs_scenario_key_created", columnList = "scenario_key, created_at DESC"),
        @Index(name = "idx_crs_user_key_created", columnList = "user_key, created_at DESC")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ChatRetrievalSnapshot extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_type", nullable = false, length = 20)
    private String snapshotType;

    @Column(name = "scenario_key", length = 100)
    private String scenarioKey;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "user_key", length = 32)
    private String userKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(name = "normalized_keyword", length = 500)
    private String normalizedKeyword;

    @Column(name = "search_keyword", length = 500)
    private String searchKeyword;

    @Column(name = "branch_key", length = 100)
    private String branchKey;

    @Column(name = "preferred_category", length = 100)
    private String preferredCategory;

    @Column(name = "preferred_terms_json", columnDefinition = "TEXT")
    private String preferredTermsJson;

    @Column(name = "branch_suggestion_keys_json", columnDefinition = "TEXT")
    private String branchSuggestionKeysJson;

    @Column(name = "fts_service_ids_json", columnDefinition = "TEXT")
    private String ftsServiceIdsJson;

    @Column(name = "semantic_service_ids_json", columnDefinition = "TEXT")
    private String semanticServiceIdsJson;

    @Column(name = "merged_service_ids_json", columnDefinition = "TEXT")
    private String mergedServiceIdsJson;

    @Column(name = "fallback_strategy", length = 40)
    private String fallbackStrategy;

    @Column(name = "needs_clarification")
    private Boolean needsClarification;

    @Column(name = "result_count", nullable = false)
    private int resultCount;
}
