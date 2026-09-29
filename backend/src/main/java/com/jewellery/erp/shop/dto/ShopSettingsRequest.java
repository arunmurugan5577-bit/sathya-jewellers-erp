package com.jewellery.erp.shop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Shop profile payload.
 *
 * <p>Only the shop name is mandatory: a shop configures the rest over time, and
 * forcing a GSTIN on a business that is not yet registered would block the setup
 * screen entirely. The formats, however, are strict - these values end up on
 * printed invoices and in GST returns, where a malformed GSTIN is rejected by
 * the portal rather than by us.
 */
@Schema(name = "ShopSettingsRequest")
public record ShopSettingsRequest(
        @Schema(example = "Sathya Jewellers")
        @NotBlank(message = "Shop name is required")
        @Size(max = 150, message = "Shop name must not exceed 150 characters")
        String shopName,

        @Size(max = 200, message = "Address line 1 must not exceed 200 characters")
        String addressLine1,

        @Size(max = 200, message = "Address line 2 must not exceed 200 characters")
        String addressLine2,

        @Size(max = 100, message = "City must not exceed 100 characters")
        String city,

        @Size(max = 100, message = "State must not exceed 100 characters")
        String state,

        @Schema(example = "600001")
        @Pattern(regexp = "^$|^[0-9]{6}$", message = "Pincode must be 6 digits")
        String pincode,

        @Pattern(regexp = "^$|^[0-9+][0-9 -]{5,19}$", message = "Enter a valid mobile number")
        String mobileNumber,

        @Pattern(regexp = "^$|^[0-9+][0-9 -]{5,19}$", message = "Enter a valid alternate mobile number")
        String alternateMobileNumber,

        @Email(message = "Enter a valid e-mail address")
        @Size(max = 150, message = "E-mail must not exceed 150 characters")
        String email,

        @Schema(example = "33AAAAA0000A1Z5", description = "15 character GSTIN")
        @Pattern(
                regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$",
                message = "Enter a valid 15 character GSTIN, for example 33AAAAA0000A1Z5")
        String gstin) {}
