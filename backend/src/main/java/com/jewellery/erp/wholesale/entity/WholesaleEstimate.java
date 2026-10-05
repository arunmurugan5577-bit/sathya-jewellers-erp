package com.jewellery.erp.wholesale.entity;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A wholesale estimate: gold sold by pure weight, against a running account.
 *
 * <p>Not a tax invoice. The document the shop uses is an estimate, and the
 * totals are held in their own columns so a taxable variant could be added
 * later without rewriting what is already recorded.
 *
 * <p>Amounts are written once, by {@code WholesaleService} from the
 * {@code WholesaleCalculator} result. The opening balance is a snapshot of the
 * party's account at the moment the estimate was raised, so reprinting an old
 * document shows what it showed on the day.
 */
@Entity
@Table(name = "wholesale_estimates")
@Getter
@Setter
@NoArgsConstructor
public class WholesaleEstimate extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "estimate_number", nullable = false, length = 16, updatable = false)
    private String estimateNumber;

    @Column(name = "estimate_date", nullable = false)
    private LocalDate estimateDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "customer_code", nullable = false, length = 20)
    private String customerCode;

    @Column(name = "customer_name", nullable = false, length = 150)
    private String customerName;

    @Column(name = "customer_mobile", length = 15)
    private String customerMobile;

    /** One gram of pure gold on the day. The whole estimate is valued from it. */
    @Column(name = "pure_rate_per_gram", nullable = false, precision = 12, scale = 2)
    private BigDecimal pureRatePerGram;

    @Column(name = "opening_pure_grams", nullable = false, precision = 12, scale = 3)
    private BigDecimal openingPureGrams;

    @Column(name = "opening_misc_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal openingMiscAmount;

    @Column(name = "opening_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal openingValue;

    @Column(name = "total_pure_grams", nullable = false, precision = 12, scale = 3)
    private BigDecimal totalPureGrams;

    @Column(name = "total_misc_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalMiscAmount;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "closing_pure_grams", nullable = false, precision = 12, scale = 3)
    private BigDecimal closingPureGrams;

    @Column(name = "closing_misc_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal closingMiscAmount;

    @Column(name = "closing_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal closingValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private WholesaleStatus status = WholesaleStatus.COMPLETED;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by", length = 100)
    private String cancelledBy;

    @OneToMany(mappedBy = "estimate", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber")
    private List<WholesaleEstimateItem> items = new ArrayList<>();

    public void addItem(WholesaleEstimateItem item) {
        item.setEstimate(this);
        items.add(item);
    }

    public boolean isCancelled() {
        return status == WholesaleStatus.CANCELLED;
    }
}
