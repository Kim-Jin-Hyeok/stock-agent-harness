package com.stock.market.stock.basicinfo.observation.storage;

import com.stock.market.stock.basicinfo.observation.persistence.KisStockBasicInfoObservationRepository;
import com.stock.market.stock.basicinfo.provider.kis.parsing.KisStockBasicInfoParser;
import com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture;
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
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.RECORDED_AT;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.START;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.content;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.normalized;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.response;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.sha256;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({KisStockBasicInfoObservationStore.class, KisStockBasicInfoObservationStoreIntegrationTest.FixedClockConfiguration.class})
class KisStockBasicInfoObservationStoreIntegrationTest {
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

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        Clock observationClock() {
            return Clock.fixed(RECORDED_AT, ZoneOffset.UTC);
        }
    }
}
