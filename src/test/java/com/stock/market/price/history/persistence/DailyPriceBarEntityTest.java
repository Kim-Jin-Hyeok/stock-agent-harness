package com.stock.market.price.history.persistence;

import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.TradingVenueScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyPriceBarEntityTest {
    private static final LocalDate DATE = LocalDate.of(2026, 9, 21);
    private static final TradingVenueScope SCOPE = TradingVenueScope.INTEGRATED;

    @Test
    void fillsMissingMetadataWithoutChangingOhlcv() {
        var entity = entity(null, null);
        var expected = bar(500L, SCOPE);

        assertThat(entity.fillMissingTradingValueMetadata(expected)).isTrue();
        assertThat(entity.toBar()).isEqualTo(expected);
        assertThat(entity.hasMissingTradingValueMetadata()).isFalse();
        assertThat(entity.fillMissingTradingValueMetadata(expected)).isFalse();
    }

    @Test
    void fillsOnlyUnknownScopeAndPreservesKnownZero() {
        var entity = entity(0L, null);

        assertThat(entity.fillMissingTradingValueMetadata(bar(0L, SCOPE))).isTrue();
        assertThat(entity.toBar()).isEqualTo(bar(0L, SCOPE));
    }

    @Test
    void fillsOnlyUnknownValueAndPreservesKnownScope() {
        var entity = entity(null, SCOPE);

        assertThat(entity.fillMissingTradingValueMetadata(bar(500L, SCOPE))).isTrue();
        assertThat(entity.toBar()).isEqualTo(bar(500L, SCOPE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"value", "scope", "missingValue", "missingScope", "date", "ohlcv"})
    void rejectsInvalidBackfillWithoutAnyFieldMutation(String scenario) {
        var entity = entity(0L, null);
        var original = entity.toBar();
        DailyPriceBar supplied = switch (scenario) {
            case "value" -> bar(1L, SCOPE);
            case "scope" -> {
                entity = entity(null, TradingVenueScope.KRX);
                original = entity.toBar();
                yield bar(0L, SCOPE);
            }
            case "missingValue" -> bar(null, SCOPE);
            case "missingScope" -> bar(0L, null);
            case "date" -> new DailyPriceBar(DATE.plusDays(1), 100, 120, 80, 110, 1000, 0L, SCOPE);
            case "ohlcv" -> new DailyPriceBar(DATE, 100, 120, 80, 111, 1000, 0L, SCOPE);
            default -> throw new IllegalArgumentException(scenario);
        };
        DailyPriceBarEntity checked = entity;

        assertThatThrownBy(() -> checked.fillMissingTradingValueMetadata(supplied)).isInstanceOf(IllegalStateException.class);
        assertThat(checked.toBar()).isEqualTo(original);
    }

    @Test
    void rejectsNullBackfill() {
        var entity = entity(null, null);

        assertThatThrownBy(() -> entity.fillMissingTradingValueMetadata(null)).isInstanceOf(NullPointerException.class);
        assertThat(entity.toBar()).isEqualTo(bar(null, null));
    }

    private DailyPriceBarEntity entity(Long value, TradingVenueScope scope) {
        return DailyPriceBarEntity.from("005930", bar(value, scope));
    }

    private DailyPriceBar bar(Long value, TradingVenueScope scope) {
        return new DailyPriceBar(DATE, 100, 120, 80, 110, 1000, value, scope);
    }
}
