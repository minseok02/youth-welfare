package com.example.welfare.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_attributes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class UserAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_key", length = 32)
    private String userKey;

    // VARCHAR(30), ENUM 아님. 유효성 검증은 AttrType enum으로 애플리케이션 레이어에서 처리.
    @Column(nullable = false, length = 30)
    private String attrType;

    @Column(nullable = false)
    private String attrValue;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum AttrType {
        INTEREST_FIELD,  // 관심분야
        TARGET_TYPE      // 대상 유형
    }
}
