package de.dahaem.stores;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Runs the weekly / monthly refresh on a cron schedule inside the long-running API process.
 * Opt-in: only registered when {@code dahaem.refresh.enabled=true}, because the importers
 * scrape external sites. Cron expressions and time zone come from {@code dahaem.refresh.*}.
 *
 * Weekly and monthly share one guard, so they never run at the same time (both write the same
 * tables); a run that finds another in progress is skipped, not queued. Scheduled methods
 * never throw, so a failure cannot stop later runs.
 */
@Component
@ConditionalOnProperty(name = "dahaem.refresh.enabled", havingValue = "true")
public class ScheduledRefresh {

    private static final Logger log = LoggerFactory.getLogger(ScheduledRefresh.class);

    private final RefreshService refreshService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ScheduledRefresh(RefreshService refreshService) {
        this.refreshService = refreshService;
    }

    @Scheduled(cron = "${dahaem.refresh.weekly-cron}", zone = "${dahaem.refresh.zone}")
    public void weekly() {
        run("weekly", refreshService::refreshWeekly);
    }

    @Scheduled(cron = "${dahaem.refresh.monthly-cron}", zone = "${dahaem.refresh.zone}")
    public void monthly() {
        run("monthly", refreshService::refreshMonthly);
    }

    private void run(String flow, Supplier<RefreshSummary> job) {
        if (!running.compareAndSet(false, true)) {
            log.warn("scheduled refresh {}: skipped, another refresh is still in progress", flow);
            return;
        }
        try {
            RefreshSummary summary = job.get();
            if (!summary.allOk()) {
                log.warn("scheduled refresh {}: finished with problems: failures={}", flow, summary.failures());
            }
        } catch (Throwable t) {
            log.error("scheduled refresh {}: aborted unexpectedly", flow, t);
        } finally {
            running.set(false);
        }
    }
}
