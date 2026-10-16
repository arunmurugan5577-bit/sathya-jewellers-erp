package com.jewellery.erp.chit.entity;

import com.jewellery.erp.common.entity.AuditableEntity;
import com.jewellery.erp.common.util.Money;
import com.jewellery.erp.customer.entity.Customer;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One customer's chit.
 *
 * <p>The terms are copied from the scheme at joining rather than read through
 * it. A shop that raises the 2,000 scheme to 2,500 for new joiners must not
 * thereby change what someone halfway through a chit is paying, or what their
 * chit will come to.
 *
 * <p>The running totals are kept here so a list screen costs one query, and
 * every one of them is the sum of {@link #payments}: {@link #applyPayment} is
 * the only thing that moves them, so they cannot drift from the instalments
 * they are a summary of.
 *
 * <p>Two balances, and a chit uses exactly one. A money chit spends rupees; a
 * gold chit spends grams and is valued at the rate on the day it is spent.
 * Check constraints in the database refuse any other combination.
 */
@Entity
@Table(name = "chit_accounts")
@Getter
@Setter
@NoArgsConstructor
public class ChitAccount extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chit_number", nullable = false, length = 16, updatable = false)
    private String chitNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scheme_id", nullable = false, updatable = false)
    private ChitScheme scheme;

    // --- the terms, as sold ---------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "scheme_type", nullable = false, length = 10, updatable = false)
    private ChitSchemeType schemeType;

    @Column(name = "monthly_amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal monthlyAmount;

    @Column(name = "payable_instalments", nullable = false, updatable = false)
    private short payableInstalments;

    @Column(name = "total_instalments", nullable = false, updatable = false)
    private short totalInstalments;

    @Column(name = "started_on", nullable = false, updatable = false)
    private LocalDate startedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ChitStatus status = ChitStatus.ACTIVE;

    // --- what has been put in, and what is left -------------------------------

    @Column(name = "paid_instalments", nullable = false)
    private short paidInstalments;

    @Column(name = "paid_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal paidAmount = Money.ZERO;

    @Column(name = "accrued_grams", nullable = false, precision = 12, scale = 3)
    private BigDecimal accruedGrams = BigDecimal.ZERO.setScale(Money.WEIGHT_SCALE);

    @Column(name = "balance_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal balanceAmount = Money.ZERO;

    @Column(name = "balance_grams", nullable = false, precision = 12, scale = 3)
    private BigDecimal balanceGrams = BigDecimal.ZERO.setScale(Money.WEIGHT_SCALE);

    @Column(name = "matured_on")
    private LocalDate maturedOn;

    @Column(name = "closed_on")
    private LocalDate closedOn;

    @Column(name = "close_reason", length = 500)
    private String closeReason;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @OneToMany(mappedBy = "chitAccount", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("instalmentNo ASC")
    private List<ChitPayment> payments = new ArrayList<>();

    @OneToMany(mappedBy = "chitAccount", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ChitRedemption> redemptions = new ArrayList<>();

    // --- the rules ------------------------------------------------------------

    public boolean isGold() {
        return schemeType == ChitSchemeType.GOLD;
    }

    /** Taking payments, and not yet finished. */
    public boolean isOpen() {
        return status == ChitStatus.ACTIVE;
    }

    /** The instalment the next payment settles. Always the next one along. */
    public short nextInstalmentNo() {
        return (short) (paidInstalments + 1);
    }

    /** How many months the customer still owes. The bonus is never theirs to pay. */
    public short instalmentsOwed() {
        return (short) Math.max(0, payableInstalments - paidInstalments);
    }

    /**
     * Records an instalment and moves the totals with it.
     *
     * <p>The only thing that writes the running totals, so they are always the
     * sum of the payments beside them. Both balances rise: what a chit holds
     * and what is left to spend are the same thing until it is spent.
     */
    public void applyPayment(ChitPayment payment) {
        payment.setChitAccount(this);
        payments.add(payment);

        paidInstalments = (short) (paidInstalments + 1);
        paidAmount = Money.money(paidAmount.add(payment.getAmount()));
        balanceAmount = Money.money(balanceAmount.add(payment.getAmount()));
        if (payment.getGrams() != null) {
            accruedGrams = Money.weight(accruedGrams.add(payment.getGrams()));
            balanceGrams = Money.weight(balanceGrams.add(payment.getGrams()));
        }

        // A money chit holds no grams, so only rupees are spendable; the
        // database says the same thing with a check constraint.
        if (paidInstalments >= payableInstalments && status == ChitStatus.ACTIVE) {
            status = ChitStatus.COMPLETE;
        }
    }

    /** The bonus is in: the chit is the customer's to spend. */
    public void mature(LocalDate on) {
        status = ChitStatus.MATURED;
        maturedOn = on;
    }

    /** Matured, with something still on it. Only such a chit can be spent. */
    public boolean isRedeemable() {
        return status == ChitStatus.MATURED && !isSpent();
    }

    /** Nothing left: a gold chit with no grams, a money chit with no rupees. */
    public boolean isSpent() {
        return (isGold() ? balanceGrams : balanceAmount).signum() == 0;
    }

    /**
     * Takes value off the chit.
     *
     * <p>The only thing that lowers a balance, as {@link #applyPayment} is the
     * only thing that raises one. A chit with nothing left closes itself: there
     * is no state in which a spent chit is still waiting to be spent.
     */
    public void redeem(ChitRedemption redemption) {
        redemption.setChitAccount(this);
        redemptions.add(redemption);

        if (redemption.getGrams() != null) {
            balanceGrams = Money.weight(balanceGrams.subtract(redemption.getGrams()));
        } else {
            balanceAmount = Money.money(balanceAmount.subtract(redemption.getAmount()));
        }
        syncGoldCost();

        if (isSpent()) {
            status = ChitStatus.CLOSED;
            closedOn = redemption.getRedeemedOn();
            closeReason = redemption.getKind() == RedemptionKind.CASH
                    ? "Withdrawn as cash"
                    : "Spent against an invoice";
        }
    }

    /**
     * Puts a redemption back, because the invoice it was spent against was
     * cancelled or deleted.
     *
     * <p>A chit that had closed opens again: the customer has their value back
     * and may spend it on something else.
     */
    public void returnRedemption(ChitRedemption redemption) {
        redemption.setStatus(RedemptionStatus.CANCELLED);

        if (redemption.getGrams() != null) {
            balanceGrams = Money.weight(balanceGrams.add(redemption.getGrams())).min(accruedGrams);
        } else {
            balanceAmount = Money.money(balanceAmount.add(redemption.getAmount())).min(paidAmount);
        }
        syncGoldCost();

        if (status == ChitStatus.CLOSED && !isSpent()) {
            status = ChitStatus.MATURED;
            closedOn = null;
            closeReason = null;
        }
    }

    /**
     * Keeps a gold chit's rupee balance tied to the grams it still holds.
     *
     * <p>A gold chit carries two balances for one thing: the grams, and what
     * those grams cost to buy. What they are worth today is grams times
     * today's rate and is never stored; the rupee balance is the cost, which
     * the database will not let exceed what was paid in. Deriving it from the
     * grams rather than adjusting it in steps means the two cannot drift, and
     * a chit spent to the last milligram ends at zero rather than at a
     * rounding crumb.
     */
    private void syncGoldCost() {
        if (!isGold()) {
            return;
        }
        if (balanceGrams.signum() == 0) {
            balanceAmount = Money.ZERO;
        } else if (balanceGrams.compareTo(accruedGrams) >= 0) {
            balanceAmount = paidAmount;
        } else {
            balanceAmount = Money.money(
                    paidAmount.multiply(balanceGrams).divide(accruedGrams, Money.MONEY_SCALE, Money.ROUNDING));
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof ChitAccount account && id != null && id.equals(account.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "ChitAccount(" + chitNumber + ")";
    }
}
