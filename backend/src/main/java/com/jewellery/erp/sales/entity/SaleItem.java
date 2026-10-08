package com.jewellery.erp.sales.entity;

import com.jewellery.erp.category.entity.Category;
import com.jewellery.erp.common.entity.AuditableEntity;
import com.jewellery.erp.inventory.entity.InventoryItem;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One piece on an invoice, with a snapshot of what it was when sold. Identity is
 * by reference (no equals override) because lines are held in a Set before they
 * have ids.
 */
@Entity
@Table(name = "sale_items")
@Getter
@Setter
@NoArgsConstructor
public class SaleItem extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false, updatable = false)
    private Sale sale;

    @Column(name = "line_number", nullable = false, updatable = false)
    private short lineNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false, updatable = false)
    private InventoryItem inventoryItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_status", nullable = false, length = 20)
    private SaleItemStatus lineStatus = SaleItemStatus.ACTIVE;

    @Column(name = "serial_number", nullable = false, length = 6, updatable = false)
    private String serialNumber;

    /**
     * Whether this line sold weight out of a box rather than one whole article.
     *
     * <p>A snapshot like the serial number beside it. It is what narrows
     * {@code uk_sale_items_active_inventory_item} - the index that stops a single
     * piece being on two live invoices - so that a box, which belongs on as many
     * live invoices as it has customers, is left out of it.
     */
    @Column(name = "bulk", nullable = false, updatable = false)
    private boolean bulk;

    @Column(name = "particulars", nullable = false, length = 200, updatable = false)
    private String particulars;

    @Column(name = "hsn_code", nullable = false, length = 8, updatable = false)
    private String hsnCode;

    @Column(name = "gst_percentage", nullable = false, precision = 5, scale = 2, updatable = false)
    private BigDecimal gstPercentage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_type_id", nullable = false, updatable = false)
    private ItemType itemType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purity_id", nullable = false, updatable = false)
    private Purity purity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false, updatable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_category_id", updatable = false)
    private SubCategory subCategory;

    @Column(name = "net_weight_grams", nullable = false, precision = 12, scale = 3, updatable = false)
    private BigDecimal netWeightGrams;

    @Column(name = "wastage_percentage", nullable = false, precision = 6, scale = 2, updatable = false)
    private BigDecimal wastagePercentage;

    @Column(name = "wastage_weight_grams", nullable = false, precision = 12, scale = 3, updatable = false)
    private BigDecimal wastageWeightGrams;

    @Column(name = "gross_weight_grams", nullable = false, precision = 12, scale = 3, updatable = false)
    private BigDecimal grossWeightGrams;

    @Column(name = "rate_per_gram", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal ratePerGram;

    @Column(name = "making_charge", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal makingCharge;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "cgst_amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal cgstAmount;

    @Column(name = "sgst_amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal sgstAmount;

    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal discountAmount;
}
