package com.jewellery.erp.permission.service;

import com.jewellery.erp.permission.PermissionCatalog;
import com.jewellery.erp.permission.dto.ModulePermissionsDto;
import com.jewellery.erp.permission.dto.PermissionDto;
import com.jewellery.erp.permission.entity.PermissionAction;
import com.jewellery.erp.permission.mapper.PermissionMapper;
import com.jewellery.erp.permission.repository.PermissionRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only access to the permission catalogue.
 *
 * <p>Permissions are reference data owned by Flyway; there is deliberately no
 * create or delete operation here.
 */
@Service
@Transactional(readOnly = true)
public class PermissionService {

    /** Fixed action order so the checkbox matrix never reshuffles between renders. */
    private static final Comparator<PermissionDto> ACTION_ORDER =
            Comparator.comparingInt(dto -> switch (PermissionAction.valueOf(dto.action())) {
                case VIEW -> 0;
                case CREATE -> 1;
                case EDIT -> 2;
                case DELETE -> 3;
                case EXPORT -> 4;
            });

    private final PermissionRepository permissionRepository;
    private final PermissionMapper permissionMapper;

    public PermissionService(PermissionRepository permissionRepository, PermissionMapper permissionMapper) {
        this.permissionRepository = permissionRepository;
        this.permissionMapper = permissionMapper;
    }

    public List<PermissionDto> findAll() {
        return permissionRepository.findAllByOrderByModuleAscActionAsc().stream()
                .map(permissionMapper::toDto)
                .toList();
    }

    /** The permission matrix, grouped and ordered the way the UI renders it. */
    public List<ModulePermissionsDto> findGroupedByModule() {
        Map<String, List<PermissionDto>> byModule = permissionRepository.findAll().stream()
                .map(permissionMapper::toDto)
                .collect(Collectors.groupingBy(PermissionDto::module));

        return byModule.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> PermissionCatalog.orderOf(entry.getKey())))
                .map(entry -> new ModulePermissionsDto(
                        entry.getKey(),
                        PermissionCatalog.labelFor(entry.getKey()),
                        entry.getValue().stream().sorted(ACTION_ORDER).toList()))
                .toList();
    }
}
