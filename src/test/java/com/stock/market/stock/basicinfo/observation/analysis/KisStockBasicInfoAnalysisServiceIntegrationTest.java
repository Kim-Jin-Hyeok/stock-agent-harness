package com.stock.market.stock.basicinfo.observation.analysis;

import com.stock.broker.kis.auth.KisTokenProvider;
import com.stock.market.stock.basicinfo.observation.persistence.KisStockBasicInfoObservationRepository;
import com.stock.market.stock.basicinfo.observation.storage.KisStockBasicInfoObservationStore;
import com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture;
import com.stock.market.stock.basicinfo.provider.kis.KisStockBasicInfoClient;
import com.stock.market.stock.basicinfo.provider.kis.KisStockBasicInfoProvider;
import com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.batch;
import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.fields;
import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.response;
import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.screening;
import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.service;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.RECORDED_AT;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.SYMBOL;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.normalized;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.sha256;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({KisStockBasicInfoObservationStore.class, KisStockBasicInfoAnalysisServiceIntegrationTest.FixedClockConfiguration.class})
class KisStockBasicInfoAnalysisServiceIntegrationTest {
    @Autowired
    private KisStockBasicInfoObservationStore store;
    @Autowired
    private KisStockBasicInfoObservationRepository repository;
    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ApplicationContext context;

    @ParameterizedTest
    @ValueSource(strings = {"N", "Y", " "})
    void storedAnalysisMatchesDirectPoliciesAfterPersistenceContextIsCleared(String suspension) {
        var original = response(SYMBOL, fields().put("tr_stop_yn", suspension));
        var master = batch();
        var expected = screening(master, original);
        Long id = store.save(original);
        entityManager.clear();
        var rowBefore = repository.findById(id).orElseThrow();
        var recordedBefore = rowBefore.getRecordedAt();
        entityManager.clear();

        var result = service(store).analyze(id, master);
        var repeated = service(store).analyze(id, master);
        Arrays.fill(result.response().content(), (byte) 0);
        entityManager.flush();
        entityManager.clear();

        assertThat(result.observationId()).isEqualTo(id);
        assertThat(result.response()).isEqualTo(normalized(original));
        assertThat(result.screeningResult()).isEqualTo(expected);
        assertThat(repeated).isEqualTo(result);
        assertThat(repository.count()).isEqualTo(1L);
        var rowAfter = repository.findById(id).orElseThrow();
        assertThat(rowAfter.getRawContent()).isEqualTo(original.content());
        assertThat(rowAfter.getContentLength()).isEqualTo(original.content().length);
        assertThat(rowAfter.getContentSha256()).isEqualTo(sha256(original.content()));
        assertThat(rowAfter.getRecordedAt()).isEqualTo(recordedBefore);
        assertThat(rowAfter.toRawResponse()).isEqualTo(normalized(original));
        assertThat(context.getBeansOfType(KisStockBasicInfoProvider.class)).isEmpty();
        assertThat(context.getBeansOfType(KisStockBasicInfoClient.class)).isEmpty();
        assertThat(context.getBeansOfType(KisTokenProvider.class)).isEmpty();
    }

    @Test
    void missingObservationDoesNotCreateAnyRow() {
        var master = batch();

        assertThatThrownBy(() -> service(store).analyze(Long.MAX_VALUE, master))
                .isExactlyInstanceOf(NoSuchElementException.class)
                .hasMessage("KIS stock basic info observation not found. id=" + Long.MAX_VALUE);
        assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @MethodSource("unparseableContents")
    void storedBusinessFailureOrMalformedPayloadIsNotRepairedOrReplacedByAnotherObservation(byte[] bytes) {
        Long failedId = store.save(KisStockBasicInfoObservationFixture.response(bytes));
        Long successfulId = store.save(response());
        entityManager.clear();
        var master = batch();

        assertThatThrownBy(() -> service(store).analyze(failedId, master)).isExactlyInstanceOf(IllegalArgumentException.class)
                .hasNoCause();

        entityManager.clear();
        assertThat(repository.count()).isEqualTo(2L);
        assertThat(store.findById(failedId).orElseThrow().content()).isEqualTo(bytes);
        assertThat(service(store).analyze(successfulId, master).response()).isEqualTo(normalized(response()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"raw_content", "content_sha256", "requested_symbol"})
    void corruptStoredObservationStopsWithoutRepairingOrWritingTheRow(String column) {
        var original = response();
        Long id = store.save(original);
        switch (column) {
            case "raw_content" -> jdbcTemplate.update("UPDATE kis_stock_basic_info_observation SET raw_content = ? WHERE id = ?",
                    new byte[]{1, 2, 3}, id);
            case "content_sha256" -> jdbcTemplate.update("UPDATE kis_stock_basic_info_observation SET content_sha256 = ? WHERE id = ?",
                    "0".repeat(64), id);
            case "requested_symbol" -> jdbcTemplate.update("UPDATE kis_stock_basic_info_observation SET requested_symbol = ? WHERE id = ?",
                    "bad", id);
            default -> throw new IllegalArgumentException("Unexpected test column.");
        }
        entityManager.clear();
        var rowBefore = repository.findById(id).orElseThrow();
        byte[] bytesBefore = rowBefore.getRawContent();
        String hashBefore = rowBefore.getContentSha256();
        String symbolBefore = rowBefore.getRequestedSymbol();
        entityManager.clear();
        var master = batch();

        assertThatThrownBy(() -> service(store).analyze(id, master)).isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageEndingWith("id=" + id).hasNoCause();

        entityManager.clear();
        var rowAfter = repository.findById(id).orElseThrow();
        assertThat(rowAfter.getRawContent()).isEqualTo(bytesBefore);
        assertThat(rowAfter.getContentSha256()).isEqualTo(hashBefore);
        assertThat(rowAfter.getRequestedSymbol()).isEqualTo(symbolBefore);
        assertThat(repository.count()).isEqualTo(1L);
    }

    private static Stream<byte[]> unparseableContents() {
        return Stream.of(KisStockBasicInfoParsingFixture.json("{\"rt_cd\":\"1\",\"msg1\":\"Synthetic failure\"}"),
                KisStockBasicInfoParsingFixture.json("not-json"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        Clock observationClock() {
            return Clock.fixed(RECORDED_AT, ZoneOffset.UTC);
        }
    }
}
