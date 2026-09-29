package com.jewellery.erp.common.exception;

import com.jewellery.erp.common.dto.ApiErrorResponse;
import com.jewellery.erp.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates every exception that escapes the service layer into the single
 * {@link ApiErrorResponse} envelope.
 *
 * <p>Two decisions worth knowing:
 *
 * <ul>
 *   <li>{@code AccessDeniedException} is handled here, because the catch-all
 *       {@code Exception} handler would otherwise turn a 403 into a 500. An
 *       anonymous caller still receives 401; only an authenticated one who
 *       lacks the permission receives 403.
 *   <li>Stack traces and JDBC messages never reach the client; they are logged
 *       server side and replaced with a neutral message.
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String VALIDATION_ERROR = "Validation Error";
    private static final String VALIDATION_FAILED = "Validation failed";

    /**
     * Human readable messages for the database constraints a user can realistically
     * trip. Anything not listed here degrades to a generic conflict message rather
     * than leaking the raw SQL error.
     */
    private static final Map<String, FieldMessage> CONSTRAINT_MESSAGES = Map.ofEntries(
            Map.entry("uk_inventory_items_serial",
                    new FieldMessage("serialNumber", "This serial number already exists.")),
            Map.entry("uk_item_types_name_lower",
                    new FieldMessage("name", "An item type with this name already exists.")),
            Map.entry("uk_item_types_code",
                    new FieldMessage("code", "An item type with this code already exists.")),
            Map.entry("uk_categories_name_lower",
                    new FieldMessage("name", "A category with this name already exists.")),
            Map.entry("uk_categories_code",
                    new FieldMessage("code", "A category with this code already exists.")),
            Map.entry("uk_sub_categories_category_name",
                    new FieldMessage("name", "This category already has a sub category with this name.")),
            Map.entry("uk_sub_categories_code",
                    new FieldMessage("code", "A sub category with this code already exists.")),
            Map.entry("uk_hsn_codes_code",
                    new FieldMessage("hsnCode", "This HSN code already exists.")),
            Map.entry("uk_purities_item_type_name",
                    new FieldMessage("name", "This item type already has a purity with this name.")),
            Map.entry("uk_purities_item_type_value",
                    new FieldMessage("purityValue", "This item type already has a purity with this value.")),
            Map.entry("uk_users_username_lower",
                    new FieldMessage("username", "This username is already taken.")),
            Map.entry("uk_users_email_lower",
                    new FieldMessage("email", "This e-mail address is already registered.")),
            // --- Sales & old metal: the race-condition backstops ----------------
            // These fire only when two requests pass the service checks at the same
            // instant; the row locks make that rare, the constraints make it harmless.
            Map.entry("uk_sale_items_active_inventory_item",
                    new FieldMessage("items", "One of these items has just been sold on another invoice.",
                            ErrorCode.ITEM_ALREADY_SOLD)),
            Map.entry("ck_old_metal_used_within_total",
                    new FieldMessage("oldMetalAdjustments",
                            "The old gold/silver value has just been used on another invoice.",
                            ErrorCode.OLD_METAL_AMOUNT_EXCEEDED)),
            Map.entry("uk_customers_code",
                    new FieldMessage("customerCode", "This customer code is already in use.")));

    // ---------------------------------------------------------------- 400 ---

    /** Bean Validation failures on an {@code @Valid @RequestBody} argument. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleBeanValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            // Keep the first message per field - the UI shows one message per control.
            fieldErrors.putIfAbsent(fieldError.getField(), defaultMessage(fieldError));
        }
        ex.getBindingResult()
                .getGlobalErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));

        return build(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, ErrorCode.VALIDATION_FAILED,
                VALIDATION_FAILED, request, fieldErrors);
    }

    /** Bean Validation failures on {@code @Validated} method parameters. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getConstraintViolations()
                .forEach(violation -> fieldErrors.putIfAbsent(
                        lastNode(violation.getPropertyPath().toString()), violation.getMessage()));

        return build(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, VALIDATION_FAILED, request, fieldErrors);
    }

    /** Domain rule violated, e.g. purity does not belong to the selected item type. */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessRule(
            BusinessRuleException ex, HttpServletRequest request) {

        ErrorCode code = ex.getCode() == null ? ErrorCode.VALIDATION_FAILED : ex.getCode();
        return build(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, code, ex.getMessage(), request, ex.getFieldErrors());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        String message = "Parameter '%s' has an invalid value.".formatted(ex.getName());
        return build(HttpStatus.BAD_REQUEST, "Bad Request", message, request, Map.of(ex.getName(), message));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        String message = "Required parameter '%s' is missing.".formatted(ex.getParameterName());
        return build(HttpStatus.BAD_REQUEST, "Bad Request", message, request,
                Map.of(ex.getParameterName(), message));
    }

    /** {@code ?sort=} referenced a property that does not exist on the entity. */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiErrorResponse> handleUnknownSortProperty(
            PropertyReferenceException ex, HttpServletRequest request) {

        String message = "'%s' is not a sortable property.".formatted(ex.getPropertyName());
        return build(HttpStatus.BAD_REQUEST, "Bad Request", message, request, Map.of("sort", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        log.debug("Malformed request body on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Bad Request",
                "The request body is missing or malformed.", request, null);
    }

    // ---------------------------------------------------------------- 401 ---

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {

        // Never disclose whether the username or the password was wrong.
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized",
                "Invalid username or password.", request, null);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiErrorResponse> handleDisabled(
            DisabledException ex, HttpServletRequest request) {

        return build(HttpStatus.UNAUTHORIZED, "Unauthorized",
                "This account has been deactivated. Contact your administrator.", request, null);
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ApiErrorResponse> handleLocked(
            LockedException ex, HttpServletRequest request) {

        return build(HttpStatus.UNAUTHORIZED, "Unauthorized",
                "This account is locked. Contact your administrator.", request, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {

        log.debug("Authentication failure on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized", "Authentication failed.", request, null);
    }

    // ---------------------------------------------------------------- 403 ---

    /**
     * A {@code @PreAuthorize} refusal.
     *
     * <p>Handled explicitly rather than left to Spring Security's
     * {@code ExceptionTranslationFilter}: the catch-all handler below would
     * otherwise claim it first and report a permission problem as a server
     * error. An anonymous caller still gets 401 rather than 403, because "you
     * are not signed in" and "you are not allowed" are different problems with
     * different fixes.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {

        if (!SecurityUtils.isAuthenticated()) {
            return build(HttpStatus.UNAUTHORIZED, "Unauthorized", ErrorCode.UNAUTHORIZED,
                    "Authentication is required to access this resource.", request, null);
        }

        log.warn("Access denied for user '{}' on {} {}",
                SecurityUtils.authenticatedName().orElse("unknown"),
                request.getMethod(),
                request.getRequestURI());
        return build(HttpStatus.FORBIDDEN, "Forbidden", ErrorCode.FORBIDDEN,
                "You do not have permission to perform this action.", request, null);
    }

    // ---------------------------------------------------------------- 404 ---

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {

        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getCode(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoHandler(
            NoResourceFoundException ex, HttpServletRequest request) {

        return build(HttpStatus.NOT_FOUND, "Not Found", "The requested endpoint does not exist.", request, null);
    }

    // ---------------------------------------------------------------- 405 ---

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

        return build(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed",
                "%s is not supported by this endpoint.".formatted(ex.getMethod()), request, null);
    }

    // ---------------------------------------------------------------- 409 ---

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicate(
            DuplicateResourceException ex, HttpServletRequest request) {

        Map<String, String> fieldErrors =
                ex.getField() == null ? null : Map.of(ex.getField(), ex.getMessage());
        return build(HttpStatus.CONFLICT, "Conflict", ErrorCode.DUPLICATE_RESOURCE, ex.getMessage(), request,
                fieldErrors);
    }

    /** The data's current state forbids the operation - item already sold, old gold already used. */
    @ExceptionHandler(StateConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleStateConflict(
            StateConflictException ex, HttpServletRequest request) {

        return build(HttpStatus.CONFLICT, "Conflict", ex.getCode(), ex.getMessage(), request, ex.getFieldErrors());
    }

    @ExceptionHandler(ReferencedRecordException.class)
    public ResponseEntity<ApiErrorResponse> handleReferenced(
            ReferencedRecordException ex, HttpServletRequest request) {

        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request, null);
    }

    /**
     * Last line of defence: a uniqueness or foreign key rule the service layer did
     * not pre-check (or lost a race on). The database is the authority, so its
     * verdict is translated rather than suppressed.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        log.warn("Database constraint violated on {}: {}", request.getRequestURI(), rootMessage(ex));

        FieldMessage match = matchConstraint(rootMessage(ex));
        if (match != null) {
            return build(HttpStatus.CONFLICT, "Conflict", match.code(), match.message(), request,
                    Map.of(match.field(), match.message()));
        }
        return build(HttpStatus.CONFLICT, "Conflict",
                "This operation conflicts with existing data.", request, null);
    }

    // ---------------------------------------------------------------- 500 ---

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred. Please try again or contact support.", request, null);
    }

    // ------------------------------------------------------------- helpers ---

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            String error,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors) {

        return build(status, error, null, message, request, fieldErrors);
    }

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            String error,
            ErrorCode code,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors) {

        // The content type is stated rather than negotiated. A download endpoint
        // is called with Accept: <spreadsheet>, and nothing can write this JSON body
        // as a spreadsheet: negotiation fails, the request falls through to /error,
        // and the caller is told 401 - which logs the user out over what was really
        // a validation error. Naming the type skips negotiation entirely.
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorResponse.of(
                        status.value(),
                        error,
                        code == null ? null : code.name(),
                        message,
                        request.getRequestURI(),
                        fieldErrors));
    }

    private static String defaultMessage(FieldError fieldError) {
        return fieldError.getDefaultMessage() == null ? "Invalid value" : fieldError.getDefaultMessage();
    }

    private static String lastNode(String propertyPath) {
        int index = propertyPath.lastIndexOf('.');
        return index < 0 ? propertyPath : propertyPath.substring(index + 1);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() == null ? "" : root.getMessage();
    }

    private static FieldMessage matchConstraint(String message) {
        String lower = message.toLowerCase();
        return CONSTRAINT_MESSAGES.entrySet().stream()
                .filter(entry -> lower.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private record FieldMessage(String field, String message, ErrorCode code) {

        FieldMessage(String field, String message) {
            this(field, message, ErrorCode.DUPLICATE_RESOURCE);
        }
    }
}
