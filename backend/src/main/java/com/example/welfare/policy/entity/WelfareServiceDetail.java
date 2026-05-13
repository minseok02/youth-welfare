package com.example.welfare.policy.entity;

import com.example.welfare.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "welfare_service_details")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class WelfareServiceDetail extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false, unique = true)
    private WelfareService service;

    @Column(columnDefinition = "TEXT")
    private String targetDetail;    // 지원 대상 상세

    @Column(columnDefinition = "TEXT")
    private String supportDetail;   // 지원 내용 상세

    @Column(columnDefinition = "TEXT")
    private String applyMethodDetail; // 신청 방법 상세

    @Column(columnDefinition = "TEXT")
    private String selectionCriteria; // 선정 기준 상세

    @Column(columnDefinition = "TEXT")
    private String contactList;     // 문의처 JSON

    private String supportCycle;
    private String provisionType;
    private String homepageUrl;
    private String relatedLaw;

    @Column(columnDefinition = "TEXT")
    private String formFiles;

    @Column(columnDefinition = "TEXT")
    private String referenceUrlsJson;
}
