package com.jewellery.erp.inventory.entity;

import com.jewellery.erp.category.entity.Category;
import com.jewellery.erp.common.entity.AuditableEntity;
import com.jewellery.erp.hsn.entity.HsnCode;
import com.jewellery.erp.common.util.Money;
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
 * <p>One exception, marked by {@link #bulk}: metti and similar small articles
 * arrive as a box of a hundred identical pieces, are weighed as a box, and are
 * sold by the gram. Numbering each one would mean a hundred tags nobody reads,
 * so the box is the row - one serial, one tag - and
 * {@link #remainingWeightGrams} falls with each invoice instead of
 * {@link #status} flipping once. Everything else about the row is unchanged.
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

    /** What the whole piece - or the whole box - weighed at stock-in. Never changes once billing starts. */
    @Column(name = "weight_grams", nullable = false, precision = 12, scale = 3)
    private BigDecimal weightGrams;

    /**
     * A box sold by weight rather than one article.
     *
     * <p>Set at stock-in and fixed from the first invoice onwards: it decides how
     * the weight on an invoice line is arrived at, so changing it under a sale
     * that has already been billed would rewrite history.
     */
    @Column(name = "bulk", nullable = false)
    private boolean bulk = false;

    /**
     * Grams still unsold.
     *
     * <p>Equal to {@link #weightGrams} at stock-in. For an ordinary article it is
     * all or nothing; for a {@link #bulk} box it comes down invoice by invoice.
     * Maintained only by {@link #billOut} and {@link #returnToStock}, and backed
     * by check constraints that admit nothing those two cannot produce.
     */
    @Column(name = "remaining_weight_grams", nullable = false, precision = 12, scale = 3)
    private BigDecimal remainingWeightGrams;

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

    /** True once part of a box has been sold - the point after which its weight is history. */
    public boolean isPartlySold() {
        return remainingWeightGrams != null && weightGrams != null
                && remainingWeightGrams.compareTo(weightGrams) < 0;
    }

    /**
     * The weight this piece contributes to an invoice line.
     *
     * <p>An ordinary article is sold whole, so the question does not arise. A box
     * is sold by the gram and the counter weighs out whatever the customer took,
     * which is the only figure the shop has.
     */
    public BigDecimal billableWeight(BigDecimal requested) {
        return bulk ? Money.weight(requested) : weightGrams;
    }

    /**
     * Takes weight off the piece. Caller must hold the row lock and have checked
     * that this much is there.
     *
     * <p>An ordinary article always goes out whole, so this empties it in one
     * step. SOLD is set exactly when nothing is left, which is the invariant the
     * sales module reads back.
     */
    public void billOut(BigDecimal grams) {
        BigDecimal left = remainingWeightGrams.subtract(Money.weight(grams));
        if (left.signum() < 0) {
            throw new IllegalStateException(
                    "Billing " + grams + "g off serial " + serialNumber + " would leave " + left + "g");
        }
        remainingWeightGrams = left;
        status = left.signum() == 0 ? InventoryStatus.SOLD : InventoryStatus.AVAILABLE;
    }

    /**
     * Puts weight back after the invoice that took it is cancelled.
     *
     * <p>An ordinary article is restored outright rather than by addition: the
     * line's weight and the piece's weight are the same figure, and adding is one
     * more place for them to drift. A box is capped at what it held, so a double
     * cancellation cannot inflate it.
     */
    public void returnToStock(BigDecimal grams) {
        remainingWeightGrams = bulk
                ? remainingWeightGrams.add(Money.weight(grams)).min(weightGrams)
                : weightGrams;
        status = InventoryStatus.AVAILABLE;
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
