package com.stock.strategy.universe.candidate.evaluation.snapshot.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.TradingVenueScope;
import com.stock.market.price.history.persistence.DailyPriceBarEntity;
import com.stock.market.price.history.persistence.DailyPriceBarRepository;
import com.stock.strategy.universe.candidate.evaluation.request.StockCandidateEvaluationRequest;
import com.stock.strategy.universe.candidate.evaluation.result.StockCandidateEvaluationStatus;
import com.stock.strategy.universe.candidate.evaluation.snapshot.StockCandidateEvaluationSnapshot;
import com.stock.strategy.universe.candidate.evaluation.snapshot.json.StockCandidateEvaluationSnapshotJsonConverter;
import com.stock.strategy.universe.candidate.evaluation.snapshot.persistence.StockCandidateEvaluationSnapshotRepository;
import com.stock.strategy.universe.eligibility.request.StockEligibilityRequest;
import com.stock.strategy.universe.liquidity.evaluation.request.DailyTradingValueSelectionEvaluationRequest;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.DailyTradingValueSelectionSnapshot;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.json.DailyTradingValueSelectionSnapshotJsonConverter;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.persistence.DailyTradingValueSelectionSnapshotRepository;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.storage.DailyTradingValueSelectionSnapshotStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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

import static com.stock.strategy.universe.candidate.evaluation.snapshot.support.StockCandidateEvaluationSnapshotFixture.completeSnapshot;
import static com.stock.strategy.universe.candidate.evaluation.snapshot.support.StockCandidateEvaluationSnapshotFixture.eligibilityIncompleteSnapshot;
import static com.stock.strategy.universe.candidate.evaluation.snapshot.support.StockCandidateEvaluationSnapshotFixture.liquidityIncompleteSnapshot;
import static com.stock.strategy.universe.candidate.evaluation.snapshot.support.StockCandidateEvaluationSnapshotFixture.replay;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.CUTOFF;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.DATE;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.eligible;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.eligibilityRequest;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.history;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.service;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({StockCandidateEvaluationSnapshotStore.class, StockCandidateEvaluationSnapshotJsonConverter.class,
        DailyTradingValueSelectionSnapshotStore.class, DailyTradingValueSelectionSnapshotJsonConverter.class})
class StockCandidateEvaluationSnapshotStoreIntegrationTest {
    @Autowired
    private StockCandidateEvaluationSnapshotStore store;
    @Autowired
    private StockCandidateEvaluationSnapshotRepository repository;
    @Autowired
    private StockCandidateEvaluationSnapshotJsonConverter converter;
    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DailyTradingValueSelectionSnapshotStore liquidityStore;
    @Autowired
    private DailyTradingValueSelectionSnapshotRepository liquidityRepository;
    @Autowired
    private DailyPriceBarRepository dailyPriceRepository;

    @ParameterizedTest(name = "case {index}")
    @MethodSource("com.stock.strategy.universe.candidate.evaluation.snapshot.support.StockCandidateEvaluationSnapshotFixture#snapshots")
    void restoresEveryEvaluationPathAfterClearingPersistenceContext(StockCandidateEvaluationSnapshot original) {
        Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);

        Long id = store.save(original);
        Instant after = Instant.now().plusMillis(1);
        entityManager.clear();
        StockCandidateEvaluationSnapshot restored = store.findById(id).orElseThrow();

        assertThat(id).isPositive();
        assertThat(restored).isEqualTo(original);
        assertThat(replay(restored)).isEqualTo(original.evaluationResult());
        assertThat(restored.evaluationResult().candidateSymbols()).isEqualTo(original.evaluationResult().candidateSymbols());
        assertThat(restored.evaluationResult().unverifiedSymbols()).isEqualTo(original.evaluationResult().unverifiedSymbols());
        var row = repository.findById(id).orElseThrow();
        assertThat(row.getSelectionAsOfDate()).isEqualTo(DATE);
        assertThat(row.getEvaluationStatus()).isEqualTo(original.evaluationResult().status());
        assertThat(row.getRecordedAt()).isBetween(before, after);
        assertThat(row.getSnapshotJson()).isEqualTo(converter.toJson(original));
    }

    @Test
    void insertsIdenticalSnapshotTwiceInsteadOfDeduplicatingOrUpdating() {
        StockCandidateEvaluationSnapshot snapshot = completeSnapshot();

        Long firstId = store.save(snapshot);
        Long secondId = store.save(snapshot);
        entityManager.clear();

        assertThat(secondId).isNotEqualTo(firstId);
        assertThat(repository.count()).isEqualTo(2L);
        assertThat(store.findById(firstId)).contains(snapshot);
        assertThat(store.findById(secondId)).contains(snapshot);
    }

    @ParameterizedTest
    @ValueSource(strings = {"eligibility", "liquidity"})
    void preservesEarlierIncompleteEvidenceAfterNewCompleteEvaluation(String stage) {
        StockCandidateEvaluationSnapshot incomplete = stage.equals("eligibility")
                ? eligibilityIncompleteSnapshot() : liquidityIncompleteSnapshot();
        Long firstId = store.save(incomplete);
        entityManager.clear();
        var firstRow = repository.findById(firstId).orElseThrow();
        String originalJson = firstRow.getSnapshotJson();
        Instant originalRecordedAt = firstRow.getRecordedAt();
        StockCandidateEvaluationSnapshot complete = StockCandidateEvaluationSnapshot.from(service().evaluate(
                incomplete.evaluationResult().request(), List.of(eligible("005930"), eligible("000660")),
                List.of(history("005930", 300L), history("000660", 100L))));
        assertThat(complete.evaluationResult().status()).isEqualTo(StockCandidateEvaluationStatus.COMPLETE);

        Long secondId = store.save(complete);
        entityManager.clear();

        assertThat(secondId).isNotEqualTo(firstId);
        assertThat(repository.count()).isEqualTo(2L);
        assertThat(store.findById(firstId)).contains(incomplete);
        assertThat(store.findById(secondId)).contains(complete);
        var unchanged = repository.findById(firstId).orElseThrow();
        assertThat(unchanged.getSnapshotJson()).isEqualTo(originalJson);
        assertThat(unchanged.getRecordedAt()).isEqualTo(originalRecordedAt);
        assertThat(unchanged.getEvaluationStatus()).isEqualTo(StockCandidateEvaluationStatus.INCOMPLETE);
        assertThat(replay(store.findById(firstId).orElseThrow())).isEqualTo(incomplete.evaluationResult());
    }

    @Test
    void persistsLargeJsonNanosecondCutoffAndTotalsBeyondLongWithoutTruncation() {
        List<LocalDate> dates = IntStream.range(0, 400).mapToObj(index -> DATE.minusDays(399L - index)).toList();
        StockEligibilityRequest eligibility = new StockEligibilityRequest(DATE, CUTOFF.plusNanos(123456789),
                eligibilityRequest().eligibleMarkets(), eligibilityRequest().eligibleSecurityTypes());
        StockCandidateEvaluationRequest request = new StockCandidateEvaluationRequest(eligibility,
                new DailyTradingValueSelectionEvaluationRequest(List.of("005930"), DATE, dates,
                        TradingVenueScope.INTEGRATED, Long.MAX_VALUE, 1));
        DailyPriceHistory history = new DailyPriceHistory("005930", dates.stream()
                .map(date -> new DailyPriceBar(date, 100L, 110L, 90L, 100L, 10L,
                        Long.MAX_VALUE, TradingVenueScope.INTEGRATED)).toList());
        StockCandidateEvaluationSnapshot snapshot = StockCandidateEvaluationSnapshot.from(service().evaluate(
                request, List.of(eligible("005930")), List.of(history)));
        String json = converter.toJson(snapshot);
        assertThat(json.length()).isGreaterThan(65_535);

        Long id = store.save(snapshot);
        entityManager.clear();

        StockCandidateEvaluationSnapshot restored = store.findById(id).orElseThrow();
        assertThat(restored).isEqualTo(snapshot);
        assertThat(restored.evaluationResult().liquidityResult().calculatedAverages().getFirst().totalTradingValueKrw())
                .isEqualTo(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(400L)));
        assertThat(restored.evaluationResult().request().eligibilityRequest().selectionCutoffAt())
                .isEqualTo(CUTOFF.plusNanos(123456789));
        assertThat(repository.findById(id).orElseThrow().getSnapshotJson()).isEqualTo(json);
        assertThat(replay(restored)).isEqualTo(snapshot.evaluationResult());
    }

    @Test
    void returnsEmptyForMissingIdWithoutCreatingDefaultSnapshot() {
        assertThat(store.findById(Long.MAX_VALUE)).isEmpty();
        assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-json", "null"})
    void rejectsCorruptStoredJsonWithoutRepairingOrUpdatingIt(String brokenJson) {
        Long id = store.save(completeSnapshot());
        overwriteJson(id, brokenJson);

        assertThatThrownBy(() -> store.findById(id)).isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.findById(id).orElseThrow().getSnapshotJson()).isEqualTo(brokenJson);
        assertThat(repository.count()).isEqualTo(1L);
    }

    @Test
    void rejectsUnsupportedVersionInOtherwiseCompleteStoredDocumentWithoutMigratingIt() throws Exception {
        Long id = store.save(completeSnapshot());
        ObjectNode tree = (ObjectNode) objectMapper.readTree(repository.findById(id).orElseThrow().getSnapshotJson());
        tree.put("schemaVersion", 2);
        String json = objectMapper.writeValueAsString(tree);
        overwriteJson(id, json);

        assertThatThrownBy(() -> store.findById(id)).isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.findById(id).orElseThrow().getSnapshotJson()).isEqualTo(json);
        assertThat(repository.count()).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"date", "status"})
    void rejectsMetadataMismatchInsteadOfReturningApparentlyValidSnapshot(String mismatch) {
        Long id = store.save(completeSnapshot());
        if (mismatch.equals("date")) {
            jdbcTemplate.update("UPDATE stock_candidate_evaluation_snapshot SET selection_as_of_date = ? WHERE id = ?",
                    Date.valueOf(DATE.plusDays(1)), id);
        } else {
            jdbcTemplate.update("UPDATE stock_candidate_evaluation_snapshot SET evaluation_status = ? WHERE id = ?",
                    "INCOMPLETE", id);
        }
        entityManager.clear();

        assertThatThrownBy(() -> store.findById(id)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Stored stock candidate evaluation snapshot metadata does not match payload. id=" + id);
        assertThat(repository.count()).isEqualTo(1L);
        var unchanged = repository.findById(id).orElseThrow();
        if (mismatch.equals("date")) {
            assertThat(unchanged.getSelectionAsOfDate()).isEqualTo(DATE.plusDays(1));
        } else {
            assertThat(unchanged.getEvaluationStatus()).isEqualTo(StockCandidateEvaluationStatus.INCOMPLETE);
        }
    }

    @Test
    void doesNotModifyExistingLiquiditySnapshotOrDailyPriceRows() {
        StockCandidateEvaluationSnapshot snapshot = completeSnapshot();
        DailyTradingValueSelectionSnapshot liquidity = DailyTradingValueSelectionSnapshot.from(
                snapshot.evaluationResult().liquidityResult());
        Long liquidityId = liquidityStore.save(liquidity);
        DailyPriceBar bar = snapshot.evaluationResult().inputHistories().getFirst().bars().getFirst();
        Long dailyPriceId = dailyPriceRepository.saveAndFlush(DailyPriceBarEntity.from("000660", bar)).getId();
        entityManager.clear();
        var legacy = liquidityRepository.findById(liquidityId).orElseThrow();
        String legacyJson = legacy.getSnapshotJson();
        Instant legacyRecordedAt = legacy.getRecordedAt();

        Long candidateId = store.save(snapshot);
        entityManager.clear();
        assertThat(store.findById(candidateId)).contains(snapshot);
        assertThat(liquidityStore.findById(liquidityId)).contains(liquidity);

        var unchanged = liquidityRepository.findById(liquidityId).orElseThrow();
        assertThat(unchanged.getSnapshotJson()).isEqualTo(legacyJson);
        assertThat(unchanged.getRecordedAt()).isEqualTo(legacyRecordedAt);
        assertThat(unchanged.getEvaluationStatus()).isEqualTo(legacy.getEvaluationStatus());
        assertThat(unchanged.getSelectionAsOfDate()).isEqualTo(legacy.getSelectionAsOfDate());
        assertThat(liquidityRepository.count()).isEqualTo(1L);
        assertThat(dailyPriceRepository.count()).isEqualTo(1L);
        assertThat(dailyPriceRepository.findById(dailyPriceId).orElseThrow().toBar()).isEqualTo(bar);
        assertThat(repository.count()).isEqualTo(1L);
    }

    private void overwriteJson(Long id, String json) {
        jdbcTemplate.update("UPDATE stock_candidate_evaluation_snapshot SET snapshot_json = ? WHERE id = ?", json, id);
        entityManager.clear();
    }
}
