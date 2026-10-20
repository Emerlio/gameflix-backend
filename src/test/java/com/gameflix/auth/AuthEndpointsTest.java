package com.gameflix.auth;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for the auth flow, covering both the success and failure path
 * of every endpoint. Runs against in-memory H2 (see src/test/resources).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthEndpointsTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    private String body(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private void register(String email, String password) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(JSON).content(body(email, password)))
                .andExpect(status().isCreated());
    }

    private String login(String email, String password) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login").contentType(JSON).content(body(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void register_succeeds_andNeverReturnsHash() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(JSON).content(body("alice@example.com", "Password123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        register("dupe@example.com", "Password123");
        mvc.perform(post("/api/auth/register").contentType(JSON).content(body("dupe@example.com", "Password123")))
                .andExpect(status().isConflict());
    }

    @Test
    void register_invalidInput_returns400() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(JSON).content(body("not-an-email", "short")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_withValidCredentials_returnsJwt() throws Exception {
        register("bob@example.com", "Password123");
        MvcResult res = mvc.perform(post("/api/auth/login").contentType(JSON).content(body("bob@example.com", "Password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();
        String token = mapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
        // A JWT is three dot-separated Base64URL segments.
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        register("carol@example.com", "Password123");
        mvc.perform(post("/api/auth/login").contentType(JSON).content(body("carol@example.com", "WrongPassword1")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withValidToken_returnsOwnProfile() throws Exception {
        register("dave@example.com", "Password123");
        String token = login("dave@example.com", "Password123");
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("dave@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void me_withNoToken_returns401() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withTamperedToken_returns401() throws Exception {
        register("erin@example.com", "Password123");
        String token = login("erin@example.com", "Password123");
        // Flip the last character(s) of the signature so verification fails.
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("a") ? "bb" : "aa");
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }
}
