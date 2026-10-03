package com.stock.market.price.history.collection.backfill.runner.config;

import com.stock.broker.kis.config.KisProperties;
import com.stock.market.price.history.TradingVenueScope;
import com.stock.market.price.history.collection.backfill.DailyPriceTradingValueBackfillService;
import com.stock.market.price.history.collection.backfill.runner.DailyPriceTradingValueBackfillRunner;
import com.stock.market.price.history.collection.policy.DailyPriceCollectionDatePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class DailyPriceTradingValueBackfillRunnerContextTest {
    private static final String PREFIX = "market.price.history.collection.trading-value-backfill.";
    private final DailyPriceTradingValueBackfillService service = mock(DailyPriceTradingValueBackfillService.class);
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withBean(DailyPriceCollectionDatePolicy.class, () -> mock(DailyPriceCollectionDatePolicy.class))
            .withBean(KisProperties.class, () -> mock(KisProperties.class));

    @Test
    void doesNotRegisterRunnerWhenFlagIsAbsent() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(DailyPriceTradingValueBackfillRunner.class);
            assertThat(context.getBean(DailyPriceTradingValueBackfillProperties.class).enabled()).isFalse();
        });
    }

    @Test
    void doesNotRegisterRunnerOrRequireServiceWhenDisabled() {
        contextRunner.withPropertyValues(PREFIX + "enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(DailyPriceTradingValueBackfillRunner.class);
            assertThat(context).doesNotHaveBean(DailyPriceTradingValueBackfillService.class);
        });
    }

    @Test
    void bindsExplicitTargetAndRegistersRunnerWhenEnabled() {
        enabledContext().withBean(DailyPriceTradingValueBackfillService.class, () -> service).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DailyPriceTradingValueBackfillRunner.class);
            var properties = context.getBean(DailyPriceTradingValueBackfillProperties.class);
            assertThat(properties.symbol()).isEqualTo("005930");
            assertThat(properties.fromDate()).isEqualTo(LocalDate.of(2026, 9, 21));
            assertThat(properties.toDate()).isEqualTo(LocalDate.of(2026, 9, 30));
            assertThat(properties.expectedVenueScope()).isEqualTo(TradingVenueScope.INTEGRATED);
            assertThat(properties.maxRangeDays()).isEqualTo(10);
            verifyNoInteractions(service);
        });
    }

    @Test
    void failsStartupWhenEnabledWithoutBackfillService() {
        enabledContext().run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining(DailyPriceTradingValueBackfillService.class.getName());
        });
    }

    @Test
    void failsBindingWhenEnabledWithoutExplicitTarget() {
        contextRunner.withBean(DailyPriceTradingValueBackfillService.class, () -> service)
                .withPropertyValues(PREFIX + "enabled=true").run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage("symbol must not be blank.");
                    verifyNoInteractions(service);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"from-date=", "to-date=", "expected-venue-scope=", "max-range-days=", "max-range-days=0",
            "max-range-days=9", "from-date=2026-10-01", "expected-venue-scope=UNKNOWN"})
    void failsStartupBeforeCollectionWhenRequiredSettingIsInvalid(String invalid) {
        enabledContext().withBean(DailyPriceTradingValueBackfillService.class, () -> service)
                .withPropertyValues(PREFIX + invalid).run(context -> {
                    assertThat(context).hasFailed();
                    verifyNoInteractions(service);
                });
    }

    private ApplicationContextRunner enabledContext() {
        return contextRunner.withPropertyValues(
                PREFIX + "enabled=true",
                PREFIX + "symbol=005930",
                PREFIX + "from-date=2026-09-21",
                PREFIX + "to-date=2026-09-30",
                PREFIX + "expected-venue-scope=INTEGRATED",
                PREFIX + "max-range-days=10"
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DailyPriceTradingValueBackfillProperties.class)
    @Import(DailyPriceTradingValueBackfillRunner.class)
    static class TestConfiguration {
    }
}
