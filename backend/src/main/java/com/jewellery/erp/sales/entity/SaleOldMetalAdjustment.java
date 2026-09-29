package com.jewellery.erp.sales.entity;

import com.jewellery.erp.common.entity.AuditableEntity;
import com.jewellery.erp.oldmetal.entity.OldMetalTransaction;
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

/** Value from one old gold / silver purchase bill applied to this invoice. */
@Entity
@Table(name = "sale_old_metal_adjustments")
@Getter
@Setter
@NoArgsConstructor
public class SaleOldMetalAdjustment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false, updatable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "old_metal_transaction_id", nullable = false, updatable = false)
    private OldMetalTransaction oldMetalTransaction;

    @Column(name = "adjustment_amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal adjustmentAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AdjustmentStatus status = AdjustmentStatus.ACTIVE;
}
