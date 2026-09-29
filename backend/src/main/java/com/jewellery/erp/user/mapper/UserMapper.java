package com.jewellery.erp.user.mapper;

import com.jewellery.erp.user.dto.UserDto;
import com.jewellery.erp.user.entity.User;
import java.util.Comparator;
import org.springframework.stereotype.Component;

/**
 * Entity to DTO translation for users.
 *
 * <p>Hand written rather than generated: it is the one place that guarantees the
 * password hash never reaches a response, and that guarantee is worth being able
 * to read.
 */
@Component
public class UserMapper {

    public UserDto toDto(User user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getMobileNumber(),
                user.isActive(),
                user.isAccountLocked(),
                user.isMustChangePassword(),
                user.getLastLoginAt(),
                user.roleNames().stream().sorted(Comparator.naturalOrder()).toList(),
                user.getDirectPermissions().size(),
                user.getCreatedAt(),
                user.getCreatedBy(),
                user.getUpdatedAt(),
                user.getUpdatedBy());
    }
}
