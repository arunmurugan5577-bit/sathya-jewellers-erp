package com.jewellery.erp.wholesale.service;

import com.jewellery.erp.wholesale.entity.WholesaleEstimate;
import com.jewellery.erp.wholesale.entity.WholesaleStatus;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Filters for the wholesale estimate list. Every part is optional. */
public final class WholesaleSpecifications {

    private WholesaleSpecifications() {}

    public static Specification<WholesaleEstimate> matching(
            LocalDate from, LocalDate to, Long customerId, WholesaleStatus status) {
        return (root, query, builder) -> {
            List<Predicate> where = new ArrayList<>();
            if (from != null) {
                where.add(builder.greaterThanOrEqualTo(root.get("estimateDate"), from));
            }
            if (to != null) {
                where.add(builder.lessThanOrEqualTo(root.get("estimateDate"), to));
            }
            if (customerId != null) {
                where.add(builder.equal(root.get("customer").get("id"), customerId));
            }
            if (status != null) {
                where.add(builder.equal(root.get("status"), status));
            }
            return where.isEmpty() ? null : builder.and(where.toArray(Predicate[]::new));
        };
    }
}
