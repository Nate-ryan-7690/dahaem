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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PUT /api/account/password and /email end to end: the PHP front end names the logged-in user in X-Acting-User. */
@SpringBootTest(properties = "dahaem.internal-token=test-secret-token")
@AutoConfigureMockMvc
class AccountApiTest {

    private static final String TOKEN = "test-secret-token";
    private static final String PASSWORD = "correct horse battery";

    @Autowired
    private MockMvc mvc;

    private static int firstUserId;

    @BeforeAll
    static void createDatabaseAndUsers() throws IOException {
        TestDatabase.reset();
        AccountService accounts = new AccountService(new AccountRepository(), new BCryptPasswordEncoder(4));
        firstUserId = accounts.createUser("you@example.com", "Nate", PASSWORD, "Zuhause", LocalDate.of(2026, 9, 26)).user().getId();
        accounts.createUser("anna@example.de", "Anna", "another long password", null, LocalDate.of(2026, 9, 27));
    }

    @AfterAll
    static void deleteDatabase() throws IOException {
        TestDatabase.close();
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String ip) {
        return request.header("X-Internal-Token", TOKEN).header("X-Acting-User", String.valueOf(firstUserId))
                .header("X-Client-Ip", ip).contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void theEmailCanBeChangedAndTheAnswerIsTheUser() throws Exception {
        mvc.perform(as(put("/api/account/email"), "198.51.100.10")
                        .content("{\"password\": \"" + PASSWORD + "\", \"email\": \"Nate.Real@Mail.DE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("nate.real@mail.de"))
                .andExpect(jsonPath("$.displayName").value("Nate"))
                .andExpect(jsonPath("$.household.name").value("Zuhause"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        // the new address is the login name now
        mvc.perform(post("/api/auth/verify").header("X-Internal-Token", TOKEN).header("X-Client-Ip", "198.51.100.11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"nate.real@mail.de\", \"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
        // and back, so the other tests find the user by the same address
        mvc.perform(as(put("/api/account/email"), "198.51.100.10")
                        .content("{\"password\": \"" + PASSWORD + "\", \"email\": \"you@example.com\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void aTakenOrInvalidEmailIsRefused() throws Exception {
        mvc.perform(as(put("/api/account/email"), "198.51.100.20")
                        .content("{\"password\": \"" + PASSWORD + "\", \"email\": \"anna@example.de\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
        mvc.perform(as(put("/api/account/email"), "198.51.100.20")
                        .content("{\"password\": \"" + PASSWORD + "\", \"email\": \"nonsense\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aWrongCurrentPasswordIsRefusedAndSlowedDown() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(as(put("/api/account/password"), "198.51.100.30")
                            .content("{\"currentPassword\": \"wrong password\", \"newPassword\": \"a brand new password\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
        }
        mvc.perform(as(put("/api/account/password"), "198.51.100.30")
                        .content("{\"currentPassword\": \"" + PASSWORD + "\", \"newPassword\": \"a brand new password\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    void thePasswordCanBeChangedAndTheNewOneLogsIn() throws Exception {
        mvc.perform(as(put("/api/account/password"), "198.51.100.40")
                        .content("{\"currentPassword\": \"" + PASSWORD + "\", \"newPassword\": \"my second password\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/verify").header("X-Internal-Token", TOKEN).header("X-Client-Ip", "198.51.100.41")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"you@example.com\", \"password\": \"my second password\"}"))
                .andExpect(status().isOk());
        mvc.perform(as(put("/api/account/password"), "198.51.100.40")
                        .content("{\"currentPassword\": \"my second password\", \"newPassword\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void theActingUserHeaderIsRequiredAndMustBeAKnownUser() throws Exception {
        String body = "{\"currentPassword\": \"" + PASSWORD + "\", \"newPassword\": \"a brand new password\"}";
        mvc.perform(put("/api/account/password").header("X-Internal-Token", TOKEN).header("X-Client-Ip", "198.51.100.50")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/account/password").header("X-Internal-Token", TOKEN).header("X-Acting-User", "abc")
                        .header("X-Client-Ip", "198.51.100.50").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/account/password").header("X-Internal-Token", TOKEN).header("X-Acting-User", "99999")
                        .header("X-Client-Ip", "198.51.100.50").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        // without the token nothing is believed at all
        mvc.perform(put("/api/account/password").header("X-Acting-User", String.valueOf(firstUserId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }
}
