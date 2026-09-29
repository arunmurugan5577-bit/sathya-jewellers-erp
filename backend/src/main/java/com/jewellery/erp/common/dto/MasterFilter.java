package com.jewellery.erp.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * List parameters shared by the master modules.
 *
 * @param search free text, matched against name, code and description
 * @param active {@code null} means "any status" - the list screen defaults to
 *     showing inactive records too, because hiding them is how a deactivated
 *     master silently becomes invisible to the person trying to reactivate it
 */
@Schema(name = "MasterFilter")
public record MasterFilter(String search, Boolean active) {}
