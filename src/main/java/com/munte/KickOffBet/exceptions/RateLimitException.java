package com.munte.KickOffBet.exceptions;

public class RateLimitException extends ExternalApiException {

    public RateLimitException(String message) {
        super(message);
    }
}
