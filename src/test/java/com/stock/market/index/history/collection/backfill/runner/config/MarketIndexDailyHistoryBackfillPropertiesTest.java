package com.stock.market.index.history.collection.backfill.runner.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketIndexDailyHistoryBackfillPropertiesTest {
    private static final LocalDate FROM = LocalDate.of(2023, 9, 25);
    private static final LocalDate TO = LocalDate.of(2025, 9, 29);

    @Test
    void bindsDisabledDefaultWithoutRangeFromApplicationYaml() throws Exception {
        var environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application.yml", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);

        var properties = Binder.get(environment).bind(
                "market.index.history.collection.backfill",
                Bindable.of(MarketIndexDailyHistoryBackfillProperties.class)
        ).orElseThrow(() -> new IllegalStateException("Backfill properties were not bound."));

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.benchmarkId()).isNull();
        assertThat(properties.fromDate()).isNull();
        assertThat(properties.toDate()).isNull();
    }

    @Test
    void rejectsMissingOrInvalidRangeWhenEnabled() {
        assertThatThrownBy(() -> new MarketIndexDailyHistoryBackfillProperties(true, " ", FROM, TO))
                .hasMessage("benchmarkId must not be blank.");
        assertThatThrownBy(() -> new MarketIndexDailyHistoryBackfillProperties(true, "KOSPI", null, TO))
                .hasMessage("fromDate must not be null.");
        assertThatThrownBy(() -> new MarketIndexDailyHistoryBackfillProperties(true, "KOSPI", FROM, null))
                .hasMessage("toDate must not be null.");
        assertThatThrownBy(() -> new MarketIndexDailyHistoryBackfillProperties(true, "KOSPI", TO, FROM))
                .hasMessage("fromDate must not be after toDate.");
    }
}
