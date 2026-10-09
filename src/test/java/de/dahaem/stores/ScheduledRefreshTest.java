package de.dahaem.stores;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduledRefreshTest {

    private static final RefreshSummary OK = new RefreshSummary("x", List.of(), List.of());

    /** Stand-in so the enabled context never builds the real (network/DB-backed) RefreshService. */
    @Configuration
    static class MockServiceConfig {
        static final RefreshService SERVICE = mock(RefreshService.class);

        @Bean
        RefreshService refreshService() {
            return SERVICE;
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MockServiceConfig.class, RefreshConfig.class);

    @Test
    void notRegisteredByDefault() {
        runner.run(ctx -> {
            assertTrue(ctx.getBeansOfType(ScheduledRefresh.class).isEmpty());
            assertTrue(ctx.getBeansOfType(RefreshConfig.class).isEmpty());
        });
    }

    @Test
    void notRegisteredWhenExplicitlyDisabled() {
        runner.withPropertyValues("dahaem.refresh.enabled=false")
                .run(ctx -> assertTrue(ctx.getBeansOfType(ScheduledRefresh.class).isEmpty()));
    }

    @Test
    void registeredWhenEnabled() {
        runner.withPropertyValues("dahaem.refresh.enabled=true",
                        "dahaem.refresh.weekly-cron=0 0 6 * * MON",
                        "dahaem.refresh.monthly-cron=0 0 5 1 * *",
                        "dahaem.refresh.zone=Europe/Berlin")
                .run(ctx -> {
                    assertEquals(1, ctx.getBeansOfType(ScheduledRefresh.class).size());
                    assertNotNull(ctx.getBean("org.springframework.context.annotation.internalScheduledAnnotationProcessor"));
                });
    }

    @Test
    void methodsDelegateToTheMatchingService() {
        RefreshService service = mock(RefreshService.class);
        when(service.refreshWeekly()).thenReturn(OK);
        when(service.refreshMonthly()).thenReturn(OK);
        ScheduledRefresh scheduled = new ScheduledRefresh(service);

        scheduled.weekly();
        verify(service).refreshWeekly();
        verify(service, never()).refreshMonthly();

        scheduled.monthly();
        verify(service).refreshMonthly();
        verify(service, times(1)).refreshWeekly();
    }

    @Test
    void aThrowingRefreshDoesNotEscapeAndDoesNotBlockTheNextRun() {
        RefreshService service = mock(RefreshService.class);
        when(service.refreshWeekly()).thenThrow(new IllegalStateException("boom")).thenReturn(OK);
        ScheduledRefresh scheduled = new ScheduledRefresh(service);

        scheduled.weekly();   // must not throw
        scheduled.weekly();   // guard was released

        verify(service, times(2)).refreshWeekly();
    }

    @Test
    void overlappingRunIsSkipped() throws Exception {
        RefreshService service = mock(RefreshService.class);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(service.refreshWeekly()).thenAnswer(inv -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return OK;
        });
        ScheduledRefresh scheduled = new ScheduledRefresh(service);

        Thread first = new Thread(scheduled::weekly);
        first.start();
        assertTrue(started.await(5, TimeUnit.SECONDS));

        scheduled.monthly();   // weekly still in progress -> skipped
        scheduled.weekly();    // likewise
        verify(service, never()).refreshMonthly();
        verify(service, times(1)).refreshWeekly();

        release.countDown();
        first.join(5000);
        scheduled.monthly();   // guard released
        verify(service).refreshMonthly();
    }

    @Test
    void scheduleIsDrivenByConfigProperties() throws Exception {
        Scheduled weekly = ScheduledRefresh.class.getMethod("weekly").getAnnotation(Scheduled.class);
        Scheduled monthly = ScheduledRefresh.class.getMethod("monthly").getAnnotation(Scheduled.class);
        assertEquals("${dahaem.refresh.weekly-cron}", weekly.cron());
        assertEquals("${dahaem.refresh.monthly-cron}", monthly.cron());
        assertEquals("${dahaem.refresh.zone}", weekly.zone());
        assertEquals("${dahaem.refresh.zone}", monthly.zone());
    }
}
