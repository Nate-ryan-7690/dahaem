package de.dahaem.api;

import de.dahaem.auth.AccountService;
import de.dahaem.auth.Household;
import de.dahaem.mealplanner.MealPlanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * The nightly job (default 00:01, household time zone): writes the resolved servings onto every meal of a past day
 * that still follows the default (see {@link MealPlanService#freezePastServings}), so history no longer changes when
 * the household number or a recipe's preference does. It also catches up on any night the API was not running.
 * Each household is frozen with its own head count. The scheduled method never throws, so a failure cannot stop later runs.
 */
public class ScheduledMealServings {

    private static final Logger log = LoggerFactory.getLogger(ScheduledMealServings.class);

    private final MealPlanService mealPlanService;
    private final AccountService accountService;

    public ScheduledMealServings(MealPlanService mealPlanService, AccountService accountService) {
        this.mealPlanService = mealPlanService;
        this.accountService = accountService;
    }

    @Scheduled(cron = "${dahaem.mealplan.freeze-cron:0 1 0 * * *}", zone = "${dahaem.mealplan.zone:Europe/Berlin}")
    public void freeze() {
        try {
            int frozen = run();
            log.info("meal servings freeze: {} meal(s) of past days frozen", frozen);
        } catch (Throwable t) {
            log.error("meal servings freeze: aborted unexpectedly", t);
        }
    }

    /**
     * One run; public so an operator or a test can call it directly. Every household is frozen with its own head count.
     * Returns how many meals were frozen.
     */
    public int run() {
        int frozen = 0;
        for (Household household : accountService.allHouseholds()) {
            frozen += mealPlanService.forHousehold(household.getId()).freezePastServings(household.getPeople());
        }
        return frozen;
    }
}
