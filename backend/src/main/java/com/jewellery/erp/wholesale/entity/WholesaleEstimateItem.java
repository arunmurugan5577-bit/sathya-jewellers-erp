package com.jewellery.erp.wholesale.entity;

import com.jewellery.erp.inventory.entity.InventoryItem;
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
 * One piece on a wholesale estimate.
 *
 * <p>The serial number and the name are copied in rather than read through the
 * inventory row: the piece is sold, and what it was called on the day is part
 * of the document.
 */
@Entity
@Table(name = "wholesale_estimate_items")
@Getter
@Setter
@NoArgsConstructor
public class WholesaleEstimateItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estimate_id", nullable = false)
    private WholesaleEstimate estimate;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @Column(name = "serial_number", nullable = false, length = 6)
    private String serialNumber;

    @Column(name = "jewel_name", nullable = false, length = 200)
    private String jewelName;

    /** As weighed. */
    @Column(name = "jewel_weight_grams", nullable = false, precision = 10, scale = 3)
    private BigDecimal jewelWeightGrams;

    /** The touch, out of 100. */
    @Column(name = "pure_percentage", nullable = false, precision = 6, scale = 2)
    private BigDecimal purePercentage;

    /** Jewel weight at that touch, to the milligram. */
    @Column(name = "pure_weight_grams", nullable = false, precision = 10, scale = 3)
    private BigDecimal pureWeightGrams;

    @Column(name = "rate_per_gram", nullable = false, precision = 12, scale = 2)
    private BigDecimal ratePerGram;

    @Column(name = "making_charge", nullable = false, precision = 12, scale = 2)
    private BigDecimal makingCharge = BigDecimal.ZERO;

    @Column(name = "stone_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal stoneAmount = BigDecimal.ZERO;

    @Column(name = "item_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal itemAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_status", nullable = false, length = 16)
    private WholesaleItemStatus lineStatus = WholesaleItemStatus.ACTIVE;
}
