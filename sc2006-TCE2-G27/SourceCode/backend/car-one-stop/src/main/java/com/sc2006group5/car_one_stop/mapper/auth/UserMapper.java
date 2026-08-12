// src/main/java/com/sc2006group5/car_one_stop/mapper/auth/UserMapper.java
package com.sc2006group5.car_one_stop.mapper.auth;

import com.sc2006group5.car_one_stop.dto.admin.UserAdminResponse;
import com.sc2006group5.car_one_stop.dto.auth.AuthResponse;
import com.sc2006group5.car_one_stop.dto.auth.LoginResponse;
import com.sc2006group5.car_one_stop.dto.auth.RegisterResponse;
import com.sc2006group5.car_one_stop.entity.auth.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "token", ignore = true)
    @Mapping(target = "userId", source = "id")
    AuthResponse toAuthResponse(User user);

    @Mapping(target = "token", ignore = true)
    @Mapping(target = "userId", source = "id")
    RegisterResponse toRegisterResponse(User user);

    @Mapping(target = "token", ignore = true)
    @Mapping(target = "userId", source = "id")
    LoginResponse toLoginResponse(User user);

    UserAdminResponse toUserAdminResponse(User user);
}