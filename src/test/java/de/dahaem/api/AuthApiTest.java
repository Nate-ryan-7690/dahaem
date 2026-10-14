package de.dahaem.api;

import de.dahaem.auth.AccountRepository;
import de.dahaem.auth.AccountService;
import de.dahaem.shared.Database;
import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The login gate, end to end through the real Spring Security filter chain against a throwaway
 * MariaDB database. Unlike ApiSmokeTest nothing is faked here: the user is created with the real
 * AccountService, logs in with a real password, and CSRF tokens are fetched from /api/auth/csrf
 * exactly the way the frontend does it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTest {

    private static final String EMAIL = "anna@example.de";
    private static final String PASSWORD = "correct horse battery";

    private static final List<Path> SCRIPTS = List.of(
            Path.of("schema.sql"),
            Path.of("seed", "inventory_catalog.sql"));

    @Autowired
    private MockMvc mvc;

    @BeforeAll
    static void createDatabaseAndUser() throws IOException, SQLException {
        TestDatabase.reset(SCRIPTS);
        new AccountService(new AccountRepository(), new BCryptPasswordEncoder())
                .createUser(EMAIL, "Anna", PASSWORD, "Zuhause", LocalDate.of(2026, 9, 25));
    }

    @AfterAll
    static void deleteDatabase() throws IOException {
        TestDatabase.close();
    }

    @Test
    void apiRequiresLoginAndAnswers401AsJson() throws Exception {
        mvc.perform(get("/api/inventory"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Login required"));
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/recipes/1"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/suggestions/by-savings"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/suggestions/by-expiring"))
                .andExpect(status().isUnauthorized());
        // A valid CSRF token alone does not get a write through.
        mvc.perform(post("/api/meal-plans").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weekStartDate\": \"2031-01-06\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staticShellStaysPublicSoTheLoginScreenCanLoad() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk());
        mvc.perform(get("/app.js")).andExpect(status().isOk());
        mvc.perform(get("/manifest.webmanifest")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    void loginGivesASessionThatReachesTheApiAndLogoutEndsIt() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/api/auth/login").session(session)
                        .header("X-CSRF-TOKEN", csrfToken(session))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "Anna@Example.de")
                        .param("password", PASSWORD))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/inventory").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThan(0)));

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.displayName").value("Anna"))
                .andExpect(jsonPath("$.household.name").value("Zuhause"))
                .andExpect(jsonPath("$.household.role").value("ADMIN"))
                .andExpect(content().string(not(containsString("$2"))));   // no password hash leaks

        mvc.perform(post("/api/auth/logout").session(session)
                        .header("X-CSRF-TOKEN", csrfToken(session)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRotatesTheCsrfTokenSoThePreLoginTokenNoLongerWorks() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String before = csrfToken(session);
        mvc.perform(post("/api/auth/login").session(session)
                        .header("X-CSRF-TOKEN", before)
                        .param("email", EMAIL)
                        .param("password", PASSWORD))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN", before))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordOrUnknownEmailIsRejected() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/auth/login").session(session)
                        .header("X-CSRF-TOKEN", csrfToken(session))
                        .param("email", EMAIL)
                        .param("password", "wrong password!"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Wrong email or password"));
        mvc.perform(post("/api/auth/login").session(session)
                        .header("X-CSRF-TOKEN", csrfToken(session))
                        .param("email", "nobody@example.de")
                        .param("password", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Wrong email or password"));

        mvc.perform(get("/api/inventory").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void csrfTokenIsRequiredForLoginAndForWrites() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .param("email", EMAIL)
                        .param("password", PASSWORD))
                .andExpect(status().isForbidden());

        MockHttpSession session = login();
        mvc.perform(post("/api/meal-plans").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weekStartDate\": \"2031-01-06\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(post("/api/meal-plans").session(session)
                        .header("X-CSRF-TOKEN", csrfToken(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weekStartDate\": \"2031-01-06\"}"))
                .andExpect(status().isCreated());
    }

    private MockHttpSession login() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/auth/login").session(session)
                        .header("X-CSRF-TOKEN", csrfToken(session))
                        .param("email", EMAIL)
                        .param("password", PASSWORD))
                .andExpect(status().isNoContent());
        return session;
    }

    private String csrfToken(MockHttpSession session) throws Exception {
        String body = mvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }
}
