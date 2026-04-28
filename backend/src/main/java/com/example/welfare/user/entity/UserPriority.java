package com.example.welfare.user.entity;

import com.example.welfare.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_priorities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class UserPriority extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_key", length = 32, columnDefinition = "CHAR(32)")
    private String userKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "priority_option_id", nullable = false)
    private PriorityOption priorityOption;

    @Column(nullable = false)
    private int priorityRank; // 1~5

    @Column(nullable = false)
    private double weight; // 2.0/1.6/1.3/1.1/1.0
}
