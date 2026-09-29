package com.jewellery.erp.common.repository;

import com.jewellery.erp.common.dto.MasterFilter;
import com.jewellery.erp.common.entity.NamedMasterEntity;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Search and status criteria shared by the "name + code" masters.
 *
 * <p>Written once against {@link NamedMasterEntity} rather than copied into each
 * module: the three masters filter on identical columns, and a divergence
 * between them would be a bug rather than a feature.
 */
public final class NamedMasterSpecifications {

    private NamedMasterSpecifications() {}

    public static <T extends NamedMasterEntity> Specification<T> matching(MasterFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.search() != null && !filter.search().isBlank()) {
                String pattern = "%" + filter.search().trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("name")), pattern),
                        builder.like(builder.lower(root.get("code")), pattern),
                        builder.like(builder.lower(root.get("description")), pattern)));
            }
            if (filter.active() != null) {
                predicates.add(builder.equal(root.get("active"), filter.active()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
