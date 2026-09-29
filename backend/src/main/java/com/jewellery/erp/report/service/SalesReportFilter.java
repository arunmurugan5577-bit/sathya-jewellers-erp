package com.jewellery.erp.report.service;

import com.jewellery.erp.sales.entity.PaymentStatus;
import com.jewellery.erp.sales.entity.SaleStatus;
import java.time.LocalDate;

/**
 * Invoices dated between two dates (inclusive).
 *
 * @param status {@code null} means every status, cancelled included
 */
public record SalesReportFilter(
        LocalDate startDate,
        LocalDate endDate,
        SaleStatus status,
        PaymentStatus paymentStatus,
        Long customerId) {}
