package uk.ac.dundee.ga.mms.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Evidence of an allocation import (FR-07). The parsed rows are kept as JSON until commit. */
@Entity
@Table(name = "import_batch")
@Getter @Setter @NoArgsConstructor
public class ImportBatch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String fileName;
    private Long uploadedBy;
    private Instant uploadedAt;
    private int rowsTotal;
    private int rowsOk;
    private int rowsError;
    private int rowsAdd;
    private int rowsUpdate;
    @Enumerated(EnumType.STRING)
    private ImportStatus status = ImportStatus.PREVIEW;
    private String payloadJson;
    private Instant committedAt;
}
