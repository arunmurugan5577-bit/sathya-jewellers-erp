package com.jewellery.erp.labels.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One page waiting for the shop's print agent.
 *
 * <p>The server renders the page here, at the printer's own resolution, so the
 * agent has nothing to lay out and no settings to get wrong - it spools a
 * bitmap. That keeps one copy of the drawing code, which is the only way the
 * preview and the tag stay the same picture.
 *
 * <p>A page rather than a whole run: a batch that fails on its third tag can be
 * retried from the third tag, and the agent never holds more than it is
 * printing.
 */
@Entity
@Table(name = "label_print_queue")
@Getter
@Setter
@NoArgsConstructor
public class LabelPrintQueuePage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The run this page belongs to, for the audit trail already kept there. */
    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "page_no", nullable = false)
    private int pageNo;

    /** The page as a 1-bit PNG at the printer's resolution. */
    @Column(name = "image", nullable = false)
    private byte[] image;

    @Column(name = "width_mm", nullable = false, precision = 6, scale = 2)
    private BigDecimal widthMm;

    @Column(name = "height_mm", nullable = false, precision = 6, scale = 2)
    private BigDecimal heightMm;

    /** Which printer at the shop; empty means the agent's default. */
    @Column(name = "printer_name", length = 160)
    private String printerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private QueuedPageStatus status = QueuedPageStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
