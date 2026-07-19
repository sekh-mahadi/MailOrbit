package com.mailorbit.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Do-not-contact entry. Pattern is either a full email address
 * ("jane@example.com") or a bare domain ("example.com") which
 * suppresses every address at that domain.
 */
@Entity
@Table(name = "suppression_entries",
       uniqueConstraints = @UniqueConstraint(name = "uk_suppression_pattern", columnNames = "pattern"))
@Getter
@Setter
@NoArgsConstructor
public class SuppressionEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String pattern;

    private String reason;

    private LocalDateTime createdAt = LocalDateTime.now();
}
