package com.example.welfare.user.entity;

import com.example.welfare.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "auth_users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AuthUser extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32, columnDefinition = "CHAR(32)")
    private String userKey;

    @Column(nullable = false, unique = true, length = 64, columnDefinition = "CHAR(64)")
    private String emailLookupHash;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean isActive;

    @Column(nullable = false)
    private int loginFailCount;

    private LocalDateTime lockedUntil;

    private LocalDateTime withdrawnAt;

    public void syncFrom(User user, String emailLookupHash) {
        this.emailLookupHash = emailLookupHash;
        this.passwordHash = user.getPasswordHash();
        this.isActive = user.isActive();
        this.loginFailCount = user.getLoginFailCount();
        this.lockedUntil = user.getLockedUntil();
        this.withdrawnAt = user.getWithdrawnAt();
    }
}
