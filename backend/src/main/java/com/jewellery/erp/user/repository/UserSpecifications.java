package com.jewellery.erp.user.repository;

import com.jewellery.erp.user.dto.UserFilter;
import com.jewellery.erp.user.entity.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Criteria for the user list.
 *
 * <p>Filtering happens in SQL, not in memory: the list endpoint must stay
 * O(page size) no matter how many users the shop accumulates.
 */
public final class UserSpecifications {

    private UserSpecifications() {}

    public static Specification<User> matching(UserFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.search() != null && !filter.search().isBlank()) {
                String pattern = "%" + filter.search().trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("username")), pattern),
                        builder.like(builder.lower(root.get("fullName")), pattern),
                        builder.like(builder.lower(root.get("email")), pattern)));
            }
            if (filter.active() != null) {
                predicates.add(builder.equal(root.get("active"), filter.active()));
            }
            if (filter.roleId() != null) {
                Join<Object, Object> roles = root.join("roles");
                predicates.add(builder.equal(roles.get("id"), filter.roleId()));
                query.distinct(true);
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
