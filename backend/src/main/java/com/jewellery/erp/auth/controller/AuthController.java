package com.jewellery.erp.auth.controller;

import com.jewellery.erp.auth.dto.AuthenticatedUserDto;
import com.jewellery.erp.auth.dto.LoginRequest;
import com.jewellery.erp.auth.dto.LoginResponse;
import com.jewellery.erp.auth.dto.RefreshTokenRequest;
import com.jewellery.erp.auth.service.AuthService;
import com.jewellery.erp.common.dto.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints.
 *
 * <p>Controllers in this application contain no business logic: they bind and
 * validate the request, delegate to a service and shape the HTTP response.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Login, token refresh and logout")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @SecurityRequirements // public endpoint - no bearer token expected
    @Operation(summary = "Sign in", description = "Exchanges username and password for an access and refresh token.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Authenticated"),
        @ApiResponse(responseCode = "400", description = "Validation failed",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid credentials, or the account is inactive or locked",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(
            summary = "Refresh the access token",
            description = "Exchanges a refresh token for a new token pair. The presented token is revoked "
                    + "(rotation), and the account status and permissions are re-read from the database.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "New token pair issued"),
        @ApiResponse(responseCode = "401", description = "The refresh token is invalid, revoked or expired",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Sign out", description = "Revokes the presented refresh token.")
    @ApiResponse(responseCode = "204", description = "Signed out")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        authService.logout(request == null ? null : request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Sign out everywhere", description = "Revokes every refresh token of the signed-in user.")
    @ApiResponse(responseCode = "204", description = "All sessions ended")
    public ResponseEntity<Void> logoutEverywhere() {
        authService.logoutEverywhere();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "The signed-in user",
            description = "Returns the current user together with the effective permission codes the UI uses "
                    + "to hide unavailable actions.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The current user"),
        @ApiResponse(responseCode = "401", description = "Not authenticated",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<AuthenticatedUserDto> currentUser() {
        return ResponseEntity.ok(authService.currentUser());
    }
}
