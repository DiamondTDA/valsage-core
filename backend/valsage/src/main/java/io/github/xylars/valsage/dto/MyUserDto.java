package io.github.xylars.valsage.dto;

import io.github.xylars.valsage.entity.MyUser;
import io.github.xylars.valsage.enums.Role;

public record MyUserDto(
        String username,
        String email,
        Role role
) {
    public static MyUserDto fromEntity(MyUser user) {
        return new MyUserDto(
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );
    }
}
