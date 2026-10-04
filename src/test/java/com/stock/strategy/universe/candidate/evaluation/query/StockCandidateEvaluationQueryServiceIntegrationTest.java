package com.stock.strategy.universe.candidate.evaluation.query;

import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.TradingVenueScope;
import com.stock.market.price.history.persistence.DailyPriceBarEntity;
import com.stock.market.price.history.persistence.DailyPriceBarRepository;
import com.stock.market.price.history.query.DailyPriceHistoryQueryService;
import com.stock.strategy.universe.candidate.evaluation.StockCandidateEvaluationService;
import com.stock.strategy.universe.candidate.evaluation.request.StockCandidateEvaluationRequest;
import com.stock.strategy.universe.candidate.evaluation.result.StockCandidateEvaluationResult;
import com.stock.strategy.universe.candidate.evaluation.result.StockCandidateEvaluationStatus;
import com.stock.strategy.universe.candidate.evaluation.snapshot.persistence.StockCandidateEvaluationSnapshotRepository;
import com.stock.strategy.universe.eligibility.StockEligibilityPolicy;
import com.stock.strategy.universe.eligibility.input.StockEligibilityEvidenceStatus;
import com.stock.strategy.universe.eligibility.input.StockEligibilityInput;
import com.stock.strategy.universe.eligibility.input.StockListingStatus;
import com.stock.strategy.universe.eligibility.input.StockMarket;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import com.stock.strategy.universe.eligibility.request.StockEligibilityRequest;
import com.stock.strategy.universe.eligibility.result.StockEligibilityStatus;
import com.stock.strategy.universe.liquidity.DailyTradingValueAverageCalculator;
import com.stock.strategy.universe.liquidity.evaluation.DailyTradingValueSelectionEvaluationService;
import com.stock.strategy.universe.liquidity.evaluation.request.DailyTradingValueSelectionEvaluationRequest;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.persistence.DailyTradingValueSelectionSnapshotRepository;
import com.stock.strategy.universe.liquidity.ranking.DailyTradingValueRankingPolicy;
import com.stock.strategy.universe.liquidity.selection.DailyTradingValueSelectionPolicy;
import com.stock.strategy.universe.liquidity.selection.result.DailyTradingValueSelectionStatus;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.CUTOFF;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.DATE;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.DATES;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.eligible;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.history;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.input;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

@DataJpaTest
@Import({
        StockCandidateEvaluationQueryService.class,
        StockCandidateEvaluationService.class,
        StockEligibilityPolicy.class,
        DailyPriceHistoryQueryService.class,
        DailyTradingValueSelectionEvaluationService.class,
        DailyTradingValueAverageCalculator.class,
        DailyTradingValueSelectionPolicy.class,
        DailyTradingValueRankingPolicy.class
})
class StockCandidateEvaluationQueryServiceIntegrationTest {
    @Autowired
    private DailyPriceBarRepository repository;
    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private StockCandidateEvaluationQueryService queryService;
    @Autowired
    private StockCandidateEvaluationService evaluationService;
    @Autowired
    private StockCandidateEvaluationSnapshotRepository candidateSnapshotRepository;
    @Autowired
    private DailyTradingValueSelectionSnapshotRepository liquiditySnapshotRepository;

    @Test
    void matchesDirectEvaluationOfStoredInputsAndDoesNotWriteRowsOrSnapshots() {
        DailyPriceHistory samsung = history("005930", 300L);
        DailyPriceHistory hynix = history("000660", 100L);
        DailyPriceHistory etf = history("069500", 9_000L);
        save(etf);
        save(samsung);
        save(hynix);
        save(history("035420", 99_000L));
        List<Tuple> before = storedRows();
        StockCandidateEvaluationRequest request = request(100L, 1, "005930", "069500", "000660");
        List<StockEligibilityInput> inputs = List.of(etf("069500"), eligible("005930"), eligible("000660"));
        StockCandidateEvaluationResult expected = evaluationService.evaluate(request, inputs,
                List.of(samsung, etf, hynix));

        StockCandidateEvaluationResult result = queryService.evaluate(request, inputs);

        assertThat(result).isEqualTo(expected);
        assertThat(result.candidateSymbols()).containsExactly("005930");
        assertThat(result.request()).isSameAs(request);
        assertThat(result.inputHistories()).containsExactly(hynix, samsung, etf);
        assertThat(queryService.evaluate(request, inputs.reversed())).isEqualTo(result);
        assertThat(storedRows()).containsExactlyInAnyOrderElementsOf(before);
        assertThat(candidateSnapshotRepository.count()).isZero();
        assertThat(liquiditySnapshotRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "current", "future"})
    void retainsUnverifiedEligibilityDespiteCompleteStoredPrices(String kind) {
        DailyPriceHistory samsung = history("005930", 300L);
        DailyPriceHistory hynix = history("000660", 100L);
        save(samsung);
        save(hynix);
        List<Tuple> before = storedRows();
        List<StockEligibilityInput> inputs = switch (kind) {
            case "missing" -> List.of(eligible("005930"));
            case "current" -> List.of(eligible("005930"), input("000660", StockSecurityType.COMMON_STOCK,
                    StockEligibilityEvidenceStatus.CURRENT_ONLY));
            case "future" -> List.of(eligible("005930"), new StockEligibilityInput("000660", DATE,
                    StockMarket.KOSPI, StockSecurityType.COMMON_STOCK, StockListingStatus.LISTED,
                    "synthetic-source-01", CUTOFF.plusNanos(1), StockEligibilityEvidenceStatus.AS_OF_VERIFIED));
            default -> throw new IllegalArgumentException("Unknown test case: " + kind);
        };

        StockCandidateEvaluationResult result = queryService.evaluate(request("005930", "000660"), inputs);

        assertThat(result.status()).isEqualTo(StockCandidateEvaluationStatus.INCOMPLETE);
        assertThat(result.unverifiedSymbols()).containsExactly("000660");
        assertThat(result.candidateSymbols()).isEmpty();
        assertThat(result.liquidityResult()).isNull();
        assertThat(result.eligibilityResults()).hasSize(2);
        assertThat(result.inputHistories()).containsExactly(hynix, samsung);
        assertThat(storedRows()).containsExactlyInAnyOrderElementsOf(before);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void distinguishesMissingEligiblePricesFromExcludedTargetWithNoPrices(boolean excluded) {
        DailyPriceHistory samsung = history("005930", 100L);
        save(samsung);
        List<Tuple> before = storedRows();
        List<StockEligibilityInput> inputs = List.of(eligible("005930"),
                excluded ? etf("069500") : eligible("069500"));

        StockCandidateEvaluationResult result = queryService.evaluate(request("005930", "069500"), inputs);

        assertThat(result.inputHistories()).containsExactly(samsung, new DailyPriceHistory("069500", List.of()));
        assertThat(result.status()).isEqualTo(excluded
                ? StockCandidateEvaluationStatus.COMPLETE : StockCandidateEvaluationStatus.INCOMPLETE);
        assertThat(result.candidateSymbols()).isEqualTo(excluded ? List.of("005930") : List.of());
        assertThat(result.unverifiedSymbols()).isEqualTo(excluded ? List.of() : List.of("069500"));
        assertThat(storedRows()).containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    void preservesEveryTargetWhenAllEligibilityAndPricesAreMissing() {
        StockCandidateEvaluationRequest request = request("005930", "000660");

        StockCandidateEvaluationResult result = queryService.evaluate(request, List.of());

        assertThat(result.status()).isEqualTo(StockCandidateEvaluationStatus.INCOMPLETE);
        assertThat(result.unverifiedSymbols()).containsExactlyElementsOf(request.targetSymbols());
        assertThat(result.inputHistories()).containsExactly(new DailyPriceHistory("000660", List.of()),
                new DailyPriceHistory("005930", List.of()));
        assertThat(result.eligibilityResults()).hasSize(2);
        assertThat(result.candidateSymbols()).isEmpty();
        assertThat(result.liquidityResult()).isNull();
        assertThat(repository.count()).isZero();
    }

    @Test
    void returnsCompleteEmptyResultWhenAllTargetsAreConfirmedIneligible() {
        DailyPriceHistory incompatible = history("069500", null, TradingVenueScope.KRX);
        save(incompatible);
        List<Tuple> before = storedRows();

        StockCandidateEvaluationResult result = queryService.evaluate(request("069500", "114800"),
                List.of(etf("069500"), etf("114800")));

        assertThat(result.status()).isEqualTo(StockCandidateEvaluationStatus.COMPLETE);
        assertThat(result.candidateSymbols()).isEmpty();
        assertThat(result.unverifiedSymbols()).isEmpty();
        assertThat(result.liquidityResult()).isNull();
        assertThat(result.eligibilityResults()).extracting(value -> value.status())
                .containsOnly(StockEligibilityStatus.INELIGIBLE);
        assertThat(result.inputHistories()).containsExactly(incompatible, new DailyPriceHistory("114800", List.of()));
        assertThat(storedRows()).containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    void excludesOlderFutureAndUnrequestedRowsFromInputNotJustFromCalculatedAverage() {
        DailyPriceHistory originalHistory = history("005930", 100L);
        save(originalHistory);
        StockCandidateEvaluationRequest request = request("005930");
        List<StockEligibilityInput> inputs = List.of(eligible("005930"));
        StockCandidateEvaluationResult original = queryService.evaluate(request, inputs);
        save(new DailyPriceHistory("005930", List.of(bar(DATES.getFirst().minusDays(1), Long.MAX_VALUE,
                TradingVenueScope.KRX), bar(DATE.plusDays(1), Long.MAX_VALUE, TradingVenueScope.NXT))));
        save(history("000660", Long.MAX_VALUE, TradingVenueScope.KRX));
        List<Tuple> before = storedRows();

        StockCandidateEvaluationResult result = queryService.evaluate(request, inputs);

        assertThat(result).isEqualTo(original);
        assertThat(result.inputHistories()).containsExactly(originalHistory);
        assertThat(result.liquidityResult().calculatedAverages().getFirst().totalTradingValueKrw())
                .isEqualTo(BigInteger.valueOf(300L));
        assertThat(storedRows()).containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    void doesNotReplaceMissingRequiredDateWithOlderOrFutureStoredBar() {
        save(new DailyPriceHistory("005930", List.of(bar(DATES.getFirst().minusDays(1), 9_000L,
                TradingVenueScope.INTEGRATED), bar(DATES.getFirst(), 100L, TradingVenueScope.INTEGRATED),
                bar(DATE, 100L, TradingVenueScope.INTEGRATED), bar(DATE.plusDays(1), 9_000L,
                TradingVenueScope.INTEGRATED))));
        List<Tuple> before = storedRows();

        StockCandidateEvaluationResult result = queryService.evaluate(request("005930"), List.of(eligible("005930")));

        assertThat(result.status()).isEqualTo(StockCandidateEvaluationStatus.INCOMPLETE);
        assertThat(result.unverifiedSymbols()).containsExactly("005930");
        assertThat(result.candidateSymbols()).isEmpty();
        assertThat(result.inputHistories().getFirst().bars()).extracting(DailyPriceBar::tradingDate)
                .containsExactly(DATES.getFirst(), DATE);
        assertThat(storedRows()).containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    void preservesQueryWindowButCalculatesOnlyExplicitRequiredDates() {
        DailyPriceHistory history = new DailyPriceHistory("005930", List.of(
                bar(DATES.getFirst(), 100L, TradingVenueScope.INTEGRATED),
                bar(DATES.get(1), Long.MAX_VALUE, TradingVenueScope.KRX),
                bar(DATE, 100L, TradingVenueScope.INTEGRATED)));
        save(history);
        StockCandidateEvaluationRequest request = new StockCandidateEvaluationRequest(request("005930").eligibilityRequest(),
                new DailyTradingValueSelectionEvaluationRequest(List.of("005930"), DATE,
                        List.of(DATES.getFirst(), DATE), TradingVenueScope.INTEGRATED, 100L, 1));

        StockCandidateEvaluationResult result = queryService.evaluate(request, List.of(eligible("005930")));

        assertThat(result.status()).isEqualTo(StockCandidateEvaluationStatus.COMPLETE);
        assertThat(result.inputHistories()).containsExactly(history);
        assertThat(result.candidateSymbols()).containsExactly("005930");
        assertThat(result.liquidityResult().calculatedAverages().getFirst().totalTradingValueKrw())
                .isEqualTo(BigInteger.valueOf(200L));
        assertThat(result.liquidityResult().calculatedAverages().getFirst().tradingDates())
                .containsExactly(DATES.getFirst(), DATE);
    }

    @ParameterizedTest
    @CsvSource({"true, false", "false, true", "true, true"})
    void preservesNullableMetadataAndReplaysOriginalInputsAfterExplicitBackfill(boolean missingValue, boolean missingVenue) {
        DailyPriceHistory incomplete = history("005930", missingValue ? null : 100L,
                missingVenue ? null : TradingVenueScope.INTEGRATED);
        save(incomplete);
        StockCandidateEvaluationRequest request = request("005930");
        List<StockEligibilityInput> inputs = List.of(eligible("005930"));
        List<Tuple> before = storedRows();

        StockCandidateEvaluationResult original = queryService.evaluate(request, inputs);

        assertThat(original.status()).isEqualTo(StockCandidateEvaluationStatus.INCOMPLETE);
        assertThat(original.inputHistories()).containsExactly(incomplete);
        assertThat(storedRows()).containsExactlyInAnyOrderElementsOf(before);
        List<DailyPriceBarEntity> rows = repository.findAll();
        for (DailyPriceBarEntity row : rows) {
            assertThat(row.fillMissingTradingValueMetadata(bar(row.getTradingDate(), 100L,
                    TradingVenueScope.INTEGRATED))).isTrue();
        }
        repository.saveAllAndFlush(rows);
        entityManager.clear();
        List<Tuple> filledRows = storedRows();

        StockCandidateEvaluationResult fresh = queryService.evaluate(request, inputs);
        StockCandidateEvaluationResult replayed = evaluationService.evaluate(original.request(),
                original.eligibilityResults().stream().map(value -> value.input()).toList(), original.inputHistories());

        assertThat(fresh.status()).isEqualTo(StockCandidateEvaluationStatus.COMPLETE);
        assertThat(fresh.inputHistories()).containsExactly(history("005930", 100L));
        assertThat(replayed).isEqualTo(original);
        assertThat(original.inputHistories()).containsExactly(incomplete);
        assertThat(storedRows()).containsExactlyInAnyOrderElementsOf(filledRows);
        assertThat(candidateSnapshotRepository.count()).isZero();
        assertThat(liquiditySnapshotRepository.count()).isZero();
    }

    @Test
    void preservesConfirmedZeroAsBelowMinimumRatherThanMissingData() {
        save(history("005930", 0L));

        StockCandidateEvaluationResult result = queryService.evaluate(request("005930"), List.of(eligible("005930")));

        assertThat(result.status()).isEqualTo(StockCandidateEvaluationStatus.COMPLETE);
        assertThat(result.unverifiedSymbols()).isEmpty();
        assertThat(result.candidateSymbols()).isEmpty();
        assertThat(result.liquidityResult().selectionResults().getFirst().status())
                .isEqualTo(DailyTradingValueSelectionStatus.LIQUIDITY_BELOW_MINIMUM);
    }

    @ParameterizedTest
    @EnumSource(value = TradingVenueScope.class, names = {"KRX", "NXT"})
    void propagatesKnownVenueConflictAfterEarlierEmptyHistoryWithoutChangingRows(TradingVenueScope venue) {
        save(history("005930", 100L, venue));
        List<Tuple> before = storedRows();

        assertThatThrownBy(() -> queryService.evaluate(request("005930", "000660"),
                List.of(eligible("005930"), eligible("000660"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tradingVenueScope must match expectedVenueScope. tradingDate=" + DATES.getFirst());
        assertThat(storedRows()).containsExactlyInAnyOrderElementsOf(before);
        assertThat(candidateSnapshotRepository.count()).isZero();
        assertThat(liquiditySnapshotRepository.count()).isZero();
    }

    @ParameterizedTest
    @EnumSource(TradingVenueScope.class)
    void usesExplicitSingleDateVenueThresholdAndKosdaqEligibilityInsteadOfDefaults(TradingVenueScope venue) {
        String symbol = "synthetic-kosdaq-01";
        save(new DailyPriceHistory(symbol, List.of(bar(DATE.minusDays(1), 9_000L, venue),
                bar(DATE, 123L, venue), bar(DATE.plusDays(1), 9_000L, venue))));
        StockCandidateEvaluationRequest request = new StockCandidateEvaluationRequest(
                new StockEligibilityRequest(DATE, CUTOFF, Set.of(StockMarket.KOSDAQ), Set.of(StockSecurityType.COMMON_STOCK)),
                new DailyTradingValueSelectionEvaluationRequest(List.of(symbol), DATE, List.of(DATE), venue, 123L, 1));
        StockEligibilityInput input = new StockEligibilityInput(symbol, DATE, StockMarket.KOSDAQ,
                StockSecurityType.COMMON_STOCK, StockListingStatus.LISTED, "synthetic-source-01", CUTOFF,
                StockEligibilityEvidenceStatus.AS_OF_VERIFIED);

        StockCandidateEvaluationResult result = queryService.evaluate(request, List.of(input));

        assertThat(result.request()).isSameAs(request);
        assertThat(result.status()).isEqualTo(StockCandidateEvaluationStatus.COMPLETE);
        assertThat(result.candidateSymbols()).containsExactly(symbol);
        assertThat(result.eligibilityResults().getFirst().input()).isEqualTo(input);
        assertThat(result.inputHistories().getFirst().bars()).containsExactly(bar(DATE, 123L, venue));
        assertThat(result.liquidityResult().calculatedAverages().getFirst().totalTradingValueKrw())
                .isEqualTo(BigInteger.valueOf(123L));
    }

    private void save(DailyPriceHistory history) {
        repository.saveAllAndFlush(history.bars().stream()
                .map(bar -> DailyPriceBarEntity.from(history.symbol(), bar)).toList());
        entityManager.clear();
    }

    private List<Tuple> storedRows() {
        entityManager.clear();
        return repository.findAll().stream().map(row -> tuple(row.getId(), row.getSymbol(), row.toBar())).toList();
    }

    private static StockEligibilityInput etf(String symbol) {
        return input(symbol, StockSecurityType.ETF, StockEligibilityEvidenceStatus.AS_OF_VERIFIED);
    }

    private static DailyPriceBar bar(LocalDate date, Long value, TradingVenueScope venue) {
        return new DailyPriceBar(date, 100L, 110L, 90L, 100L, 10L, value, venue);
    }
}
