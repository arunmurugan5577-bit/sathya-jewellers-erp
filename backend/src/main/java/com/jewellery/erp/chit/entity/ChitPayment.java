package com.jewellery.erp.chit.entity;

import com.jewellery.erp.sales.entity.PaymentMethod;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.EntityListeners;

/**
 * One instalment of a chit, the shop's bonus month included.
 *
 * <p>The bonus is a payment row like any other, marked {@link #paidByShop}.
 * Keeping it here rather than in a column on the account means a balance is
 * the sum of the payments and nothing has to remember to add it on - and a
 * gold bonus buys grams at a rate the same way every other instalment does.
 *
 * <p>{@link #ratePerGram} and {@link #grams} are filled on a gold chit and left
 * empty on a money one. The rate is kept as well as the grams it bought
 * because a customer asking "what did my August instalment get me" is asking
 * about the rate that day, and the rate that day is not recoverable from
 * anywhere else.
 */
@Entity
@Table(name = "chit_payments")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ChitPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chit_account_id", nullable = false, updatable = false)
    private ChitAccount chitAccount;

    @Column(name = "instalment_no", nullable = false, updatable = false)
    private short instalmentNo;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    /** Gold chits only: the rate this instalment bought at. */
    @Column(name = "rate_per_gram", precision = 14, scale = 2)
    private BigDecimal ratePerGram;

    /** Gold chits only: what it bought. */
    @Column(name = "grams", precision = 12, scale = 3)
    private BigDecimal grams;

    /** The twelfth month, which the shop puts in. */
    @Column(name = "paid_by_shop", nullable = false)
    private boolean paidByShop;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "reference_no", length = 100)
    private String referenceNo;

    @Column(name = "remarks", length = 300)
    private String remarks;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", length = 100, updatable = false)
    private String createdBy;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof ChitPayment payment && id != null && id.equals(payment.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
