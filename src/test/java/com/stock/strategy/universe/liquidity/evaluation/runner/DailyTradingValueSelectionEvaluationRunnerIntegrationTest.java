package com.stock.strategy.universe.liquidity.evaluation.runner;

import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.TradingVenueScope;
import com.stock.market.price.history.persistence.DailyPriceBarEntity;
import com.stock.market.price.history.persistence.DailyPriceBarRepository;
import com.stock.market.price.history.query.DailyPriceHistoryQueryService;
import com.stock.strategy.universe.liquidity.DailyTradingValueAverageCalculator;
import com.stock.strategy.universe.liquidity.evaluation.DailyTradingValueSelectionEvaluationService;
import com.stock.strategy.universe.liquidity.evaluation.query.DailyTradingValueSelectionQueryService;
import com.stock.strategy.universe.liquidity.evaluation.result.DailyTradingValueSelectionEvaluationStatus;
import com.stock.strategy.universe.liquidity.evaluation.runner.config.DailyTradingValueSelectionEvaluationProperties;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.DailyTradingValueSelectionSnapshot;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.json.DailyTradingValueSelectionSnapshotJsonConverter;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.persistence.DailyTradingValueSelectionSnapshotRepository;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.storage.DailyTradingValueSelectionSnapshotStore;
import com.stock.strategy.universe.liquidity.ranking.DailyTradingValueRankingPolicy;
import com.stock.strategy.universe.liquidity.selection.DailyTradingValueSelectionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

import static com.stock.strategy.universe.liquidity.evaluation.snapshot.support.DailyTradingValueSelectionSnapshotFixture.TRADING_DATES;
import static com.stock.strategy.universe.liquidity.evaluation.snapshot.support.DailyTradingValueSelectionSnapshotFixture.bar;
import static com.stock.strategy.universe.liquidity.evaluation.snapshot.support.DailyTradingValueSelectionSnapshotFixture.completeSnapshot;
import static com.stock.strategy.universe.liquidity.evaluation.snapshot.support.DailyTradingValueSelectionSnapshotFixture.incompleteSnapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({
        DailyTradingValueSelectionSnapshotStore.class,
        DailyTradingValueSelectionSnapshotJsonConverter.class,
        DailyTradingValueSelectionQueryService.class,
        DailyPriceHistoryQueryService.class,
        DailyTradingValueSelectionEvaluationService.class,
        DailyTradingValueAverageCalculator.class,
        DailyTradingValueSelectionPolicy.class,
        DailyTradingValueRankingPolicy.class
})
@ExtendWith(OutputCaptureExtension.class)
class DailyTradingValueSelectionEvaluationRunnerIntegrationTest {
    @Autowired
    private DailyTradingValueSelectionQueryService queryService;
    @Autowired
    private DailyTradingValueSelectionSnapshotStore snapshotStore;
    @Autowired
    private DailyTradingValueSelectionSnapshotRepository snapshotRepository;
    @Autowired
    private DailyPriceBarRepository dailyPriceRepository;
    @Autowired
    private TestEntityManager entityManager;

    @ParameterizedTest
    @EnumSource(DailyTradingValueSelectionEvaluationStatus.class)
    void readsStoredPricesAndRestoresExactlyTheRecordedEvaluation(
            DailyTradingValueSelectionEvaluationStatus status, CapturedOutput output
    ) {
        var input = status == DailyTradingValueSelectionEvaluationStatus.COMPLETE
                ? completeSnapshot() : incompleteSnapshot();
        saveHistories(input.evaluationResult().inputHistories());
        var originalRows = dailyPriceRepository.findAll(Sort.by("symbol", "tradingDate"));
        List<Long> originalIds = originalRows.stream().map(DailyPriceBarEntity::getId).toList();
        var originalBars = originalRows.stream().map(DailyPriceBarEntity::toBar).toList();
        var expected = DailyTradingValueSelectionSnapshot.from(queryService.evaluate(properties().toRequest()));
        assertThat(snapshotRepository.count()).isZero();

        runner().run(new DefaultApplicationArguments());
        entityManager.clear();

        assertThat(snapshotRepository.count()).isEqualTo(1L);
        var row = snapshotRepository.findAll().getFirst();
        assertThat(snapshotStore.findById(row.getId())).contains(expected);
        assertThat(row.getEvaluationStatus()).isEqualTo(status);
        var unchanged = dailyPriceRepository.findAll(Sort.by("symbol", "tradingDate"));
        assertThat(unchanged).extracting(DailyPriceBarEntity::getId).isEqualTo(originalIds);
        assertThat(unchanged).extracting(DailyPriceBarEntity::toBar).isEqualTo(originalBars);
        assertThat(output).contains("evaluation recorded.", "snapshotId=" + row.getId(), "status=" + status);
        if (status == DailyTradingValueSelectionEvaluationStatus.INCOMPLETE) {
            assertThat(expected.evaluationResult().unverifiedSymbols()).containsExactly("000660", "005380", "035420");
            assertThat(expected.evaluationResult().selectionResults()).isEmpty();
            assertThat(expected.evaluationResult().inputHistories()).extracting(DailyPriceHistory::symbol)
                    .containsExactly("000660", "005380", "005930", "035420");
        }
    }

    @Test
    void recordsAllMissingTargetsWithoutCreatingDailyPrices(CapturedOutput output) {
        runner().run(new DefaultApplicationArguments());
        entityManager.clear();

        assertThat(dailyPriceRepository.count()).isZero();
        assertThat(snapshotRepository.count()).isEqualTo(1L);
        var row = snapshotRepository.findAll().getFirst();
        var result = snapshotStore.findById(row.getId()).orElseThrow().evaluationResult();
        assertThat(result.request()).isEqualTo(properties().toRequest());
        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.INCOMPLETE);
        assertThat(result.unverifiedSymbols()).containsExactlyElementsOf(properties().targetSymbols());
        assertThat(result.calculatedAverages()).isEmpty();
        assertThat(result.selectionResults()).isEmpty();
        assertThat(result.inputHistories()).hasSize(4).allSatisfy(history -> assertThat(history.bars()).isEmpty());
        assertThat(output).contains("evaluation recorded.", "snapshotId=" + row.getId(), "status=INCOMPLETE",
                "targetCount=4", "calculatedCount=0", "selectedCount=0", "unverifiedCount=4");
    }

    @Test
    void recordsNewIdOnRepeatRunWithoutChangingPreviousEvidence() {
        saveHistories(completeSnapshot().evaluationResult().inputHistories());
        runner().run(new DefaultApplicationArguments());
        entityManager.clear();
        var first = snapshotRepository.findAll().getFirst();
        Long firstId = first.getId();
        String originalJson = first.getSnapshotJson();
        var recordedAt = first.getRecordedAt();

        runner().run(new DefaultApplicationArguments());
        entityManager.clear();

        var rows = snapshotRepository.findAll(Sort.by("id"));
        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().getId()).isEqualTo(firstId);
        assertThat(rows.getFirst().getSnapshotJson()).isEqualTo(originalJson);
        assertThat(rows.getFirst().getRecordedAt()).isEqualTo(recordedAt);
        assertThat(rows.getLast().getId()).isNotEqualTo(firstId);
        assertThat(snapshotStore.findById(rows.getFirst().getId()))
                .isEqualTo(snapshotStore.findById(rows.getLast().getId()));
    }

    @Test
    void doesNotSavePartialEvaluationWhenStoredVenueConflicts(CapturedOutput output) {
        saveHistories(List.of(new DailyPriceHistory("000660", TRADING_DATES.stream()
                .map(date -> bar(date, 100L, TradingVenueScope.KRX)).toList())));

        assertThatThrownBy(() -> runner().run(new DefaultApplicationArguments())).isInstanceOf(IllegalArgumentException.class);

        assertThat(snapshotRepository.count()).isZero();
        assertThat(dailyPriceRepository.count()).isEqualTo(3L);
        assertThat(output).doesNotContain("evaluation recorded.");
    }

    private void saveHistories(List<DailyPriceHistory> histories) {
        dailyPriceRepository.saveAllAndFlush(histories.stream()
                .flatMap(history -> history.bars().stream().map(bar -> DailyPriceBarEntity.from(history.symbol(), bar)))
                .toList());
    }

    private DailyTradingValueSelectionEvaluationRunner runner() {
        // Invoke after seeding prices, rather than during the JPA slice's application startup.
        return new DailyTradingValueSelectionEvaluationRunner(queryService, snapshotStore, properties());
    }

    private DailyTradingValueSelectionEvaluationProperties properties() {
        var request = completeSnapshot().evaluationResult().request();
        return new DailyTradingValueSelectionEvaluationProperties(
                true, request.targetSymbols(), request.selectionAsOfDate(), request.requiredTradingDates(),
                request.expectedVenueScope(), request.minimumAverageTradingValueKrw(), request.maxCandidateCount()
        );
    }
}
