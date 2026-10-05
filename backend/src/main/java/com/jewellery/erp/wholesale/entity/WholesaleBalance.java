package com.jewellery.erp.wholesale.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A wholesale party's running account.
 *
 * <p>Two currencies at once: grams of pure gold, and a rupee balance for the
 * making and stone charges. They are kept apart because only the gold is
 * revalued when the rate moves - yesterday's making charge is still yesterday's
 * rupees.
 */
@Entity
@Table(name = "wholesale_balances")
@Getter
@Setter
@NoArgsConstructor
public class WholesaleBalance {

    @Id
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "pure_grams", nullable = false, precision = 12, scale = 3)
    private BigDecimal pureGrams = BigDecimal.ZERO;

    @Column(name = "misc_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal miscAmount = BigDecimal.ZERO;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    /** Adds an estimate to the account. Negative figures reverse a cancellation. */
    public void add(BigDecimal pure, BigDecimal misc) {
        this.pureGrams = this.pureGrams.add(pure);
        this.miscAmount = this.miscAmount.add(misc);
        this.updatedAt = Instant.now();
    }
}
