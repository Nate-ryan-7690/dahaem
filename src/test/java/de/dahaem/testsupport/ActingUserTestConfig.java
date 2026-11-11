package de.dahaem.testsupport;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * For the MockMvc tests that run as a mock user (no internal token configured): every request carries the header
 * {@code X-Acting-User} with {@link TestHousehold#actingUserId()}, the way the PHP front end names the logged-in user. A
 * request that sets the header itself keeps its own value. Import it with {@code @Import}.
 */
@TestConfiguration
public class ActingUserTestConfig {

    @Bean
    MockMvcBuilderCustomizer actingUserHeader() {
        return builder -> builder.defaultRequest(get("/").with(request -> {
            int id = TestHousehold.actingUserId();
            if (id > 0 && request.getHeader("X-Acting-User") == null) {
                request.addHeader("X-Acting-User", String.valueOf(id));
            }
            return request;
        }));
    }
}
