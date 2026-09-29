package com.jewellery.erp.customer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create / update payload. The customer code is not accepted: it is issued by the
 * server and never changes.
 */
@Schema(name = "CustomerRequest")
public record CustomerRequest(
        @Schema(example = "Kavitha Sivaraj")
        @NotBlank(message = "Customer name is required")
        @Size(max = 150, message = "Name must not exceed 150 characters")
        String fullName,

        @Schema(example = "9791493639")
        @Pattern(regexp = "^$|^[0-9+][0-9 -]{5,19}$", message = "Enter a valid mobile number")
        String mobileNumber,

        @Email(message = "Enter a valid e-mail address")
        @Size(max = 150, message = "E-mail must not exceed 150 characters")
        String email,

        @Size(max = 200, message = "Address line 1 must not exceed 200 characters") String addressLine1,
        @Size(max = 200, message = "Address line 2 must not exceed 200 characters") String addressLine2,
        @Size(max = 100, message = "City must not exceed 100 characters") String city,
        @Size(max = 100, message = "State must not exceed 100 characters") String state,

        @Pattern(regexp = "^$|^[0-9]{6}$", message = "Pincode must be 6 digits")
        String pincode,

        @Pattern(
                regexp = "^$|^[0-9]{2}[A-Za-z]{5}[0-9]{4}[A-Za-z][1-9A-Za-z][Zz][0-9A-Za-z]$",
                message = "Enter a valid 15 character GSTIN")
        String gstin,

        @Pattern(regexp = "^$|^[A-Za-z]{5}[0-9]{4}[A-Za-z]$", message = "Enter a valid 10 character PAN")
        String pan) {}
