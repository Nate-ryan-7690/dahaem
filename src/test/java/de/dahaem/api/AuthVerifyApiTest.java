package de.dahaem.api;

import de.dahaem.auth.AccountRepository;
import de.dahaem.auth.AccountService;
import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/auth/verify end to end: the PHP front end checks a login with the internal token, no session is started,
 * wrong answers look alike, and repeated failures from one client address are slowed down.
 */
@SpringBootTest(properties = "dahaem.internal-token=test-secret-token")
@AutoConfigureMockMvc
class AuthVerifyApiTest {

    private static final String TOKEN = "test-secret-token";
    private static final String EMAIL = "anna@example.de";
    private static final String PASSWORD = "correct horse battery";

    @Autowired
    private MockMvc mvc;

    @BeforeAll
    static void createDatabaseAndUser() throws IOException {
        TestDatabase.reset();
        new AccountService(new AccountRepository(), new BCryptPasswordEncoder(4))
                .createUser(EMAIL, "Anna", PASSWORD, "Zuhause", LocalDate.of(2026, 9, 25));
    }

    @AfterAll
    static void deleteDatabase() throws IOException {
        TestDatabase.close();
    }

    private static String body(String email, String password) {
        return "{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}";
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder verify(String ip, String email,
                                                                                              String password) {
        return post("/api/auth/verify").header("X-Internal-Token", TOKEN).header("X-Client-Ip", ip)
                .contentType(MediaType.APPLICATION_JSON).content(body(email, password));
    }

    @Test
    void theRightPasswordGivesTheUserAndStartsNoSession() throws Exception {
        MvcResult result = mvc.perform(verify("203.0.113.10", EMAIL, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.displayName").value("Anna"))
                .andExpect(jsonPath("$.household.name").value("Zuhause"))
                .andExpect(jsonPath("$.household.role").value("ADMIN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        assertNull(result.getRequest().getSession(false), "the PHP front end owns the session: none is started here");
        assertNull(result.getResponse().getHeader("Set-Cookie"));
    }

    @Test
    void aWrongPasswordAndAnUnknownEmailGiveTheSameAnswer() throws Exception {
        String wrong = mvc.perform(verify("203.0.113.20", EMAIL, "not the password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();
        String unknown = mvc.perform(verify("203.0.113.20", "nobody@example.de", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertEquals(wrong, unknown);
    }

    @Test
    void withoutTheInternalTokenItIsRefused() throws Exception {
        mvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON).content(body(EMAIL, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void emailAndPasswordAreRequired() throws Exception {
        mvc.perform(post("/api/auth/verify").header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"" + EMAIL + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void repeatedFailuresFromOneAddressAreSlowedDownAndOtherAddressesAreNot() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(verify("203.0.113.99", EMAIL, "wrong password " + i)).andExpect(status().isUnauthorized());
        }

        MvcResult blocked = mvc.perform(verify("203.0.113.99", EMAIL, PASSWORD))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
                .andReturn();
        assertNotNull(blocked.getResponse().getHeader("Retry-After"));

        mvc.perform(verify("203.0.113.98", EMAIL, PASSWORD)).andExpect(status().isOk());
    }
}
