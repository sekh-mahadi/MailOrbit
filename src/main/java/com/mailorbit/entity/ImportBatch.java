package com.mailorbit.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "import_batches")
@Getter
@Setter
@NoArgsConstructor
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;

    /** Data rows seen in the file (excluding the header). */
    private int totalRows;

    /** New contacts actually inserted. */
    private int imported;

    /** Rows skipped because the dedupe key already existed. */
    private int duplicates;

    /** Rows skipped because no email address could be parsed. */
    private int unparseable;

    private LocalDateTime createdAt = LocalDateTime.now();
}
