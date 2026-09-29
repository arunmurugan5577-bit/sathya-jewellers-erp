package com.jewellery.erp.subcategory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** List parameters for sub categories - the master filter plus the parent. */
@Schema(name = "SubCategoryFilter")
public record SubCategoryFilter(String search, Boolean active, Long categoryId) {}
