package com.example.mutexa_be.entity;

import com.example.mutexa_be.entity.enums.ParameterStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "exclude_parameter")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExcludeParameter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "keyword", nullable = false, unique = true)
    private String keyword;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ParameterStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;
}
