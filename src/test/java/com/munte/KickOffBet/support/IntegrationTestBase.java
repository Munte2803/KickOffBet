package com.munte.KickOffBet.support;

import com.jayway.jsonpath.JsonPath;
import com.munte.KickOffBet.client.FootballDataClient;
import com.munte.KickOffBet.domain.entity.User;
import com.munte.KickOffBet.services.users.EmailService;
import com.munte.KickOffBet.services.users.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for HTTP-level integration tests: one shared Spring context, a clean database before each test,
 * real JWTs obtained through /api/auth/login, and helpers for JSON / multipart requests.
 */
@IntegrationTest
public abstract class IntegrationTestBase {

    @Autowired protected MockMvc mvc;
    @Autowired protected JsonMapper jsonMapper;
    @Autowired protected TestDataFactory data;

    // Context-level mocks declared on @IntegrationTest (reset after every test by Spring)
    @Autowired protected StorageService storageService;
    @Autowired protected EmailService emailService;
    @Autowired protected FootballDataClient footballDataClient;

    @BeforeEach
    void cleanDatabase() {
        data.cleanDatabase();
    }

    // ------------------------------------------------------------------ auth

    /** Logs in through the real endpoint and returns the access token. */
    protected String login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    protected String login(User user) throws Exception {
        return login(user.getEmail(), TestDataFactory.DEFAULT_PASSWORD);
    }

    /** Creates an ACTIVE user and returns a real JWT for it. */
    protected String userToken(String email) throws Exception {
        return login(data.user(email));
    }

    protected String adminToken() throws Exception {
        return login(data.admin("admin" + TestDataFactory.next() + "@kickoffbet.test"));
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    // ------------------------------------------------------------------ requests

    protected String json(Object body) {
        return jsonMapper.writeValueAsString(body);
    }

    protected <B extends AbstractMockHttpServletRequestBuilder<B>> B auth(B builder, String token) {
        builder.header(HttpHeaders.AUTHORIZATION, bearer(token));
        return builder;
    }

    protected MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder builder, Object body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(json(body));
    }

    /** The UI sends the JSON part as a Blob with type application/json named "data". */
    protected MockMultipartFile jsonPart(String name, Object body) {
        return new MockMultipartFile(name, "", MediaType.APPLICATION_JSON_VALUE,
                json(body).getBytes(StandardCharsets.UTF_8));
    }

    protected static MockMultipartFile filePart(String name, String filename) {
        return new MockMultipartFile(name, filename, MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3, 4});
    }

    protected static MockMultipartHttpServletRequestBuilder multipartPut(String url, Object... vars) {
        return multipart(HttpMethod.PUT, url, vars);
    }

    protected String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    protected <T> T read(ResultActions actions, String path) throws Exception {
        return JsonPath.read(body(actions), path);
    }

    /** Asserts the GlobalExceptionHandler ErrorDto contract: {errorCode, error}. */
    protected static ResultMatcher errorCode(String code) {
        return result -> {
            jsonPath("$.errorCode").value(code).match(result);
            jsonPath("$.error").isString().match(result);
        };
    }
}
