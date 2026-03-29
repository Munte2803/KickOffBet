package com.munte.KickOffBet.mapper;

import com.munte.KickOffBet.domain.dto.api.response.UserDto;
import com.munte.KickOffBet.domain.dto.api.response.UserListDto;
import com.munte.KickOffBet.domain.entity.User;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    UserListDto toListDto(User user);

    UserDto toDto(User user);

}
