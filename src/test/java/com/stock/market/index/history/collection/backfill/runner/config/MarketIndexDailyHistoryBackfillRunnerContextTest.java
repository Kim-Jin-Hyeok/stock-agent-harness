package com.stock.market.index.history.collection.backfill.runner.config;

import com.stock.market.index.history.collection.backfill.MarketIndexDailyHistoryBackfillService;
import com.stock.market.index.history.collection.backfill.runner.MarketIndexDailyHistoryBackfillRunner;
import com.stock.market.index.history.collection.policy.MarketIndexDailyHistoryCollectionDatePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MarketIndexDailyHistoryBackfillRunnerContextTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withBean(MarketIndexDailyHistoryCollectionDatePolicy.class,
                    () -> mock(MarketIndexDailyHistoryCollectionDatePolicy.class));

    @Test
    void doesNotRegisterRunnerWhenDisabled() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(MarketIndexDailyHistoryBackfillRunner.class);
        });
    }

    @Test
    void bindsRangeAndRegistersRunnerWhenEnabled() {
        enabledContext().withBean(MarketIndexDailyHistoryBackfillService.class,
                () -> mock(MarketIndexDailyHistoryBackfillService.class)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(MarketIndexDailyHistoryBackfillRunner.class);
            assertThat(context.getBean(MarketIndexDailyHistoryBackfillProperties.class).benchmarkId())
                    .isEqualTo("KOSPI");
        });
    }

    @Test
    void failsInsteadOfSilentlySkippingWhenEnabledWithoutProviderService() {
        enabledContext().run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining(
                    MarketIndexDailyHistoryBackfillService.class.getName()
            );
        });
    }

    @Test
    void failsStartupWhenEnabledWithoutRange() {
        contextRunner.withBean(MarketIndexDailyHistoryBackfillService.class,
                () -> mock(MarketIndexDailyHistoryBackfillService.class))
                .withPropertyValues("market.index.history.collection.backfill.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage("benchmarkId must not be blank.");
                });
    }

    private ApplicationContextRunner enabledContext() {
        return contextRunner.withPropertyValues(
                "market.index.history.collection.backfill.enabled=true",
                "market.index.history.collection.backfill.benchmark-id=KOSPI",
                "market.index.history.collection.backfill.from-date=2023-09-25",
                "market.index.history.collection.backfill.to-date=2025-09-29"
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MarketIndexDailyHistoryBackfillProperties.class)
    @Import(MarketIndexDailyHistoryBackfillRunner.class)
    static class TestConfiguration {
    }
}
