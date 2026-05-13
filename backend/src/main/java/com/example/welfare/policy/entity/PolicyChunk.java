package com.example.welfare.policy.entity;

import com.example.welfare.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "policy_chunks", uniqueConstraints = {
        @UniqueConstraint(name = "uq_policy_chunk_scope", columnNames = {"service_id", "chunk_type", "chunk_order"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PolicyChunk extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private WelfareService service;

    @Column(nullable = false, length = 50)
    private String chunkType;

    @Column(nullable = false)
    private Integer chunkOrder;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String chunkText;

    public void updateChunkText(String chunkText) {
        this.chunkText = chunkText;
    }
}
