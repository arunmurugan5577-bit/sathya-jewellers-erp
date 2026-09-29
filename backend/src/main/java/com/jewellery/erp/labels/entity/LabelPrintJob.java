package com.jewellery.erp.labels.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One print run. A label carries the serial number that identifies a piece, so
 * who printed which serials, and when, is worth keeping - not least to answer
 * "was this tag reprinted?".
 */
@Entity
@Table(name = "label_print_jobs")
@Getter
@Setter
@NoArgsConstructor
public class LabelPrintJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "printed_at", nullable = false)
    private Instant printedAt;

    @Column(name = "printed_by", nullable = false, length = 100)
    private String printedBy;

    @Column(name = "label_count", nullable = false)
    private int labelCount;

    /** Comma separated, in printed order. */
    @Column(name = "serial_numbers", nullable = false)
    private String serialNumbers;
}
