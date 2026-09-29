package com.jewellery.erp.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Query parameters for the user list.
 *
 * @param search matched against username, full name and e-mail
 * @param active null means "any status"
 */
@Schema(name = "UserFilter")
public record UserFilter(String search, Boolean active, Long roleId) {}
