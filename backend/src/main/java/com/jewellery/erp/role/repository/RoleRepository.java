package com.jewellery.erp.role.repository;

import com.jewellery.erp.role.entity.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);

    @EntityGraph(attributePaths = {"permissions"})
    Optional<Role> findWithPermissionsByName(String name);
}
