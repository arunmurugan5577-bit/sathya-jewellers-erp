package com.jewellery.erp.sales.entity;

import com.jewellery.erp.common.entity.AuditableEntity;
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
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Money received against an invoice. Payments are only ever added, never edited. */
@Entity
@Table(name = "sale_payments")
@Getter
@Setter
@NoArgsConstructor
public class SalePayment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false, updatable = false)
    private Sale sale;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20, updatable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "reference_number", length = 100, updatable = false)
    private String referenceNumber;

    @Column(name = "payment_date", nullable = false, updatable = false)
    private LocalDate paymentDate;

    @Column(name = "remarks", length = 300, updatable = false)
    private String remarks;
}
