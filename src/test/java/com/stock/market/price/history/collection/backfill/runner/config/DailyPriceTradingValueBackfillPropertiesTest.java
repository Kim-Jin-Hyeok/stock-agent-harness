package com.stock.market.price.history.collection.backfill.runner.config;

import com.stock.market.price.history.DailyPriceHistoryRequest;
import com.stock.market.price.history.TradingVenueScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyPriceTradingValueBackfillPropertiesTest {
    private static final LocalDate FROM = LocalDate.of(2026, 9, 21);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);
    private static final TradingVenueScope SCOPE = TradingVenueScope.INTEGRATED;

    @Test
    void bindsDisabledDefaultWithoutImplicitTargetOrLimitsFromApplicationYaml() throws Exception {
        var environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application.yml", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);

        var properties = Binder.get(environment).bind(
                "market.price.history.collection.trading-value-backfill",
                Bindable.of(DailyPriceTradingValueBackfillProperties.class)
        ).orElseThrow(() -> new IllegalStateException("Trading value backfill properties were not bound."));

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.symbol()).isNull();
        assertThat(properties.fromDate()).isNull();
        assertThat(properties.toDate()).isNull();
        assertThat(properties.expectedVenueScope()).isNull();
        assertThat(properties.maxRangeDays()).isNull();
    }

    @Test
    void rejectsRequestCreationWhenDisabled() {
        var properties = new DailyPriceTradingValueBackfillProperties(false, null, null, null, null, null);

        assertThatThrownBy(properties::toRequest).isInstanceOf(IllegalStateException.class)
                .hasMessage("Trading value backfill must be enabled to create a request.");
    }

    @ParameterizedTest
    @EnumSource(TradingVenueScope.class)
    void acceptsExplicitScopeAndInclusiveCalendarRangeAtLimit(TradingVenueScope scope) {
        var properties = new DailyPriceTradingValueBackfillProperties(true, "005930", FROM, TO, scope, 10);

        assertThat(properties.toRequest()).isEqualTo(new DailyPriceHistoryRequest("005930", FROM, TO));
        assertThat(properties.expectedVenueScope()).isEqualTo(scope);
        assertThat(properties.maxRangeDays()).isEqualTo(10);
    }

    @Test
    void acceptsSingleDateWithOneDayLimit() {
        var properties = new DailyPriceTradingValueBackfillProperties(true, "005930", FROM, FROM, SCOPE, 1);

        assertThat(properties.toRequest()).isEqualTo(new DailyPriceHistoryRequest("005930", FROM, FROM));
    }

    @ParameterizedTest
    @ValueSource(strings = {"nullSymbol", "emptySymbol", "blankSymbol", "from", "to", "reversed", "scope", "limit"})
    void rejectsMissingTargetAndInvalidDateRangeWhenEnabled(String field) {
        String symbol = switch (field) {
            case "nullSymbol" -> null;
            case "emptySymbol" -> "";
            case "blankSymbol" -> " ";
            default -> "005930";
        };

        assertThatThrownBy(() -> new DailyPriceTradingValueBackfillProperties(
                true, symbol, field.equals("from") ? null : field.equals("reversed") ? TO : FROM,
                field.equals("to") ? null : field.equals("reversed") ? FROM : TO,
                field.equals("scope") ? null : SCOPE, field.equals("limit") ? null : 10
        )).isInstanceOfAny(IllegalArgumentException.class, NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 1, 9})
    void rejectsNonpositiveLimitOrInclusiveSpanOverLimit(int limit) {
        assertThatThrownBy(() -> new DailyPriceTradingValueBackfillProperties(true, "005930", FROM, TO, SCOPE, limit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(limit <= 0 ? "maxRangeDays must be positive."
                        : "Backfill range must not exceed maxRangeDays (inclusive calendar days).");
    }

    @Test
    void doesNotOverflowWhenCheckingVeryLargeDateSpan() {
        assertThatThrownBy(() -> new DailyPriceTradingValueBackfillProperties(
                true, "005930", LocalDate.MIN, LocalDate.MAX, SCOPE, Integer.MAX_VALUE
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Backfill range must not exceed maxRangeDays (inclusive calendar days).");
    }
}
