package com.munte.KickOffBet.domain.dto.api.response;

public record SeedResultDto(
        long usersCreated,
        long ticketsCreated,
        long transactionsCreated,
        long durationMs
) {}
