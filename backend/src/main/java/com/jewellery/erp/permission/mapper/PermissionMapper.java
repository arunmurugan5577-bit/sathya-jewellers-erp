package com.jewellery.erp.permission.mapper;

import com.jewellery.erp.permission.entity.Permission;
import com.jewellery.erp.permission.dto.PermissionDto;
import org.springframework.stereotype.Component;

@Component
public class PermissionMapper {

    public PermissionDto toDto(Permission permission) {
        return new PermissionDto(
                permission.getId(),
                permission.getCode(),
                permission.getModule(),
                permission.getAction().name(),
                permission.getDescription());
    }
}
