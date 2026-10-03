package com.stock.strategy.universe.liquidity.evaluation.snapshot.storage;

import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.TradingVenueScope;
import com.stock.market.price.history.persistence.DailyPriceBarEntity;
import com.stock.market.price.history.persistence.DailyPriceBarRepository;
import com.stock.market.price.history.query.DailyPriceHistoryQueryService;
import com.stock.strategy.universe.liquidity.DailyTradingValueAverageCalculator;
import com.stock.strategy.universe.liquidity.evaluation.DailyTradingValueSelectionEvaluationService;
import com.stock.strategy.universe.liquidity.evaluation.query.DailyTradingValueSelectionQueryService;
import com.stock.strategy.universe.liquidity.evaluation.request.DailyTradingValueSelectionEvaluationRequest;
import com.stock.strategy.universe.liquidity.evaluation.result.DailyTradingValueSelectionEvaluationStatus;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.DailyTradingValueSelectionSnapshot;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.json.DailyTradingValueSelectionSnapshotJsonConverter;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.persistence.DailyTradingValueSelectionSnapshotRepository;
import com.stock.strategy.universe.liquidity.ranking.DailyTradingValueRankingPolicy;
import com.stock.strategy.universe.liquidity.selection.DailyTradingValueSelectionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigInteger;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.IntStream;

import static com.stock.strategy.universe.liquidity.evaluation.snapshot.support.DailyTradingValueSelectionSnapshotFixture.SELECTION_DATE;
import static com.stock.strategy.universe.liquidity.evaluation.snapshot.support.DailyTradingValueSelectionSnapshotFixture.TRADING_DATES;
import static com.stock.strategy.universe.liquidity.evaluation.snapshot.support.DailyTradingValueSelectionSnapshotFixture.bar;
import static com.stock.strategy.universe.liquidity.evaluation.snapshot.support.DailyTradingValueSelectionSnapshotFixture.completeSnapshot;
import static com.stock.strategy.universe.liquidity.evaluation.snapshot.support.DailyTradingValueSelectionSnapshotFixture.evaluate;
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
class DailyTradingValueSelectionSnapshotStoreIntegrationTest {
    @Autowired
    private DailyTradingValueSelectionSnapshotStore store;
    @Autowired
    private DailyTradingValueSelectionSnapshotRepository repository;
    @Autowired
    private DailyTradingValueSelectionSnapshotJsonConverter converter;
    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DailyPriceBarRepository dailyPriceRepository;
    @Autowired
    private DailyTradingValueSelectionQueryService queryService;

    @ParameterizedTest
    @EnumSource(DailyTradingValueSelectionEvaluationStatus.class)
    void roundTripsCompleteAndIncompleteSnapshotsAfterClearingPersistenceContext(
            DailyTradingValueSelectionEvaluationStatus status
    ) {
        DailyTradingValueSelectionSnapshot original = status == DailyTradingValueSelectionEvaluationStatus.COMPLETE
                ? completeSnapshot() : incompleteSnapshot();
        Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);

        Long id = store.save(original);
        Instant after = Instant.now().plusMillis(1);
        entityManager.clear();
        DailyTradingValueSelectionSnapshot restored = store.findById(id).orElseThrow();

        assertThat(id).isPositive();
        assertThat(restored).isEqualTo(original);
        assertThat(evaluate(restored.evaluationResult().request(), restored.evaluationResult().inputHistories()))
                .isEqualTo(original);
        var row = repository.findById(id).orElseThrow();
        assertThat(row.getSelectionAsOfDate()).isEqualTo(SELECTION_DATE);
        assertThat(row.getEvaluationStatus()).isEqualTo(status);
        assertThat(row.getRecordedAt()).isBetween(before, after);
        assertThat(row.getSnapshotJson()).isEqualTo(converter.toJson(original));
    }

    @Test
    void insertsIdenticalSnapshotTwiceInsteadOfDeduplicatingOrUpdating() {
        DailyTradingValueSelectionSnapshot snapshot = completeSnapshot();

        Long firstId = store.save(snapshot);
        Long secondId = store.save(snapshot);
        entityManager.clear();

        assertThat(secondId).isNotEqualTo(firstId);
        assertThat(repository.count()).isEqualTo(2L);
        assertThat(store.findById(firstId)).contains(snapshot);
        assertThat(store.findById(secondId)).contains(snapshot);
    }

    @Test
    void preservesEarlierIncompleteEvidenceAfterDailyPriceBackfillAndNewEvaluation() {
        var request = new DailyTradingValueSelectionEvaluationRequest(
                List.of("005930"), SELECTION_DATE, TRADING_DATES, TradingVenueScope.INTEGRATED, 100L, 1
        );
        var missingMetadata = DailyPriceBarEntity.from("005930", bar(TRADING_DATES.get(1), null, null));
        dailyPriceRepository.saveAllAndFlush(List.of(
                DailyPriceBarEntity.from("005930", bar(TRADING_DATES.getFirst(), 100L, TradingVenueScope.INTEGRATED)),
                missingMetadata,
                DailyPriceBarEntity.from("005930", bar(SELECTION_DATE, 100L, TradingVenueScope.INTEGRATED))
        ));
        DailyTradingValueSelectionSnapshot incomplete = DailyTradingValueSelectionSnapshot.from(
                queryService.evaluate(request)
        );
        assertThat(incomplete.evaluationResult().status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.INCOMPLETE);
        Long firstId = store.save(incomplete);
        Long missingMetadataId = missingMetadata.getId();
        entityManager.clear();
        var firstRow = repository.findById(firstId).orElseThrow();
        String originalJson = firstRow.getSnapshotJson();
        Instant originalRecordedAt = firstRow.getRecordedAt();

        dailyPriceRepository.findById(missingMetadataId).orElseThrow().fillMissingTradingValueMetadata(
                bar(TRADING_DATES.get(1), 100L, TradingVenueScope.INTEGRATED)
        );
        dailyPriceRepository.flush();
        entityManager.clear();
        DailyTradingValueSelectionSnapshot complete = DailyTradingValueSelectionSnapshot.from(queryService.evaluate(request));
        assertThat(complete.evaluationResult().status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.COMPLETE);
        Long secondId = store.save(complete);
        entityManager.clear();

        assertThat(secondId).isNotEqualTo(firstId);
        assertThat(repository.count()).isEqualTo(2L);
        assertThat(store.findById(firstId)).contains(incomplete);
        assertThat(store.findById(secondId)).contains(complete);
        var unchanged = repository.findById(firstId).orElseThrow();
        assertThat(unchanged.getSnapshotJson()).isEqualTo(originalJson);
        assertThat(unchanged.getRecordedAt()).isEqualTo(originalRecordedAt);
        DailyTradingValueSelectionSnapshot restored = store.findById(firstId).orElseThrow();
        assertThat(evaluate(restored.evaluationResult().request(), restored.evaluationResult().inputHistories()))
                .isEqualTo(incomplete);
        assertThat(dailyPriceRepository.count()).isEqualTo(3L);
        assertThat(dailyPriceRepository.findById(missingMetadataId).orElseThrow().getTradingValueKrw())
                .isEqualTo(100L);
    }

    @Test
    void persistsLargeJsonAndTotalsBeyondLongWithoutTruncation() {
        List<LocalDate> dates = IntStream.range(0, 400)
                .mapToObj(index -> SELECTION_DATE.minusDays(399L - index)).toList();
        var request = new DailyTradingValueSelectionEvaluationRequest(
                List.of("005930"), SELECTION_DATE, dates, TradingVenueScope.INTEGRATED, Long.MAX_VALUE, 1
        );
        DailyTradingValueSelectionSnapshot snapshot = evaluate(request, List.of(new DailyPriceHistory(
                "005930", dates.stream().map(date -> bar(date, Long.MAX_VALUE, TradingVenueScope.INTEGRATED)).toList()
        )));
        String json = converter.toJson(snapshot);
        assertThat(json.length()).isGreaterThan(65_535);

        Long id = store.save(snapshot);
        entityManager.clear();

        DailyTradingValueSelectionSnapshot restored = store.findById(id).orElseThrow();
        assertThat(restored).isEqualTo(snapshot);
        assertThat(restored.evaluationResult().calculatedAverages().getFirst().totalTradingValueKrw())
                .isEqualTo(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(400L)));
        assertThat(repository.findById(id).orElseThrow().getSnapshotJson()).isEqualTo(json);
    }

    @Test
    void returnsEmptyForMissingIdWithoutCreatingDefaultSnapshot() {
        assertThat(store.findById(Long.MAX_VALUE)).isEmpty();
        assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "null", "{\"schemaVersion\":2}"})
    void rejectsCorruptOrUnsupportedStoredJsonWithoutRepairingIt(String brokenJson) {
        Long id = store.save(completeSnapshot());
        jdbcTemplate.update("UPDATE daily_trading_value_selection_snapshot SET snapshot_json = ? WHERE id = ?",
                brokenJson, id);
        entityManager.clear();

        assertThatThrownBy(() -> store.findById(id)).isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.findById(id).orElseThrow().getSnapshotJson()).isEqualTo(brokenJson);
        assertThat(repository.count()).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"date", "status"})
    void rejectsMetadataMismatchInsteadOfReturningAnApparentlyValidSnapshot(String mismatch) {
        Long id = store.save(completeSnapshot());
        if (mismatch.equals("date")) {
            jdbcTemplate.update("UPDATE daily_trading_value_selection_snapshot SET selection_as_of_date = ? WHERE id = ?",
                    Date.valueOf(SELECTION_DATE.plusDays(1)), id);
        } else {
            jdbcTemplate.update("UPDATE daily_trading_value_selection_snapshot SET evaluation_status = ? WHERE id = ?",
                    "INCOMPLETE", id);
        }
        entityManager.clear();

        assertThatThrownBy(() -> store.findById(id)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Stored daily trading value selection snapshot metadata does not match payload. id=" + id);
        assertThat(repository.count()).isEqualTo(1L);
    }
}
