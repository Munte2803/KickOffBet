package com.munte.KickOffBet.domain.dto.api.response;

import com.munte.KickOffBet.domain.enums.UserRole;
import com.munte.KickOffBet.domain.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDto {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private BigDecimal balance;
    private LocalDate birthDate;
    private UserRole role;
    private UserStatus status;
    private boolean emailVerified;
    private boolean idCardVerified;
    private String idCardUrl;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
