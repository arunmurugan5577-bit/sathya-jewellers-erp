package com.jewellery.erp.oldmetal.entity;

import com.jewellery.erp.common.entity.AuditableEntity;
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
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Old gold or silver bought from a customer - one PURCHASE BILL.
 *
 * <p>Its value can be applied, in part or whole, to one or more later sales. The
 * usage bookkeeping lives here, behind {@link #applyUsage} and
 * {@link #reverseUsage}, so the status can never drift from {@code usedAmount}:
 * the database check constraint rejects any combination these methods would not
 * produce.
 */
@Entity
@Table(name = "old_metal_transactions")
@Getter
@Setter
@NoArgsConstructor
public class OldMetalTransaction extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_number", nullable = false, length = 16, updatable = false)
    private String transactionNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @Column(name = "transaction_date", nullable = false, updatable = false)
    private LocalDate transactionDate;

    @Column(name = "customer_name", nullable = false, length = 150, updatable = false)
    private String customerName;

    @Column(name = "customer_mobile", length = 20, updatable = false)
    private String customerMobile;

    @Column(name = "customer_address", length = 600, updatable = false)
    private String customerAddress;

    @Column(name = "seller_name", nullable = false, length = 150, updatable = false)
    private String sellerName;

    @Column(name = "seller_address", length = 600, updatable = false)
    private String sellerAddress;

    @Column(name = "seller_mobile", length = 40, updatable = false)
    private String sellerMobile;

    @Column(name = "seller_gstin", length = 15, updatable = false)
    private String sellerGstin;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal totalAmount;

    @Column(name = "used_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal usedAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OldMetalStatus status = OldMetalStatus.AVAILABLE;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by", length = 100)
    private String cancelledBy;

    /** Optimistic lock, on top of the row lock taken when a sale uses this bill. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber ASC")
    private List<OldMetalTransactionItem> items = new ArrayList<>();

    public void addItem(OldMetalTransactionItem item) {
        item.setTransaction(this);
        items.add(item);
    }

    public BigDecimal availableAmount() {
        return totalAmount.subtract(usedAmount);
    }

    /** Consumes part of the value. Caller must hold the row lock and have checked availability. */
    public void applyUsage(BigDecimal amount) {
        usedAmount = usedAmount.add(amount);
        refreshStatus();
    }

    /** Gives value back after the sale that used it is cancelled. */
    public void reverseUsage(BigDecimal amount) {
        usedAmount = usedAmount.subtract(amount);
        if (usedAmount.signum() < 0) {
            throw new IllegalStateException("Old metal usage would become negative on " + transactionNumber);
        }
        refreshStatus();
    }

    public void cancel(String reason, String by, Instant at) {
        status = OldMetalStatus.CANCELLED;
        cancelReason = reason;
        cancelledBy = by;
        cancelledAt = at;
    }

    private void refreshStatus() {
        if (usedAmount.signum() == 0) {
            status = OldMetalStatus.AVAILABLE;
        } else if (usedAmount.compareTo(totalAmount) < 0) {
            status = OldMetalStatus.PARTIALLY_USED;
        } else {
            status = OldMetalStatus.USED;
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OldMetalTransaction transaction) || id == null) {
            return false;
        }
        return id.equals(transaction.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
