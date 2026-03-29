package com.munte.KickOffBet.domain.dto.api.response;

import com.munte.KickOffBet.domain.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserListDto {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private UserStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
