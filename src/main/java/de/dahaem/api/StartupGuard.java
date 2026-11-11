package de.dahaem.api;

import de.dahaem.shared.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Fail closed: the API does not start without its secrets. A missing or blank {@code INTERNAL_API_TOKEN} would leave
 * only the session login in front of the data, and missing database settings would only show up as errors on the first
 * request, so both stop the start-up with a message that names the variables (never their values).
 *
 * <p>For local development only, {@code -Ddahaem.require-secrets=false} switches the check off (the tests do this
 * through src/test/resources/config/application.properties). It is never off by default.
 */
@Component
public class StartupGuard {

    private static final Logger log = LoggerFactory.getLogger(StartupGuard.class);
    static final String TOKEN_ENV = "INTERNAL_API_TOKEN";
    static final int RECOMMENDED_TOKEN_LENGTH = 32;

    public StartupGuard(@Value("${dahaem.require-secrets:true}") boolean required,
                        @Value("${dahaem.internal-token:}") String token) {
        check(required, token, System::getenv);
        if (required && token.length() < RECOMMENDED_TOKEN_LENGTH) {
            log.warn("{} is shorter than {} characters: use a long random value", TOKEN_ENV, RECOMMENDED_TOKEN_LENGTH);
        }
    }

    /** Throws if a required secret is missing or blank. {@code env} reads an environment variable (null if unset). */
    static void check(boolean required, String token, UnaryOperator<String> env) {
        if (!required) {
            log.warn("dahaem.require-secrets=false: the start-up check for secrets is OFF (local development only)");
            return;
        }
        List<String> missing = new ArrayList<>();
        if (token == null || token.isBlank()) {
            missing.add(TOKEN_ENV);
        }
        for (String name : List.of(Database.URL_ENV, Database.USER_ENV, Database.PASSWORD_ENV)) {
            String value = env.apply(name);
            if (value == null || value.isBlank()) {
                missing.add(name);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Refusing to start: " + String.join(", ", missing)
                    + " not set or blank. Set them in the environment (never in the repository). "
                    + "Local development only: -Ddahaem.require-secrets=false");
        }
    }
}
