package com.jewellery.erp.user.controller;

import com.jewellery.erp.common.dto.ApiErrorResponse;
import com.jewellery.erp.common.dto.PageResponse;
import com.jewellery.erp.common.dto.UpdateStatusRequest;
import com.jewellery.erp.permission.PermissionCatalog;
import com.jewellery.erp.user.dto.ChangePasswordRequest;
import com.jewellery.erp.user.dto.CreateUserRequest;
import com.jewellery.erp.user.dto.ResetPasswordRequest;
import com.jewellery.erp.user.dto.UpdateUserPermissionsRequest;
import com.jewellery.erp.user.dto.UpdateUserRequest;
import com.jewellery.erp.user.dto.UserDto;
import com.jewellery.erp.user.dto.UserFilter;
import com.jewellery.erp.user.dto.UserPermissionsDto;
import com.jewellery.erp.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * User administration.
 *
 * <p>Every endpoint names the permission it requires. The annotations are the
 * enforcement point - the Angular client hides the same actions, but that is
 * cosmetic.
 */
@RestController
@RequestMapping("/api/users")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Users", description = "Create and manage users, their status and their permissions")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_VIEW + "')")
    @Operation(summary = "List users", description = "Paginated and filtered on the server. Requires USER_VIEW.")
    public ResponseEntity<PageResponse<UserDto>> findAll(
            @Parameter(description = "Matched against username, full name and e-mail")
            @RequestParam(required = false) String search,
            @Parameter(description = "Filter by status; omit for any")
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Long roleId,
            @ParameterObject @PageableDefault(size = 20, sort = "username", direction = Sort.Direction.ASC)
            Pageable pageable) {

        return ResponseEntity.ok(userService.findAll(new UserFilter(search, active, roleId), pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_VIEW + "')")
    @Operation(summary = "Get one user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Found"),
        @ApiResponse(responseCode = "404", description = "No such user",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<UserDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_CREATE + "')")
    @Operation(summary = "Create a user", description = "Requires USER_CREATE.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created"),
        @ApiResponse(responseCode = "400", description = "Validation failed",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Username or e-mail already exists",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<UserDto> create(@Valid @RequestBody CreateUserRequest request) {
        UserDto created = userService.create(request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/users/{id}").buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_EDIT + "')")
    @Operation(summary = "Update a user", description = "The username cannot be changed. Requires USER_EDIT.")
    public ResponseEntity<UserDto> update(
            @PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_EDIT + "')")
    @Operation(
            summary = "Activate or deactivate a user",
            description = "Deactivating also revokes the user's live sessions. Requires USER_EDIT.")
    public ResponseEntity<UserDto> updateStatus(
            @PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(userService.updateStatus(id, request.active()));
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_EDIT + "')")
    @Operation(
            summary = "Reset a user's password",
            description = "Sets a new password and ends every session that user has open. Requires USER_EDIT.")
    @ApiResponse(responseCode = "204", description = "Password reset")
    public ResponseEntity<Void> resetPassword(
            @PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_VIEW + "')")
    @Operation(
            summary = "The user's permission matrix",
            description = "Returns the whole catalogue plus the grants this user holds directly and by role.")
    public ResponseEntity<UserPermissionsDto> findPermissions(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findPermissions(id));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_EDIT + "')")
    @Operation(
            summary = "Replace the user's direct permissions",
            description = "Send the complete set of permission ids the user should hold. "
                    + "Live sessions are revoked so the change applies immediately. Requires USER_EDIT.")
    public ResponseEntity<UserPermissionsDto> updatePermissions(
            @PathVariable Long id, @Valid @RequestBody UpdateUserPermissionsRequest request) {
        return ResponseEntity.ok(userService.updatePermissions(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_DELETE + "')")
    @Operation(
            summary = "Delete a user",
            description = "Permitted only for an account that has never signed in; anything else must be "
                    + "deactivated so its audit history stays intact. Requires USER_DELETE.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deleted"),
        @ApiResponse(responseCode = "400", description = "The account has history and must be deactivated instead",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/change-password")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Change your own password",
            description = "Available to every signed-in user; the current password must be supplied.")
    @ApiResponse(responseCode = "204", description = "Password changed")
    public ResponseEntity<Void> changeOwnPassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changeOwnPassword(request);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
