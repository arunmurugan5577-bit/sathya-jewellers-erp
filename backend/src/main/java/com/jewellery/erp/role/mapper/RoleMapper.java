package com.jewellery.erp.role.mapper;

import com.jewellery.erp.role.dto.RoleDto;
import com.jewellery.erp.role.entity.Role;
import org.springframework.stereotype.Component;

@Component
public class RoleMapper {

    public RoleDto toDto(Role role) {
        return new RoleDto(
                role.getId(),
                role.getName(),
                toLabel(role.getName()),
                role.getDescription(),
                role.getPermissions().size());
    }

    /** ROLE_SUB_ADMIN -> "Sub Admin": a name the UI can show without a lookup table. */
    private static String toLabel(String roleName) {
        String withoutPrefix = roleName.startsWith("ROLE_") ? roleName.substring(5) : roleName;
        String[] words = withoutPrefix.toLowerCase().split("_");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return label.toString();
    }
}
