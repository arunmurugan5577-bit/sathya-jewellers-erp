package com.jewellery.erp.inventory.spec;

import com.jewellery.erp.inventory.dto.InventoryItemFilter;
import com.jewellery.erp.inventory.entity.InventoryItem;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Criteria for the inventory list.
 *
 * <p>All filtering happens in SQL against indexed columns. Loading the stock into
 * memory and filtering there would work for a shop with a hundred pieces and
 * fall over for one with fifty thousand - and the second is the case this system
 * has to survive.
 */
public final class InventoryItemSpecifications {

    private InventoryItemSpecifications() {}

    public static Specification<InventoryItem> matching(InventoryItemFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (isPresent(filter.search())) {
                String pattern = "%" + filter.search().trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("serialNumber")), pattern),
                        builder.like(builder.lower(root.get("description")), pattern),
                        builder.like(builder.lower(root.get("size")), pattern)));
            }
            if (isPresent(filter.serialNumber())) {
                // Partial match: staff routinely search by the last few digits.
                predicates.add(builder.like(
                        root.get("serialNumber"), "%" + filter.serialNumber().trim() + "%"));
            }
            if (filter.itemTypeId() != null) {
                predicates.add(builder.equal(root.get("itemType").get("id"), filter.itemTypeId()));
            }
            if (filter.purityId() != null) {
                predicates.add(builder.equal(root.get("purity").get("id"), filter.purityId()));
            }
            if (filter.categoryId() != null) {
                predicates.add(builder.equal(root.get("category").get("id"), filter.categoryId()));
            }
            if (filter.subCategoryId() != null) {
                predicates.add(builder.equal(root.get("subCategory").get("id"), filter.subCategoryId()));
            }
            if (filter.active() != null) {
                predicates.add(builder.equal(root.get("active"), filter.active()));
            }
            if (filter.status() != null) {
                predicates.add(builder.equal(root.get("status"), filter.status()));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
