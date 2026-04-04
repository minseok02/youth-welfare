package com.example.welfare.user.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "priority_options")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriorityOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Byte id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false)
    private String label;

    private String description;
}
