package com.example.welfare.user.entity;

import com.example.welfare.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_pii", catalog = "youth_welfare_pii")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserPii extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32, columnDefinition = "CHAR(32)")
    private String userKey;

    private String emailEnc;

    private String nameEnc;

    private String birthDateEnc;

    private String phoneEnc;

    public void sync(String emailEnc, String nameEnc, String birthDateEnc, String phoneEnc) {
        this.emailEnc = emailEnc;
        this.nameEnc = nameEnc;
        this.birthDateEnc = birthDateEnc;
        this.phoneEnc = phoneEnc;
    }
}
