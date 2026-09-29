package com.jewellery.erp.inventory.entity;

import com.jewellery.erp.category.entity.Category;
import com.jewellery.erp.common.entity.AuditableEntity;
import com.jewellery.erp.hsn.entity.HsnCode;
import com.jewellery.erp.itemtype.entity.ItemType;
import com.jewellery.erp.purity.entity.Purity;
import com.jewellery.erp.subcategory.entity.SubCategory;
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
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One physical piece of jewellery.
 *
 * <p>This is the central modelling decision of the system. Jewellery is not
 * quantity-based stock: two 22K rings of the same design have different weights
 * and are different objects, so "5 units of SKU 123" cannot describe them. Each
 * row here is one piece with its own serial number, weight and purity, which is
 * what lets a future sale, return or old-gold exchange reference the exact piece
 * that moved.
 *
 * <p>Consequences that the rest of the design depends on:
 *
 * <ul>
 *   <li>{@code serialNumber} is the business key - text, not a number, because
 *       "000001" and "1" are different serials and leading zeros must survive.
 *   <li>{@code weightGrams} is {@code NUMERIC(12,3)}. Gold is priced per gram;
 *       a floating point milligram error becomes a rupee error on every invoice.
 *   <li>{@code purity} must belong to {@code itemType}, and {@code subCategory}
 *       to {@code category}. The service enforces both - the foreign keys alone
 *       cannot express a cross-column rule.
 * </ul>
 */
@Entity
@Table(name = "inventory_items")
@Getter
@Setter
@NoArgsConstructor
public class InventoryItem extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Exactly six digits, unique across the shop. */
    @Column(name = "serial_number", nullable = false, length = 6)
    private String serialNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_type_id", nullable = false)
    private ItemType itemType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purity_id", nullable = false)
    private Purity purity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /** Optional: not every category is subdivided. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_category_id")
    private SubCategory subCategory;

    /** Optional at stock-in; required by the time the piece is billed. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hsn_id")
    private HsnCode hsnCode;

    /**
     * Free text for now - "7", "Medium", "18 inch". A per-category size master is
     * a later refinement; storing text keeps every category expressible today
     * without inventing a taxonomy the shop has not asked for.
     */
    @Column(name = "size", length = 50)
    private String size;

    @Column(name = "weight_grams", nullable = false, precision = 12, scale = 3)
    private BigDecimal weightGrams;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** AVAILABLE until invoiced. Changed only by the sales module, never by the inventory form. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InventoryStatus status = InventoryStatus.AVAILABLE;

    /** A piece can be billed only while it is still in the shop and not switched off. */
    public boolean isSellable() {
        return status == InventoryStatus.AVAILABLE && active;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InventoryItem item) || id == null) {
            return false;
        }
        return id.equals(item.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "InventoryItem(serialNumber=" + serialNumber + ")";
    }
}
