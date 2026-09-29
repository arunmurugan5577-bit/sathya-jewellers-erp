package com.jewellery.erp.role.service;

import com.jewellery.erp.role.dto.RoleDto;
import com.jewellery.erp.role.entity.Role;
import com.jewellery.erp.role.mapper.RoleMapper;
import com.jewellery.erp.role.repository.RoleRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read access to roles.
 *
 * <p>Roles are seeded reference data in this release. A full role management
 * module (create a role, edit its permissions) is a listed future module; this
 * service is the seam it will grow from.
 */
@Service
@Transactional(readOnly = true)
public class RoleService {

    private final RoleRepository roleRepository;
    private final RoleMapper roleMapper;

    public RoleService(RoleRepository roleRepository, RoleMapper roleMapper) {
        this.roleRepository = roleRepository;
        this.roleMapper = roleMapper;
    }

    public List<RoleDto> findAll() {
        return roleRepository.findAll().stream()
                .sorted(Comparator.comparing(Role::getName))
                .map(roleMapper::toDto)
                .toList();
    }

}
