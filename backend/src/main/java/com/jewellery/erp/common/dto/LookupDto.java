package com.jewellery.erp.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Minimal projection used by every dropdown in the UI.
 *
 * <p>Lookup endpoints return active records only, so inactive master data can
 * never be selected - the rule is enforced by the query, not by the caller.
 */
@Schema(name = "Lookup", description = "Dropdown option")
public record LookupDto(Long id, String name, String code) {}
