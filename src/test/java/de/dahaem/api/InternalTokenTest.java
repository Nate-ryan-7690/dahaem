package de.dahaem.api;

import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With INTERNAL_API_TOKEN configured, /api/** is callable only with the matching X-Internal-Token. */
@SpringBootTest(properties = "dahaem.internal-token=test-secret-token")
@AutoConfigureMockMvc
class InternalTokenTest {

    private static final String HEADER = "X-Internal-Token";

    @Autowired
    private MockMvc mvc;

    @BeforeAll
    static void createDatabase() {
        TestDatabase.reset(List.of(Path.of("schema.sql")));
    }

    @AfterAll
    static void closeDatabase() {
        TestDatabase.close();
    }

    @Test
    void missingTokenIsRejected() throws Exception {
        mvc.perform(get("/api/recipes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Missing or invalid internal token"));
    }

    @Test
    void wrongTokenIsRejected() throws Exception {
        mvc.perform(get("/api/recipes").header(HEADER, "nope"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/recipes").header(HEADER, "test-secret-token-extra"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void correctTokenIsAcceptedWithoutSessionOrCsrf() throws Exception {
        mvc.perform(get("/api/recipes").header(HEADER, "test-secret-token"))
                .andExpect(status().isOk());
        // A state-changing call needs no CSRF token either; the 400 comes from validation, not security.
        mvc.perform(post("/api/meal-plans").header(HEADER, "test-secret-token")
                        .contentType("application/json").content("{}"))
                .andExpect(status().is4xxClientError())
                .andExpect(result -> {
                    int code = result.getResponse().getStatus();
                    if (code == 401 || code == 403) {
                        throw new AssertionError("security rejected a request carrying the valid token: " + code);
                    }
                });
    }

    @Test
    void nonApiPathsAreNotGated() throws Exception {
        mvc.perform(get("/")).andExpect(status().is(org.hamcrest.Matchers.not(401)));
    }
}
