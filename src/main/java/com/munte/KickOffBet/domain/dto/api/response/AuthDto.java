package com.munte.KickOffBet.domain.dto.api.response;

import com.munte.KickOffBet.domain.enums.UserRole;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthDto {
    private String token;
    private String email;
    private String firstName;
    private String lastName;
    private UserRole role;
}