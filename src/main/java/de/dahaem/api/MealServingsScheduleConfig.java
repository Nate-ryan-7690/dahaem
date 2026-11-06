package de.dahaem.api;

import de.dahaem.auth.AccountService;
import de.dahaem.mealplanner.MealPlanService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Registers the nightly servings freeze. On by default (it only writes numbers onto meals of past days, no external
 * site is touched); switch it off with {@code dahaem.mealplan.freeze-enabled=false}. Scheduling itself is switched on
 * here, independent of the store refresh.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "dahaem.mealplan.freeze-enabled", havingValue = "true", matchIfMissing = true)
public class MealServingsScheduleConfig {

    @Bean
    ScheduledMealServings scheduledMealServings(MealPlanService mealPlanService, AccountService accountService) {
        return new ScheduledMealServings(mealPlanService, accountService);
    }
}
