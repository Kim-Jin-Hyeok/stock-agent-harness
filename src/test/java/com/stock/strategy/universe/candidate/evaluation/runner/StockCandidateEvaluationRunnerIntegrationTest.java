package com.stock.strategy.universe.candidate.evaluation.runner;

import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.TradingVenueScope;
import com.stock.market.price.history.persistence.DailyPriceBarEntity;
import com.stock.market.price.history.persistence.DailyPriceBarRepository;
import com.stock.market.price.history.query.DailyPriceHistoryQueryService;
import com.stock.strategy.universe.candidate.evaluation.StockCandidateEvaluationService;
import com.stock.strategy.universe.candidate.evaluation.query.StockCandidateEvaluationQueryService;
import com.stock.strategy.universe.candidate.evaluation.result.StockCandidateEvaluationStatus;
import com.stock.strategy.universe.candidate.evaluation.runner.config.StockCandidateEvaluationProperties;
import com.stock.strategy.universe.candidate.evaluation.snapshot.StockCandidateEvaluationSnapshot;
import com.stock.strategy.universe.candidate.evaluation.snapshot.json.StockCandidateEvaluationSnapshotJsonConverter;
import com.stock.strategy.universe.candidate.evaluation.snapshot.persistence.StockCandidateEvaluationSnapshotRepository;
import com.stock.strategy.universe.candidate.evaluation.snapshot.storage.StockCandidateEvaluationSnapshotStore;
import com.stock.strategy.universe.eligibility.StockEligibilityPolicy;
import com.stock.strategy.universe.liquidity.DailyTradingValueAverageCalculator;
import com.stock.strategy.universe.liquidity.evaluation.DailyTradingValueSelectionEvaluationService;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.DailyTradingValueSelectionSnapshot;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.json.DailyTradingValueSelectionSnapshotJsonConverter;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.persistence.DailyTradingValueSelectionSnapshotRepository;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.storage.DailyTradingValueSelectionSnapshotStore;
import com.stock.strategy.universe.liquidity.ranking.DailyTradingValueRankingPolicy;
import com.stock.strategy.universe.liquidity.selection.DailyTradingValueSelectionPolicy;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.stream.Collectors;

import static com.stock.strategy.universe.candidate.evaluation.runner.support.StockCandidateEvaluationRunnerFixture.disabledProperties;
import static com.stock.strategy.universe.candidate.evaluation.runner.support.StockCandidateEvaluationRunnerFixture.properties;
import static com.stock.strategy.universe.candidate.evaluation.snapshot.support.StockCandidateEvaluationSnapshotFixture.completeSnapshot;
import static com.stock.strategy.universe.candidate.evaluation.snapshot.support.StockCandidateEvaluationSnapshotFixture.replay;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.eligible;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.history;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

@DataJpaTest
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({
        StockCandidateEvaluationQueryService.class,
        StockCandidateEvaluationService.class,
        StockEligibilityPolicy.class,
        DailyPriceHistoryQueryService.class,
        DailyTradingValueSelectionEvaluationService.class,
        DailyTradingValueAverageCalculator.class,
        DailyTradingValueSelectionPolicy.class,
        DailyTradingValueRankingPolicy.class,
        StockCandidateEvaluationSnapshotStore.class,
        StockCandidateEvaluationSnapshotJsonConverter.class,
        DailyTradingValueSelectionSnapshotStore.class,
        DailyTradingValueSelectionSnapshotJsonConverter.class
})
@ExtendWith(OutputCaptureExtension.class)
class StockCandidateEvaluationRunnerIntegrationTest {
    @Autowired
    private StockCandidateEvaluationQueryService queryService;
    @Autowired
    private StockCandidateEvaluationService evaluationService;
    @Autowired
    private StockCandidateEvaluationSnapshotStore snapshotStore;
    @Autowired
    private StockCandidateEvaluationSnapshotRepository snapshotRepository;
    @Autowired
    private DailyPriceBarRepository dailyPriceRepository;
    @Autowired
    private DailyTradingValueSelectionSnapshotStore liquidityStore;
    @Autowired
    private DailyTradingValueSelectionSnapshotRepository liquidityRepository;
    @Autowired
    private TestEntityManager entityManager;

    @ParameterizedTest(name = "case {index}")
    @MethodSource("com.stock.strategy.universe.candidate.evaluation.snapshot.support.StockCandidateEvaluationSnapshotFixture#snapshots")
    void readsStoredPricesAndRestoresExactlyTheDirectEvaluationForEveryOutcome(
            StockCandidateEvaluationSnapshot input, CapturedOutput output
    ) {
        saveHistories(input.evaluationResult().inputHistories());
        var properties = properties(input);
        List<Tuple> originalRows = storedRows();
        var bySymbol = input.evaluationResult().inputHistories().stream()
                .collect(Collectors.toMap(DailyPriceHistory::symbol, value -> value));
        var expectedHistories = properties.targetSymbols().stream()
                .map(symbol -> bySymbol.getOrDefault(symbol, new DailyPriceHistory(symbol, List.of()))).toList();
        var expected = StockCandidateEvaluationSnapshot.from(evaluationService.evaluate(
                properties.toRequest(), properties.eligibilityInputs(), expectedHistories));

        runner(properties).run(new DefaultApplicationArguments());
        entityManager.clear();

        assertThat(snapshotRepository.count()).isEqualTo(1L);
        var row = snapshotRepository.findAll().getFirst();
        var restored = snapshotStore.findById(row.getId()).orElseThrow();
        assertThat(restored).isEqualTo(expected);
        assertThat(restored.evaluationResult().status()).isEqualTo(input.evaluationResult().status());
        assertThat(restored.evaluationResult().candidateSymbols()).isEqualTo(input.evaluationResult().candidateSymbols());
        assertThat(replay(restored)).isEqualTo(restored.evaluationResult());
        assertThat(row.getEvaluationStatus()).isEqualTo(expected.evaluationResult().status());
        assertThat(storedRows()).containsExactlyInAnyOrderElementsOf(originalRows);
        assertThat(liquidityRepository.count()).isZero();
        assertThat(output).contains("Stock candidate evaluation recorded.", "snapshotId=" + row.getId(),
                "status=" + expected.evaluationResult().status());
    }

    @Test
    void recordsOmittedEligibilityAsUnverifiedWithoutInventingMetadataFromStoredPrices() {
        var input = completeSnapshot();
        saveHistories(input.evaluationResult().inputHistories());
        List<Tuple> originalRows = storedRows();
        var properties = properties(input.evaluationResult().request(), null);

        runner(properties).run(new DefaultApplicationArguments());
        entityManager.clear();

        var row = snapshotRepository.findAll().getFirst();
        var result = snapshotStore.findById(row.getId()).orElseThrow().evaluationResult();
        assertThat(result.status()).isEqualTo(StockCandidateEvaluationStatus.INCOMPLETE);
        assertThat(result.unverifiedSymbols()).containsExactlyElementsOf(properties.targetSymbols());
        assertThat(result.candidateSymbols()).isEmpty();
        assertThat(result.liquidityResult()).isNull();
        assertThat(result.eligibilityResults()).allSatisfy(value -> {
            assertThat(value.input().asOfDate()).isNull();
            assertThat(value.input().market()).isNull();
            assertThat(value.input().securityType()).isNull();
            assertThat(value.input().listingStatus()).isNull();
            assertThat(value.input().sourceReference()).isNull();
            assertThat(value.input().informationAvailableAt()).isNull();
        });
        assertThat(result.inputHistories()).isEqualTo(input.evaluationResult().inputHistories());
        assertThat(storedRows()).containsExactlyInAnyOrderElementsOf(originalRows);
        assertThat(snapshotRepository.count()).isEqualTo(1L);
    }

    @Test
    void repeatRunCreatesNewIdAndKeepsEarlierIncompleteEvidenceAfterEligibilityIsSupplied() {
        var input = completeSnapshot();
        saveHistories(input.evaluationResult().inputHistories());
        List<Tuple> originalRows = storedRows();
        var incompleteProperties = properties(input.evaluationResult().request(), List.of());
        var incompleteRunner = runner(incompleteProperties);
        incompleteRunner.run(new DefaultApplicationArguments());
        entityManager.clear();
        var first = snapshotRepository.findAll().getFirst();
        Long firstId = first.getId();
        String firstJson = first.getSnapshotJson();
        var firstRecordedAt = first.getRecordedAt();
        var firstSnapshot = snapshotStore.findById(firstId).orElseThrow();

        incompleteRunner.run(new DefaultApplicationArguments());
        runner(properties(input)).run(new DefaultApplicationArguments());
        entityManager.clear();

        var rows = snapshotRepository.findAll(Sort.by("id"));
        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(value -> value.getId()).doesNotHaveDuplicates();
        assertThat(rows.getFirst().getId()).isEqualTo(firstId);
        assertThat(rows.getFirst().getSnapshotJson()).isEqualTo(firstJson);
        assertThat(rows.getFirst().getRecordedAt()).isEqualTo(firstRecordedAt);
        assertThat(rows.getFirst().getEvaluationStatus()).isEqualTo(StockCandidateEvaluationStatus.INCOMPLETE);
        assertThat(snapshotStore.findById(firstId)).contains(firstSnapshot);
        assertThat(snapshotStore.findById(rows.get(1).getId())).contains(firstSnapshot);
        assertThat(snapshotStore.findById(rows.getLast().getId())).contains(input);
        assertThat(replay(snapshotStore.findById(firstId).orElseThrow())).isEqualTo(firstSnapshot.evaluationResult());
        assertThat(storedRows()).containsExactlyInAnyOrderElementsOf(originalRows);
    }

    @Test
    void disabledRunnerDoesNotCreateSnapshotOrChangeDailyPrices(CapturedOutput output) {
        saveHistories(completeSnapshot().evaluationResult().inputHistories());
        List<Tuple> originalRows = storedRows();

        runner(disabledProperties()).run(new DefaultApplicationArguments());

        assertThat(snapshotRepository.count()).isZero();
        assertThat(liquidityRepository.count()).isZero();
        assertThat(storedRows()).containsExactlyInAnyOrderElementsOf(originalRows);
        assertThat(output).doesNotContain("Stock candidate evaluation started.", "Stock candidate evaluation recorded.");
    }

    @Test
    void storedVenueConflictDoesNotSavePartialEvaluationOrChangeRows(CapturedOutput output) {
        saveHistories(List.of(history("005930", 100L, TradingVenueScope.KRX)));
        List<Tuple> originalRows = storedRows();
        var properties = properties(request("000660", "005930"), List.of(eligible("000660"), eligible("005930")));

        assertThatThrownBy(() -> runner(properties).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(snapshotRepository.count()).isZero();
        assertThat(liquidityRepository.count()).isZero();
        assertThat(storedRows()).containsExactlyInAnyOrderElementsOf(originalRows);
        assertThat(output).doesNotContain("Stock candidate evaluation recorded.");
    }

    @Test
    void doesNotModifyExistingLiquiditySnapshotWhenRecordingCombinedEvaluation() {
        var input = completeSnapshot();
        saveHistories(input.evaluationResult().inputHistories());
        List<Tuple> originalRows = storedRows();
        var liquidity = DailyTradingValueSelectionSnapshot.from(input.evaluationResult().liquidityResult());
        Long liquidityId = liquidityStore.save(liquidity);
        entityManager.clear();
        var legacy = liquidityRepository.findById(liquidityId).orElseThrow();
        String legacyJson = legacy.getSnapshotJson();
        var legacyRecordedAt = legacy.getRecordedAt();

        runner(properties(input)).run(new DefaultApplicationArguments());
        entityManager.clear();

        var unchanged = liquidityRepository.findById(liquidityId).orElseThrow();
        assertThat(liquidityRepository.count()).isEqualTo(1L);
        assertThat(unchanged.getSnapshotJson()).isEqualTo(legacyJson);
        assertThat(unchanged.getRecordedAt()).isEqualTo(legacyRecordedAt);
        assertThat(unchanged.getSelectionAsOfDate()).isEqualTo(legacy.getSelectionAsOfDate());
        assertThat(unchanged.getEvaluationStatus()).isEqualTo(legacy.getEvaluationStatus());
        assertThat(liquidityStore.findById(liquidityId)).contains(liquidity);
        assertThat(snapshotRepository.count()).isEqualTo(1L);
        assertThat(storedRows()).containsExactlyInAnyOrderElementsOf(originalRows);
    }

    private void saveHistories(List<DailyPriceHistory> histories) {
        dailyPriceRepository.saveAllAndFlush(histories.stream()
                .flatMap(history -> history.bars().stream().map(bar -> DailyPriceBarEntity.from(history.symbol(), bar))).toList());
        entityManager.clear();
    }

    private List<Tuple> storedRows() {
        entityManager.clear();
        return dailyPriceRepository.findAll().stream().map(row -> tuple(row.getId(), row.getSymbol(), row.toBar())).toList();
    }

    private StockCandidateEvaluationRunner runner(StockCandidateEvaluationProperties properties) {
        // Seed the JPA slice first; invoking this runner does not launch an application process.
        return new StockCandidateEvaluationRunner(queryService, snapshotStore, properties);
    }
}
