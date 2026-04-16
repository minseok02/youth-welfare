package com.example.welfare.notification.entity;

import com.example.welfare.global.entity.BaseTimeEntity;
import com.example.welfare.policy.entity.WelfareService;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "notification_services", indexes = {
        @Index(name = "idx_ns_notification", columnList = "notification_id"),
        @Index(name = "idx_ns_service", columnList = "service_id"),
        @Index(name = "idx_ns_log", columnList = "recommendation_log_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class NotificationServiceItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private WelfareService service;

    @Column(name = "recommendation_log_id")
    private Long recommendationLogId;

    @Column(name = "rank_order", nullable = false)
    private Integer rankOrder;

    private BigDecimal finalScore;

    @Column(name = "service_title", length = 255)
    private String serviceTitle;
}
