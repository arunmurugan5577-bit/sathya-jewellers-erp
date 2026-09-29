package com.jewellery.erp.permission.controller;

import com.jewellery.erp.permission.PermissionCatalog;
import com.jewellery.erp.permission.dto.ModulePermissionsDto;
import com.jewellery.erp.permission.dto.PermissionDto;
import com.jewellery.erp.permission.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The permission catalogue, used to render the user permission screen.
 *
 * <p>Guarded by {@code USER_VIEW}: knowing which permissions exist is only
 * useful to someone who administers users.
 */
@RestController
@RequestMapping("/api/permissions")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Permissions", description = "Read the permission catalogue (requires USER_VIEW)")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_VIEW + "')")
    @Operation(summary = "List every permission")
    public ResponseEntity<List<PermissionDto>> findAll() {
        return ResponseEntity.ok(permissionService.findAll());
    }

    @GetMapping("/grouped")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_VIEW + "')")
    @Operation(
            summary = "The permission matrix",
            description = "Permissions grouped by module, in the order the permission screen renders them.")
    public ResponseEntity<List<ModulePermissionsDto>> findGrouped() {
        return ResponseEntity.ok(permissionService.findGroupedByModule());
    }
}
