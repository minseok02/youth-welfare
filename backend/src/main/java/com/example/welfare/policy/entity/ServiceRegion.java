package com.example.welfare.policy.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "service_regions",
        indexes = {
                @Index(name = "idx_sr_service", columnList = "service_id"),
                @Index(name = "idx_sr_region_code", columnList = "regionCode"),
                @Index(name = "idx_sr_sido", columnList = "sidoName")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class ServiceRegion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private WelfareService service;

    private String regionCode; // 온통청년 지역코드

    private String sidoName;
    private String sggName;
}
