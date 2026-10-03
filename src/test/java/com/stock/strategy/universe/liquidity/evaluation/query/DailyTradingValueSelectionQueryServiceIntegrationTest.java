package com.stock.strategy.universe.liquidity.evaluation.query;

import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.TradingVenueScope;
import com.stock.market.price.history.persistence.DailyPriceBarEntity;
import com.stock.market.price.history.persistence.DailyPriceBarRepository;
import com.stock.market.price.history.query.DailyPriceHistoryQueryService;
import com.stock.strategy.universe.liquidity.DailyTradingValueAverage;
import com.stock.strategy.universe.liquidity.DailyTradingValueAverageCalculator;
import com.stock.strategy.universe.liquidity.evaluation.DailyTradingValueSelectionEvaluationService;
import com.stock.strategy.universe.liquidity.evaluation.request.DailyTradingValueSelectionEvaluationRequest;
import com.stock.strategy.universe.liquidity.evaluation.result.DailyTradingValueSelectionEvaluationResult;
import com.stock.strategy.universe.liquidity.evaluation.result.DailyTradingValueSelectionEvaluationStatus;
import com.stock.strategy.universe.liquidity.ranking.DailyTradingValueRankingPolicy;
import com.stock.strategy.universe.liquidity.selection.DailyTradingValueSelectionPolicy;
import com.stock.strategy.universe.liquidity.selection.result.DailyTradingValueSelectionStatus;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

@DataJpaTest
@Import({
        DailyTradingValueSelectionQueryService.class,
        DailyPriceHistoryQueryService.class,
        DailyTradingValueSelectionEvaluationService.class,
        DailyTradingValueAverageCalculator.class,
        DailyTradingValueSelectionPolicy.class,
        DailyTradingValueRankingPolicy.class
})
class DailyTradingValueSelectionQueryServiceIntegrationTest {
    private static final LocalDate FIRST_DATE = LocalDate.of(2026, 9, 21);
    private static final LocalDate SECOND_DATE = LocalDate.of(2026, 9, 22);
    private static final LocalDate SELECTION_DATE = LocalDate.of(2026, 9, 23);
    private static final List<LocalDate> TRADING_DATES = List.of(FIRST_DATE, SECOND_DATE, SELECTION_DATE);
    private static final TradingVenueScope VENUE = TradingVenueScope.INTEGRATED;

    @Autowired
    private DailyPriceBarRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private DailyTradingValueSelectionQueryService service;

    @Autowired
    private DailyTradingValueSelectionEvaluationService evaluationService;

    @Test
    void matchesDirectEvaluationOfSameStoredInputAndNeverChangesRows() {
        DailyPriceHistory samsung = completeHistory("005930", 300L, VENUE);
        DailyPriceHistory hynix = completeHistory("000660", 200L, VENUE);
        DailyPriceHistory naver = completeHistory("035420", 150L, VENUE);
        DailyPriceHistory hyundai = completeHistory("005380", 10L, VENUE);
        save(naver);
        save(hynix);
        save(hyundai);
        save(samsung);
        List<Tuple> before = storedRows();
        DailyTradingValueSelectionEvaluationRequest request = request("035420", "005930", "000660", "005380");
        DailyTradingValueSelectionEvaluationResult expected = evaluationService.evaluate(
                request, List.of(naver, hynix, hyundai, samsung)
        );

        DailyTradingValueSelectionEvaluationResult result = service.evaluate(request);

        assertThat(result).isEqualTo(expected);
        assertThat(result.request()).isSameAs(request);
        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.COMPLETE);
        assertThat(result.selectionResults()).extracting(selection -> selection.average().symbol())
                .containsExactly("005930", "000660", "035420", "005380");
        assertThat(result.selectionResults()).extracting(selection -> selection.status())
                .containsExactly(DailyTradingValueSelectionStatus.SELECTED, DailyTradingValueSelectionStatus.SELECTED,
                        DailyTradingValueSelectionStatus.CANDIDATE_LIMIT,
                        DailyTradingValueSelectionStatus.LIQUIDITY_BELOW_MINIMUM);
        assertThat(service.evaluate(request)).isEqualTo(result);
        assertThat(service.evaluate(request("005380", "000660", "005930", "035420"))).isEqualTo(result);
        assertThat(storedRows()).containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    void preservesTargetWithNoStoredRowsRatherThanUsingOtherStoredSymbols() {
        save(completeHistory("005930", 100L, VENUE));
        save(completeHistory("035420", 900L, VENUE));
        List<Tuple> before = storedRows();

        DailyTradingValueSelectionEvaluationResult result = service.evaluate(request("005930", "000660"));

        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.INCOMPLETE);
        assertThat(result.unverifiedSymbols()).containsExactly("000660");
        assertThat(result.calculatedAverages()).containsExactly(average("005930", 300L));
        assertThat(result.selectionResults()).isEmpty();
        assertThat(storedRows()).containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    void preservesAllTargetsWhenOnlyOutsideWindowRowsExist() {
        save(history("005930", bar(FIRST_DATE.minusDays(1), 9_000L, VENUE),
                bar(SELECTION_DATE.plusDays(1), 9_000L, VENUE)));
        DailyTradingValueSelectionEvaluationRequest request = request("005930", "000660");

        DailyTradingValueSelectionEvaluationResult result = service.evaluate(request);

        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.INCOMPLETE);
        assertThat(result.request()).isSameAs(request);
        assertThat(result.unverifiedSymbols()).containsExactlyElementsOf(request.targetSymbols());
        assertThat(result.calculatedAverages()).isEmpty();
        assertThat(result.selectionResults()).isEmpty();
        assertThat(repository.count()).isEqualTo(2L);
    }

    @Test
    void doesNotFillMissingRequiredDateWithOlderOrFutureStoredBars() {
        save(history("005930", bar(FIRST_DATE.minusDays(1), 9_000L, VENUE),
                bar(FIRST_DATE, 100L, VENUE), bar(SELECTION_DATE, 100L, VENUE),
                bar(SELECTION_DATE.plusDays(1), 9_000L, VENUE)));

        DailyTradingValueSelectionEvaluationResult result = service.evaluate(request("005930"));

        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.INCOMPLETE);
        assertThat(result.unverifiedSymbols()).containsExactly("005930");
        assertThat(result.calculatedAverages()).isEmpty();
        assertThat(result.selectionResults()).isEmpty();
        assertThat(repository.count()).isEqualTo(4L);
    }

    @Test
    void futureOlderAndUnrequestedStoredRowsDoNotChangeCompletedResult() {
        save(completeHistory("005930", 100L, VENUE));
        DailyTradingValueSelectionEvaluationRequest request = request("005930");
        DailyTradingValueSelectionEvaluationResult original = service.evaluate(request);
        save(history("005930", bar(FIRST_DATE.minusDays(1), Long.MAX_VALUE, TradingVenueScope.NXT),
                bar(SELECTION_DATE.plusDays(1), Long.MAX_VALUE, TradingVenueScope.KRX)));
        save(completeHistory("000660", Long.MAX_VALUE, TradingVenueScope.KRX));
        List<Tuple> before = storedRows();

        DailyTradingValueSelectionEvaluationResult result = service.evaluate(request);

        assertThat(result).isEqualTo(original);
        assertThat(result.calculatedAverages()).containsExactly(average("005930", 300L));
        assertThat(storedRows()).containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    void usesExactRequiredDateListRatherThanEveryBarWithinQueryBounds() {
        save(history("005930", bar(FIRST_DATE, 100L, VENUE),
                bar(SECOND_DATE, 9_000L, TradingVenueScope.KRX), bar(SELECTION_DATE, 100L, VENUE)));
        DailyTradingValueSelectionEvaluationRequest request = new DailyTradingValueSelectionEvaluationRequest(
                List.of("005930"), SELECTION_DATE, List.of(FIRST_DATE, SELECTION_DATE), VENUE, 100L, 1
        );

        DailyTradingValueSelectionEvaluationResult result = service.evaluate(request);

        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.COMPLETE);
        assertThat(result.calculatedAverages()).containsExactly(new DailyTradingValueAverage(
                "005930", SELECTION_DATE, List.of(FIRST_DATE, SELECTION_DATE), VENUE, BigInteger.valueOf(200L)
        ));
        assertThat(result.selectionResults().getFirst().status()).isEqualTo(DailyTradingValueSelectionStatus.SELECTED);
    }

    @ParameterizedTest
    @CsvSource({"true, false", "false, true", "true, true"})
    void nullableStoredMetadataRemainsUnverifiedWithoutDefaults(boolean missingValue, boolean missingVenue) {
        save(history("005930", bar(FIRST_DATE, 100L, VENUE),
                bar(SECOND_DATE, missingValue ? null : 100L, missingVenue ? null : VENUE),
                bar(SELECTION_DATE, 100L, VENUE)));
        List<Tuple> before = storedRows();

        DailyTradingValueSelectionEvaluationResult result = service.evaluate(request("005930"));

        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.INCOMPLETE);
        assertThat(result.unverifiedSymbols()).containsExactly("005930");
        assertThat(result.calculatedAverages()).isEmpty();
        assertThat(result.selectionResults()).isEmpty();
        assertThat(storedRows()).containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    void confirmedStoredZeroIsCalculatedAndBelowMinimum() {
        save(completeHistory("005930", 0L, VENUE));

        DailyTradingValueSelectionEvaluationResult result = service.evaluate(request("005930"));

        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.COMPLETE);
        assertThat(result.calculatedAverages()).containsExactly(average("005930", 0L));
        assertThat(result.unverifiedSymbols()).isEmpty();
        assertThat(result.selectionResults().getFirst().status())
                .isEqualTo(DailyTradingValueSelectionStatus.LIQUIDITY_BELOW_MINIMUM);
    }

    @ParameterizedTest
    @EnumSource(value = TradingVenueScope.class, names = {"KRX", "NXT"})
    void rejectsConflictingStoredVenueAfterMissingTargetWithoutChangingRows(TradingVenueScope venue) {
        save(history("005930", bar(SELECTION_DATE, 0L, venue)));
        List<Tuple> before = storedRows();

        assertThatThrownBy(() -> service.evaluate(request("000660", "005930")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tradingVenueScope must match expectedVenueScope. tradingDate=" + SELECTION_DATE);
        assertThat(storedRows()).containsExactlyInAnyOrderElementsOf(before);
    }

    @ParameterizedTest
    @EnumSource(TradingVenueScope.class)
    void usesExplicitVenueAndSelectionCriteriaForStoredData(TradingVenueScope venue) {
        save(completeHistory("005930", 123L, venue));
        DailyTradingValueSelectionEvaluationRequest request = new DailyTradingValueSelectionEvaluationRequest(
                List.of("005930"), SELECTION_DATE, TRADING_DATES, venue, 123L, 1
        );

        DailyTradingValueSelectionEvaluationResult result = service.evaluate(request);

        assertThat(result.request()).isSameAs(request);
        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.COMPLETE);
        assertThat(result.calculatedAverages().getFirst().tradingVenueScope()).isEqualTo(venue);
        assertThat(result.calculatedAverages().getFirst().totalTradingValueKrw()).isEqualTo(BigInteger.valueOf(369L));
        assertThat(result.selectionResults().getFirst().status()).isEqualTo(DailyTradingValueSelectionStatus.SELECTED);
    }

    @Test
    void supportsSingleDateWindowWithoutReadingOtherDates() {
        save(history("005930", bar(SELECTION_DATE.minusDays(1), 9_000L, TradingVenueScope.NXT),
                bar(SELECTION_DATE, 100L, VENUE), bar(SELECTION_DATE.plusDays(1), 9_000L, TradingVenueScope.KRX)));
        DailyTradingValueSelectionEvaluationRequest request = new DailyTradingValueSelectionEvaluationRequest(
                List.of("005930"), SELECTION_DATE, List.of(SELECTION_DATE), VENUE, 100L, 1
        );

        DailyTradingValueSelectionEvaluationResult result = service.evaluate(request);

        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.COMPLETE);
        assertThat(result.calculatedAverages()).containsExactly(new DailyTradingValueAverage(
                "005930", SELECTION_DATE, List.of(SELECTION_DATE), VENUE, BigInteger.valueOf(100L)
        ));
    }

    private void save(DailyPriceHistory history) {
        repository.saveAllAndFlush(history.bars().stream()
                .map(bar -> DailyPriceBarEntity.from(history.symbol(), bar)).toList());
        entityManager.clear();
    }

    private List<Tuple> storedRows() {
        entityManager.clear();
        return repository.findAll().stream()
                .map(row -> tuple(row.getId(), row.getSymbol(), row.toBar())).toList();
    }

    private DailyTradingValueSelectionEvaluationRequest request(String... symbols) {
        return new DailyTradingValueSelectionEvaluationRequest(
                List.of(symbols), SELECTION_DATE, TRADING_DATES, VENUE, 100L, 2
        );
    }

    private DailyPriceHistory completeHistory(String symbol, long value, TradingVenueScope venue) {
        return history(symbol, bar(FIRST_DATE, value, venue), bar(SECOND_DATE, value, venue),
                bar(SELECTION_DATE, value, venue));
    }

    private DailyPriceHistory history(String symbol, DailyPriceBar... bars) {
        return new DailyPriceHistory(symbol, List.of(bars));
    }

    private DailyPriceBar bar(LocalDate date, Long value, TradingVenueScope venue) {
        return new DailyPriceBar(date, 100L, 100L, 100L, 100L, 1L, value, venue);
    }

    private DailyTradingValueAverage average(String symbol, long total) {
        return new DailyTradingValueAverage(symbol, SELECTION_DATE, TRADING_DATES, VENUE, BigInteger.valueOf(total));
    }
}
