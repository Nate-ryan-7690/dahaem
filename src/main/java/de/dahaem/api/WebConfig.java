package de.dahaem.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/** Registers the {@link Caller} parameter resolver. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CallerArgumentResolver callerResolver;

    public WebConfig(CallerArgumentResolver callerResolver) {
        this.callerResolver = callerResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(callerResolver);
    }
}
