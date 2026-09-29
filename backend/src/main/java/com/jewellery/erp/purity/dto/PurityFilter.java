package com.jewellery.erp.purity.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** List parameters for purities - the master filter plus the owning item type. */
@Schema(name = "PurityFilter")
public record PurityFilter(String search, Boolean active, Long itemTypeId) {}
