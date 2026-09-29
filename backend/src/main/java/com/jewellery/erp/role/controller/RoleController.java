package com.jewellery.erp.role.controller;

import com.jewellery.erp.permission.PermissionCatalog;
import com.jewellery.erp.role.dto.RoleDto;
import com.jewellery.erp.role.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/roles")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Roles", description = "Read roles (requires USER_VIEW)")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_VIEW + "')")
    @Operation(summary = "List roles", description = "Populates the role selector on the user form.")
    public ResponseEntity<List<RoleDto>> findAll() {
        return ResponseEntity.ok(roleService.findAll());
    }
}
