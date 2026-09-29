package com.jewellery.erp.sales.entity;

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
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A TAX INVOICE. Amounts are written once, by {@code SaleService} from the
 * {@code SaleCalculator} result; afterwards only payments, remarks and
 * cancellation change it. The table's check constraints re-verify every total.
 */
@Entity
@Table(name = "sales")
@Getter
@Setter
@NoArgsConstructor
public class Sale extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", nullable = false, length = 16, updatable = false)
    private String invoiceNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @Column(name = "invoice_date", nullable = false, updatable = false)
    private LocalDate invoiceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SaleStatus status = SaleStatus.COMPLETED;

    @Column(name = "customer_name", nullable = false, length = 150, updatable = false)
    private String customerName;

    @Column(name = "customer_mobile", length = 20, updatable = false)
    private String customerMobile;

    @Column(name = "customer_address", length = 600, updatable = false)
    private String customerAddress;

    @Column(name = "customer_gstin", length = 15, updatable = false)
    private String customerGstin;

    @Column(name = "seller_name", nullable = false, length = 150, updatable = false)
    private String sellerName;

    @Column(name = "seller_address", length = 600, updatable = false)
    private String sellerAddress;

    @Column(name = "seller_mobile", length = 40, updatable = false)
    private String sellerMobile;

    @Column(name = "seller_gstin", length = 15, updatable = false)
    private String sellerGstin;

    @Column(name = "subtotal", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal subtotal;

    @Column(name = "cgst_amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal cgstAmount;

    @Column(name = "sgst_amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal sgstAmount;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal taxAmount;

    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal discountAmount;

    @Column(name = "grand_total", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal grandTotal;

    @Column(name = "old_metal_adjustment_amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal oldMetalAdjustmentAmount;

    @Column(name = "round_off_amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal roundOffAmount;

    @Column(name = "net_payable", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal netPayable;

    @Column(name = "amount_paid", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountPaid;

    @Column(name = "balance_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal balanceAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by", length = 100)
    private String cancelledBy;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    // Sets rather than lists: the detail query fetches all three collections at
    // once, which Hibernate only allows for unordered bags if they are Sets.
    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL)
    @OrderBy("lineNumber ASC")
    private Set<SaleItem> items = new LinkedHashSet<>();

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private Set<SaleOldMetalAdjustment> oldMetalAdjustments = new LinkedHashSet<>();

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private Set<SalePayment> payments = new LinkedHashSet<>();

    public void addItem(SaleItem item) {
        item.setSale(this);
        items.add(item);
    }

    public void addAdjustment(SaleOldMetalAdjustment adjustment) {
        adjustment.setSale(this);
        oldMetalAdjustments.add(adjustment);
    }

    public void addPayment(SalePayment payment) {
        payment.setSale(this);
        payments.add(payment);
    }

    public boolean isCancelled() {
        return status == SaleStatus.CANCELLED;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Sale sale) || id == null) {
            return false;
        }
        return id.equals(sale.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
