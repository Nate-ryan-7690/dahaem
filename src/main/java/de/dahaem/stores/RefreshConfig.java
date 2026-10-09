package de.dahaem.stores;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Switches scheduling on, but only when {@code dahaem.refresh.enabled=true}: with the default
 * (false) no scheduler infrastructure, RefreshService or ScheduledRefresh bean exists at all.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "dahaem.refresh.enabled", havingValue = "true")
@Import(ScheduledRefresh.class)
public class RefreshConfig {

    @Bean
    @ConditionalOnMissingBean
    RefreshService refreshService() {
        return RefreshService.create();
    }
}
