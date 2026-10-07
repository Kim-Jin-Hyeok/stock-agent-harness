package com.stock.market.stock.basicinfo.observation.storage;

import com.stock.market.stock.basicinfo.observation.persistence.KisStockBasicInfoObservationEntity;
import com.stock.market.stock.basicinfo.observation.persistence.KisStockBasicInfoObservationRepository;
import com.stock.market.stock.basicinfo.provider.kis.dto.KisStockBasicInfoRawResponse;
import com.stock.market.stock.basicinfo.provider.kis.parsing.KisStockBasicInfoParser;
import com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.RECORDED_AT;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.START;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.SYMBOL;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.content;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.normalized;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.response;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.sha256;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import({KisStockBasicInfoObservationStore.class, KisStockBasicInfoObservationStoreIntegrationTest.FixedClockConfiguration.class})
class KisStockBasicInfoObservationStoreIntegrationTest {
    private static final Instant EVALUATED_AT = RECORDED_AT.truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private KisStockBasicInfoObservationStore store;
    @Autowired
    private KisStockBasicInfoObservationRepository repository;
    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @ParameterizedTest(name = "content case {index}")
    @MethodSource("com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture#contents")
    void restoresExactBytesAndNormalizedTimesAfterClearingPersistenceContext(byte[] bytes) {
        var original = response(bytes);
        Long id = store.save(original);
        entityManager.clear();

        assertThat(id).isPositive();
        assertThat(store.findById(id)).contains(normalized(original));
        var row = repository.findById(id).orElseThrow();
        assertThat(row.getRecordedAt()).isEqualTo(RECORDED_AT.truncatedTo(ChronoUnit.MICROS));
        assertThat(row.getContentLength()).isEqualTo(bytes.length);
        assertThat(row.getContentSha256()).isEqualTo(sha256(bytes));
        assertThat(row.getRawContent()).isEqualTo(bytes);
        assertThat(jdbcTemplate.queryForObject("SELECT request_started_at FROM kis_stock_basic_info_observation WHERE id = ?",
                Timestamp.class, id).toInstant()).isEqualTo(START.truncatedTo(ChronoUnit.MICROS));
    }

    @Test
    void preservesIdenticalObservationsAsSeparateRowsInsteadOfUpdatingOrDeduplicating() {
        Long firstId = store.save(response());
        Long secondId = store.save(response());
        entityManager.clear();

        assertThat(secondId).isNotEqualTo(firstId);
        assertThat(repository.count()).isEqualTo(2L);
        assertThat(store.findById(firstId)).contains(normalized(response()));
        assertThat(store.findById(secondId)).contains(normalized(response()));
    }

    @Test
    void doesNotReplaceEarlierBusinessFailureWithLaterReceivedResponse() {
        byte[] failure = "{\"rt_cd\":\"1\"}".getBytes(StandardCharsets.UTF_8);
        Long firstId = store.save(response(failure));
        Long secondId = store.save(response());
        entityManager.clear();

        assertThat(repository.count()).isEqualTo(2L);
        assertThat(store.findById(firstId)).contains(normalized(response(failure)));
        assertThat(store.findById(secondId)).contains(normalized(response()));
    }

    @Test
    void replayingStoredSuccessfulResponseProducesIdenticalParserResultAndInputHash() {
        byte[] bytes = (" \n" + KisStockBasicInfoParsingFixture.root().toPrettyString() + "\r\n")
                .getBytes(StandardCharsets.UTF_8);
        var parser = new KisStockBasicInfoParser();
        var expected = parser.parse(bytes);
        Long id = store.save(response(bytes));
        entityManager.clear();

        var restored = store.findById(id).orElseThrow();
        assertThat(restored.content()).isEqualTo(bytes);
        assertThat(parser.parse(restored.content())).isEqualTo(expected);
        assertThat(repository.findById(id).orElseThrow().getContentSha256()).isEqualTo(expected.inputSha256());
    }

    @Test
    void supportsExactlyOneMiBOfArbitraryBytesThroughTheLobMapping() {
        byte[] bytes = new byte[KisStockBasicInfoParser.MAX_CONTENT_BYTES];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) index;
        }
        Long id = store.save(response(bytes));
        entityManager.clear();

        assertThat(store.findById(id).orElseThrow().content()).isEqualTo(bytes);
        assertThat(repository.findById(id).orElseThrow().getContentLength()).isEqualTo(bytes.length);
    }

    @Test
    void modifyingExposedArraysDoesNotChangeManagedOrStoredPayload() {
        Long id = store.save(response());
        entityManager.clear();
        var row = repository.findById(id).orElseThrow();

        Arrays.fill(row.getRawContent(), (byte) 0);
        Arrays.fill(store.findById(id).orElseThrow().content(), (byte) 0);
        entityManager.flush();
        entityManager.clear();

        assertThat(store.findById(id)).contains(normalized(response()));
        assertThat(repository.findById(id).orElseThrow().getContentSha256()).isEqualTo(sha256(content()));
    }

    @Test
    void returnsEmptyForMissingIdWithoutCreatingAnObservation() {
        assertThat(store.findById(Long.MAX_VALUE)).isEmpty();
        assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"raw_content", "empty_content", "content_length", "content_sha256",
            "requested_symbol", "http_status", "response_received_at"})
    void refusesCorruptStoredRowsWithoutReturningEmptyOrRepairingTheEvidence(String corruption) {
        Long id = store.save(response());
        switch (corruption) {
            case "raw_content" -> {
                byte[] changed = content();
                changed[0] ^= 1;
                jdbcTemplate.update("UPDATE kis_stock_basic_info_observation SET raw_content = ? WHERE id = ?", changed, id);
            }
            case "empty_content" -> jdbcTemplate.update(
                    "UPDATE kis_stock_basic_info_observation SET raw_content = ? WHERE id = ?", new byte[0], id);
            case "content_length" -> jdbcTemplate.update(
                    "UPDATE kis_stock_basic_info_observation SET content_length = ? WHERE id = ?", content().length + 1, id);
            case "content_sha256" -> jdbcTemplate.update(
                    "UPDATE kis_stock_basic_info_observation SET content_sha256 = ? WHERE id = ?", "0".repeat(64), id);
            case "requested_symbol" -> jdbcTemplate.update(
                    "UPDATE kis_stock_basic_info_observation SET requested_symbol = ? WHERE id = ?", "bad", id);
            case "http_status" -> jdbcTemplate.update(
                    "UPDATE kis_stock_basic_info_observation SET http_status = ? WHERE id = ?", 201, id);
            case "response_received_at" -> jdbcTemplate.update(
                    "UPDATE kis_stock_basic_info_observation SET response_received_at = ? WHERE id = ?",
                    Timestamp.from(START.minusSeconds(1).truncatedTo(ChronoUnit.MICROS)), id);
            default -> throw new IllegalArgumentException("Unexpected test corruption.");
        }
        entityManager.clear();
        var before = repository.findById(id).orElseThrow();
        byte[] corruptBytes = before.getRawContent();
        int corruptLength = before.getContentLength();
        String corruptHash = before.getContentSha256();
        String corruptSymbol = before.getRequestedSymbol();
        int corruptStatus = before.getHttpStatus();
        var corruptReceivedAt = before.getResponseReceivedAt();
        entityManager.clear();

        assertThatThrownBy(() -> store.findById(id)).isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageEndingWith("id=" + id).hasNoCause();
        entityManager.clear();
        var unchanged = repository.findById(id).orElseThrow();
        assertThat(unchanged.getRawContent()).isEqualTo(corruptBytes);
        assertThat(unchanged.getContentLength()).isEqualTo(corruptLength);
        assertThat(unchanged.getContentSha256()).isEqualTo(corruptHash);
        assertThat(unchanged.getRequestedSymbol()).isEqualTo(corruptSymbol);
        assertThat(unchanged.getHttpStatus()).isEqualTo(corruptStatus);
        assertThat(unchanged.getResponseReceivedAt()).isEqualTo(corruptReceivedAt);
        assertThat(repository.count()).isEqualTo(1L);
    }

    @Test
    void selectsLatestRequestForExactSymbolRatherThanAnotherSymbolsNewerObservation() {
        saveObservation(SYMBOL, EVALUATED_AT.minusSeconds(20), EVALUATED_AT.minusSeconds(19), EVALUATED_AT.minusSeconds(18));
        Long expectedId = saveObservation(SYMBOL, EVALUATED_AT.minusSeconds(10), EVALUATED_AT.minusSeconds(9), EVALUATED_AT.minusSeconds(8));
        saveObservation("005930", EVALUATED_AT.minusSeconds(3), EVALUATED_AT.minusSeconds(2), EVALUATED_AT.minusSeconds(1));
        entityManager.clear();

        assertThat(store.findLatestObservationId(SYMBOL, EVALUATED_AT)).contains(expectedId);
        assertThat(store.findLatestObservationId("000660", EVALUATED_AT)).isEmpty();
        assertThat(repository.count()).isEqualTo(3L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"response", "recorded", "both"})
    void excludesObservationsReceivedOrRecordedAfterEvaluationTime(String futureField) {
        Long expectedId = saveObservation(SYMBOL, EVALUATED_AT.minusSeconds(10), EVALUATED_AT.minusSeconds(9), EVALUATED_AT.minusSeconds(8));
        var future = EVALUATED_AT.plus(1, ChronoUnit.MICROS);
        var received = futureField.equals("recorded") ? EVALUATED_AT : future;
        var recorded = futureField.equals("response") ? EVALUATED_AT : future;
        saveObservation(SYMBOL, EVALUATED_AT.minusSeconds(1), received, recorded);
        entityManager.clear();

        assertThat(store.findLatestObservationId(SYMBOL, EVALUATED_AT)).contains(expectedId);
        assertThat(repository.count()).isEqualTo(2L);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, 0L, 999L})
    void honorsInclusiveMicrosecondBoundaryWithoutRoundingEvaluationTimeUp(long nanosAfterBoundary) {
        Long boundaryId = saveObservation(SYMBOL, EVALUATED_AT.minusSeconds(2), EVALUATED_AT, EVALUATED_AT);
        var future = EVALUATED_AT.plus(1, ChronoUnit.MICROS);
        saveObservation(SYMBOL, EVALUATED_AT.minusSeconds(1), future, future);
        entityManager.clear();

        var result = store.findLatestObservationId(SYMBOL, EVALUATED_AT.plusNanos(nanosAfterBoundary));

        if (nanosAfterBoundary < 0) {
            assertThat(result).isEmpty();
        } else {
            assertThat(result).contains(boundaryId);
        }
    }

    @Test
    void lateArrivalAndInsertionOfOlderRequestDoesNotSupersedeNewerRequest() {
        Long expectedId = saveObservation(SYMBOL, EVALUATED_AT.minusSeconds(10), EVALUATED_AT.minusSeconds(9), EVALUATED_AT.minusSeconds(8));
        Long lateId = saveObservation(SYMBOL, EVALUATED_AT.minusSeconds(20), EVALUATED_AT.minusSeconds(2), EVALUATED_AT.minusSeconds(1));
        entityManager.clear();

        assertThat(lateId).isGreaterThan(expectedId);
        assertThat(store.findLatestObservationId(SYMBOL, EVALUATED_AT)).contains(expectedId);
    }

    @Test
    void breaksEqualRequestTimeByResponseTimeBeforeId() {
        var start = EVALUATED_AT.minusSeconds(10);
        Long expectedId = saveObservation(SYMBOL, start, EVALUATED_AT.minusSeconds(6), EVALUATED_AT.minusSeconds(5));
        Long laterId = saveObservation(SYMBOL, start, EVALUATED_AT.minusSeconds(8), EVALUATED_AT.minusSeconds(1));
        entityManager.clear();

        assertThat(laterId).isGreaterThan(expectedId);
        assertThat(store.findLatestObservationId(SYMBOL, EVALUATED_AT)).contains(expectedId);
    }

    @Test
    void breaksEqualRequestAndResponseTimesByDescendingIdNotRecordedTime() {
        var start = EVALUATED_AT.minusSeconds(10);
        var received = EVALUATED_AT.minusSeconds(8);
        Long firstId = saveObservation(SYMBOL, start, received, EVALUATED_AT.minusSeconds(1));
        Long expectedId = saveObservation(SYMBOL, start, received, EVALUATED_AT.minusSeconds(5));
        entityManager.clear();

        assertThat(expectedId).isGreaterThan(firstId);
        assertThat(store.findLatestObservationId(SYMBOL, EVALUATED_AT)).contains(expectedId);
    }

    @Test
    void returnsEmptyForNoSavedObservationsWithoutCreatingRows() {
        assertThat(store.findLatestObservationId(SYMBOL, EVALUATED_AT)).isEmpty();
        assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "{\"rt_cd\":\"1\",\"msg1\":\"Synthetic business failure\"}"})
    void selectsLatestFailedContentInsteadOfFallingBackToOlderSuccess(String failedContent) {
        saveObservation(SYMBOL, EVALUATED_AT.minusSeconds(10), EVALUATED_AT.minusSeconds(9), EVALUATED_AT.minusSeconds(8));
        byte[] bytes = failedContent.getBytes(StandardCharsets.UTF_8);
        Long failedId = saveObservation(SYMBOL, EVALUATED_AT.minusSeconds(3), EVALUATED_AT.minusSeconds(2), EVALUATED_AT.minusSeconds(1), bytes);
        entityManager.clear();

        assertThat(store.findLatestObservationId(SYMBOL, EVALUATED_AT)).contains(failedId);
        assertThat(store.findById(failedId).orElseThrow().content()).isEqualTo(bytes);
        assertThat(repository.count()).isEqualTo(2L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"content_sha256", "http_status"})
    void selectsLatestCorruptRowAndLeavesRejectionToExistingRawRead(String corruption) {
        Long olderId = saveObservation(SYMBOL, EVALUATED_AT.minusSeconds(10), EVALUATED_AT.minusSeconds(9), EVALUATED_AT.minusSeconds(8));
        Long corruptId = saveObservation(SYMBOL, EVALUATED_AT.minusSeconds(3), EVALUATED_AT.minusSeconds(2), EVALUATED_AT.minusSeconds(1));
        if (corruption.equals("content_sha256")) {
            jdbcTemplate.update("UPDATE kis_stock_basic_info_observation SET content_sha256 = ? WHERE id = ?", "0".repeat(64), corruptId);
        } else {
            jdbcTemplate.update("UPDATE kis_stock_basic_info_observation SET http_status = ? WHERE id = ?", 201, corruptId);
        }
        entityManager.clear();

        assertThat(store.findLatestObservationId(SYMBOL, EVALUATED_AT)).contains(corruptId);
        assertThatThrownBy(() -> store.findById(corruptId)).isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageEndingWith("id=" + corruptId).hasNoCause();
        assertThat(store.findLatestObservationId(SYMBOL, EVALUATED_AT)).contains(corruptId);
        assertThat(store.findById(olderId)).isPresent();
        assertThat(repository.count()).isEqualTo(2L);
    }

    @Test
    void readsOneScalarIdWithoutLoadingEntitiesOrWritingEvidence() {
        saveObservation(SYMBOL, EVALUATED_AT.minusSeconds(10), EVALUATED_AT.minusSeconds(9), EVALUATED_AT.minusSeconds(8));
        Long expectedId = saveObservation(SYMBOL, EVALUATED_AT.minusSeconds(3), EVALUATED_AT.minusSeconds(2), EVALUATED_AT.minusSeconds(1));
        entityManager.clear();
        var statistics = entityManager.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        assertThat(store.findLatestObservationId(SYMBOL, EVALUATED_AT)).contains(expectedId);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1L);
        assertThat(statistics.getQueryExecutionCount()).isEqualTo(1L);
        assertThat(statistics.getEntityLoadCount()).isZero();
        assertThat(statistics.getEntityInsertCount()).isZero();
        assertThat(statistics.getEntityUpdateCount()).isZero();
        assertThat(statistics.getEntityDeleteCount()).isZero();
        assertThat(statistics.getQueries()).hasSize(1);
        assertThat(statistics.getQueries()[0]).startsWith("select observation.id ");
        assertThat(statistics.getQueryStatistics(statistics.getQueries()[0]).getExecutionRowCount()).isEqualTo(1L);
        assertThat(repository.count()).isEqualTo(2L);
    }

    private Long saveObservation(String symbol, Instant requestStartedAt, Instant responseReceivedAt, Instant recordedAt) {
        return saveObservation(symbol, requestStartedAt, responseReceivedAt, recordedAt, content());
    }

    private Long saveObservation(String symbol, Instant requestStartedAt, Instant responseReceivedAt, Instant recordedAt, byte[] bytes) {
        var rawResponse = new KisStockBasicInfoRawResponse(symbol, requestStartedAt, responseReceivedAt, 200, bytes);
        return repository.saveAndFlush(KisStockBasicInfoObservationEntity.from(rawResponse, recordedAt)).getId();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        Clock observationClock() {
            return Clock.fixed(RECORDED_AT, ZoneOffset.UTC);
        }
    }
}
