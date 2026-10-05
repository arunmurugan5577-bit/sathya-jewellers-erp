package com.jewellery.erp.wholesale.dto;

import com.jewellery.erp.wholesale.entity.WholesaleStatus;
import java.time.LocalDate;

/**
 * What the wholesale report covers.
 *
 * @param status null includes cancelled estimates, which is how a reconciliation
 *     finds the ones that were reversed
 */
public record WholesaleReportFilter(
        LocalDate startDate, LocalDate endDate, WholesaleStatus status, Long customerId) {}
