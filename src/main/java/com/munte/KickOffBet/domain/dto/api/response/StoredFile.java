package com.munte.KickOffBet.domain.dto.api.response;

public record StoredFile(
        String filename,
        String contentType,
        byte[] bytes
) {
}
