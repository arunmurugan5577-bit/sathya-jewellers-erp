package com.jewellery.erp.oldmetal.entity;

import com.jewellery.erp.common.entity.AuditableEntity;
import com.jewellery.erp.itemtype.entity.ItemType;
import com.jewellery.erp.purity.entity.Purity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One numbered row on a purchase bill: "Gold coin, 7108, 8.000 g @ 14,370 = 1,14,960". */
@Entity
@Table(name = "old_metal_transaction_items")
@Getter
@Setter
@NoArgsConstructor
public class OldMetalTransactionItem extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    private OldMetalTransaction transaction;

    @Column(name = "line_number", nullable = false, updatable = false)
    private short lineNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_type_id", nullable = false, updatable = false)
    private ItemType itemType;

    /** Recorded for reference; the amount follows the gross weight, not this, and is not purity-adjusted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purity_id", updatable = false)
    private Purity purity;

    @Column(name = "particulars", nullable = false, length = 200, updatable = false)
    private String particulars;

    @Column(name = "hsn_code", nullable = false, length = 8, updatable = false)
    private String hsnCode;

    @Column(name = "net_weight_grams", nullable = false, precision = 12, scale = 3, updatable = false)
    private BigDecimal netWeightGrams;

    /** Optional - the shop's bill leaves it as "-" for a coin. */
    @Column(name = "gross_weight_grams", precision = 12, scale = 3, updatable = false)
    private BigDecimal grossWeightGrams;

    @Column(name = "rate_per_gram", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal ratePerGram;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal amount;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OldMetalTransactionItem item) || id == null) {
            return false;
        }
        return id.equals(item.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
