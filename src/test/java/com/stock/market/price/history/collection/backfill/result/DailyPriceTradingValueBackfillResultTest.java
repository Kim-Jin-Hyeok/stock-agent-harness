package com.stock.market.price.history.collection.backfill.result;

import com.stock.market.price.history.DailyPriceHistoryRequest;
import com.stock.market.price.history.TradingVenueScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyPriceTradingValueBackfillResultTest {
    private static final LocalDate FROM = LocalDate.of(2026, 9, 21);
    private static final LocalDate TO = FROM.plusDays(4);
    private static final DailyPriceHistoryRequest REQUEST = new DailyPriceHistoryRequest("005930", FROM, TO);
    private static final TradingVenueScope SCOPE = TradingVenueScope.INTEGRATED;

    @Test
    void acceptsNoTargetsAndSuccessfulOrConcurrentFilledCounts() {
        var noTargets = result(DailyPriceTradingValueBackfillStatus.NO_TARGETS, null, 0, 0, 0);
        var backfilled = result(DailyPriceTradingValueBackfillStatus.BACKFILLED, REQUEST, 2, 4, 1);
        var concurrentFilled = result(DailyPriceTradingValueBackfillStatus.ALREADY_FILLED, REQUEST, 2, 4, 0);

        assertThat(noTargets.collectionRange()).isNull();
        assertThat(backfilled.updatedCount()).isEqualTo(1);
        assertThat(concurrentFilled.targetCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"negativeTarget", "negativeFetched", "negativeUpdated", "updatedAboveTarget",
            "targetAboveFetched", "emptyBackfilled", "backfilledWithoutUpdates", "alreadyFilledWithUpdates",
            "noTargetsWithRange", "noTargetsWithCounts", "wrongSymbol", "before", "after"})
    void rejectsInconsistentStatusCountsOrRange(String scenario) {
        assertThatThrownBy(() -> {
            switch (scenario) {
                case "negativeTarget" -> result(DailyPriceTradingValueBackfillStatus.BACKFILLED, REQUEST, -1, 2, 1);
                case "negativeFetched" -> result(DailyPriceTradingValueBackfillStatus.BACKFILLED, REQUEST, 1, -1, 1);
                case "negativeUpdated" -> result(DailyPriceTradingValueBackfillStatus.BACKFILLED, REQUEST, 1, 2, -1);
                case "updatedAboveTarget" -> result(DailyPriceTradingValueBackfillStatus.BACKFILLED, REQUEST, 1, 2, 2);
                case "targetAboveFetched" -> result(DailyPriceTradingValueBackfillStatus.BACKFILLED, REQUEST, 2, 1, 1);
                case "emptyBackfilled" -> result(DailyPriceTradingValueBackfillStatus.BACKFILLED, REQUEST, 0, 0, 0);
                case "backfilledWithoutUpdates" -> result(DailyPriceTradingValueBackfillStatus.BACKFILLED, REQUEST, 2, 2, 0);
                case "alreadyFilledWithUpdates" -> result(DailyPriceTradingValueBackfillStatus.ALREADY_FILLED, REQUEST, 2, 2, 1);
                case "noTargetsWithRange" -> result(DailyPriceTradingValueBackfillStatus.NO_TARGETS, REQUEST, 0, 0, 0);
                case "noTargetsWithCounts" -> result(DailyPriceTradingValueBackfillStatus.NO_TARGETS, null, 1, 1, 0);
                case "wrongSymbol" -> result(DailyPriceTradingValueBackfillStatus.BACKFILLED,
                        new DailyPriceHistoryRequest("000660", FROM, TO), 1, 1, 1);
                case "before" -> result(DailyPriceTradingValueBackfillStatus.BACKFILLED,
                        new DailyPriceHistoryRequest("005930", FROM.minusDays(1), TO), 1, 1, 1);
                case "after" -> result(DailyPriceTradingValueBackfillStatus.BACKFILLED,
                        new DailyPriceHistoryRequest("005930", FROM, TO.plusDays(1)), 1, 1, 1);
                default -> throw new IllegalArgumentException(scenario);
            }
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingRequiredFields() {
        assertThatThrownBy(() -> result(null, null, 0, 0, 0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> result(DailyPriceTradingValueBackfillStatus.BACKFILLED, null, 1, 1, 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DailyPriceTradingValueBackfillResult(
                DailyPriceTradingValueBackfillStatus.NO_TARGETS, null, null, SCOPE, 0, 0, 0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DailyPriceTradingValueBackfillResult(
                DailyPriceTradingValueBackfillStatus.NO_TARGETS, REQUEST, null, null, 0, 0, 0))
                .isInstanceOf(NullPointerException.class);
    }

    private DailyPriceTradingValueBackfillResult result(
            DailyPriceTradingValueBackfillStatus status,
            DailyPriceHistoryRequest range,
            int targets,
            int fetched,
            int updated
    ) {
        return new DailyPriceTradingValueBackfillResult(status, REQUEST, range, SCOPE, targets, fetched, updated);
    }
}
