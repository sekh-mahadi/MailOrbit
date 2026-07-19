package com.mailorbit.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_contacts",
       uniqueConstraints = @UniqueConstraint(name = "uk_contact_dedupe_key", columnNames = "dedupe_key"),
       indexes = {
           @Index(name = "idx_contact_status", columnList = "status"),
           @Index(name = "idx_contact_domain", columnList = "domain"),
           @Index(name = "idx_contact_batch", columnList = "batch_id")
       })
@Getter
@Setter
@NoArgsConstructor
public class EmailContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    /** Normalized key used to detect duplicates (lowercased, +tags stripped, gmail dots removed). */
    @Column(name = "dedupe_key", nullable = false)
    private String dedupeKey;

    private String firstName;
    private String lastName;
    private String company;

    /** Where the contact came from (CSV column "source", or the import file name). */
    private String source;

    @Column(name = "batch_id")
    private Long batchId;

    private String domain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContactStatus status = ContactStatus.NEW;

    /** 0-100 deliverability confidence, set by verification. */
    private Integer score;

    @Column(length = 1000)
    private String reasons;

    private Boolean syntaxOk;
    private Boolean mxOk;
    private Boolean disposable;
    private Boolean roleAccount;
    private Boolean freeProvider;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime verifiedAt;

    @Transient
    public String getLocalPart() {
        if (email == null) return "";
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
