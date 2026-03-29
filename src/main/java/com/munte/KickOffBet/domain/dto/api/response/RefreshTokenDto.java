package com.munte.KickOffBet.domain.dto.api.response;

import com.munte.KickOffBet.domain.enums.UserRole;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class RefreshTokenDto {
    private String token;
    private String email;
    private String firstName;
    private String lastName;
    private UserRole role;
}
