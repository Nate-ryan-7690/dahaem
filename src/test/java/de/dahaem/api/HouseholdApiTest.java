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
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The household head count through the real Spring Security chain against a throwaway MariaDB, and the 401 of an
 * unauthenticated call. (Before the PHP front end owned the login this was part of the session-login test.)
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class HouseholdApiTest {

    private static final List<Path> SCRIPTS = List.of(Path.of("schema.sql"), Path.of("seed", "inventory_catalog.sql"));

    @Autowired
    private MockMvc mvc;

    @BeforeAll
    static void createDatabaseAndUser() throws IOException {
        TestDatabase.reset(SCRIPTS);
        new AccountService(new AccountRepository(), new BCryptPasswordEncoder(4))
                .createUser("anna@example.de", "Anna", "correct horse battery", "Zuhause", LocalDate.of(2026, 9, 25));
    }

    @AfterAll
    static void deleteDatabase() throws IOException {
        TestDatabase.close();
    }

    @Test
    @WithAnonymousUser
    void withoutCredentialsEveryApiCallAnswers401AsJson() throws Exception {
        mvc.perform(get("/api/inventory"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
        mvc.perform(get("/api/recipes/1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/meal-plans").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weekStartDate\": \"2031-01-06\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void householdHeadCountCanBeReadAndSet() throws Exception {
        int householdId = new AccountRepository().findFirstHousehold().orElseThrow().getId();
        String path = "/api/households/" + householdId;

        mvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(householdId))
                .andExpect(jsonPath("$.name").value("Zuhause"))
                .andExpect(jsonPath("$.people").doesNotExist());

        mvc.perform(put(path + "/people")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"people\": 4}"))
                .andExpect(status().isForbidden());   // a request without the token needs a CSRF token, like every write

        mvc.perform(put(path + "/people").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"people\": 4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.people").value(4));
        mvc.perform(get(path))
                .andExpect(jsonPath("$.people").value(4));
    }

    @Test
    void householdHeadCountRejectsBadInputAndUnknownHouseholds() throws Exception {
        int householdId = new AccountRepository().findFirstHousehold().orElseThrow().getId();

        for (String body : new String[] {"{\"people\": 0}", "{\"people\": -2}", "{}", "{\"people\": null}",
                "{\"people\": \"many\"}"}) {
            mvc.perform(put("/api/households/" + householdId + "/people").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(put("/api/households/" + (householdId + 99) + "/people").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"people\": 3}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/households/" + (householdId + 99)))
                .andExpect(status().isNotFound());
    }
}
