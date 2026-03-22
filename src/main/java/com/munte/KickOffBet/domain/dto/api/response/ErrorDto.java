package com.munte.KickOffBet.domain.dto.api.response;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ErrorDto {
    private String errorCode;
    private String error;

    public ErrorDto(String errorCode, String error) {
        this.errorCode = errorCode;
        this.error = error;
    }
}
