package com.munte.KickOffBet.domain.dto.api.response;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ErrorDto {
    private String errorCode;
    private String error;
}
