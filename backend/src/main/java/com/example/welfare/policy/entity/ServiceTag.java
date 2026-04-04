package com.example.welfare.policy.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "service_tags",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_st", columnNames = {"service_id", "tag_type", "tag_value"})
        },
        indexes = {
                @Index(name = "idx_st_tag", columnList = "tagType, tagValue")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class ServiceTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private WelfareService service;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TagType tagType;

    @Column(nullable = false)
    private String tagValue;

    public enum TagType {
        INTEREST_THEME, TARGET_GROUP, LIFE_STAGE, KEYWORD
    }
}
