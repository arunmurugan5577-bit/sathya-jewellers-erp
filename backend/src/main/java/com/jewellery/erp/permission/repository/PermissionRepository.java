package com.jewellery.erp.permission.repository;

import com.jewellery.erp.permission.entity.Permission;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    List<Permission> findAllByOrderByModuleAscActionAsc();

    /** Resolves the ids submitted by the user permission screen. */
    List<Permission> findAllByIdIn(Set<Long> ids);
}
