package com.jewellery.erp.chit.entity;

import com.jewellery.erp.common.entity.AuditableEntity;
import com.jewellery.erp.sales.entity.PaymentMethod;
import com.jewellery.erp.sales.entity.Sale;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What became of a matured chit: taken as cash, or spent against an invoice.
 *
 * <p>One row per redemption rather than a pair of columns on the account,
 * because a chit need not go all at once - 24,000 of chit against an 18,000
 * invoice leaves 6,000 to come back for - and because "what became of this
 * chit" should read the same whichever way it went.
 *
 * <p>On a gold chit {@link #grams} and {@link #ratePerGram} say what came off
 * and what it was valued at; {@link #amount} is their product, and the figure
 * the invoice or the cash drawer sees. A money chit spends rupees and carries
 * neither.
 */
@Entity
@Table(name = "chit_redemptions")
@Getter
@Setter
@NoArgsConstructor
public class ChitRedemption extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chit_account_id", nullable = false, updatable = false)
    private ChitAccount chitAccount;

    @Column(name = "redeemed_on", nullable = false, updatable = false)
    private LocalDate redeemedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10, updatable = false)
    private RedemptionKind kind;

    @Column(name = "grams", precision = 12, scale = 3, updatable = false)
    private BigDecimal grams;

    @Column(name = "rate_per_gram", precision = 14, scale = 2, updatable = false)
    private BigDecimal ratePerGram;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal amount;

    /** Set on a SALE redemption. The invoice the chit was spent against. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", updatable = false)
    private Sale sale;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RedemptionStatus status = RedemptionStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20, updatable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "reference_no", length = 100, updatable = false)
    private String referenceNo;

    @Column(name = "remarks", length = 300, updatable = false)
    private String remarks;

    public boolean isActive() {
        return status == RedemptionStatus.ACTIVE;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof ChitRedemption redemption && id != null && id.equals(redemption.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "ChitRedemption(" + id + ", " + kind + ", " + amount + ")";
    }
}
