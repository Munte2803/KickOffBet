package com.munte.KickOffBet.controllers;

import com.munte.KickOffBet.exceptions.StorageException;
import com.munte.KickOffBet.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every error leaves the API as an ErrorDto {errorCode, error} with a meaningful status:
 * the UI translates errorCode and otherwise shows the error text.
 */
class GlobalExceptionHandlerIntegrationTest extends IntegrationTestBase {

    @Test
    void resourceNotFound() throws Exception {
        mvc.perform(auth(get("/api/matches/{id}", UUID.randomUUID()), userToken("nf@kickoffbet.test")))
                .andExpect(status().isNotFound())
                .andExpect(errorCode("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.error").value("Match not found"));
    }

    @Test
    void businessErrorFromPageableSortWhitelist() throws Exception {
        mvc.perform(auth(get("/api/teams").param("sort", "password,asc"), userToken("sort@kickoffbet.test")))
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.error").value("Sorting by 'password' is not allowed"));
    }

    @Test
    void storageFailureIsServiceUnavailable() throws Exception {
        when(storageService.uploadFile(any(), anyString())).thenThrow(new StorageException("MinIO down"));
        var body = Map.of("firstName", "Ion", "lastName", "Pop", "email", "s@kickoffbet.test",
                "password", "Secret123", "birthDate", "1990-01-01");
        mvc.perform(multipart("/api/auth/register").file(jsonPart("data", body)).file(filePart("idCard", "a.png")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(errorCode("STORAGE_ERROR"))
                .andExpect(jsonPath("$.error").value("MinIO down"));
    }

    @Test
    void malformedJsonIsABadRequest() throws Exception {
        mvc.perform(auth(post("/api/wallet/deposit"), userToken("json@kickoffbet.test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": "))
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("BAD_REQUEST"))
                .andExpect(jsonPath("$.error").value("Malformed request body"));
    }

    @Test
    void invalidPathVariableTypeIsABadRequest() throws Exception {
        String token = userToken("uuid@kickoffbet.test");
        mvc.perform(auth(get("/api/matches/not-a-uuid"), token))
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("BAD_REQUEST"))
                .andExpect(jsonPath("$.error").value("Invalid value for 'id'"));
        mvc.perform(auth(get("/api/matches/day/NOT_A_STATUS"), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid value for 'matchStatus'"));
    }

    @Test
    void missingRequiredParameterIsABadRequest() throws Exception {
        mvc.perform(auth(get("/api/tickets"), userToken("param@kickoffbet.test")))
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("BAD_REQUEST"));
        mvc.perform(get("/api/auth/confirm-email"))
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("BAD_REQUEST"));
    }

    @Test
    void unsupportedMethodAndMediaType() throws Exception {
        String token = userToken("method@kickoffbet.test");
        mvc.perform(auth(put("/api/wallet/deposit"), token))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(errorCode("METHOD_NOT_ALLOWED"));
        mvc.perform(auth(post("/api/wallet/deposit"), token)
                        .contentType(MediaType.TEXT_PLAIN).content("50"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(errorCode("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void unknownApiPathIsNotFound() throws Exception {
        mvc.perform(auth(get("/api/does-not-exist"), userToken("404@kickoffbet.test")))
                .andExpect(status().isNotFound())
                .andExpect(errorCode("NOT_FOUND"));
    }
}
