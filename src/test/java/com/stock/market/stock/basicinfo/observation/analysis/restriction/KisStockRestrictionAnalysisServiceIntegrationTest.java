package com.stock.market.stock.basicinfo.observation.analysis.restriction;

import com.stock.broker.kis.auth.KisTokenProvider;
import com.stock.market.stock.basicinfo.observation.persistence.KisStockBasicInfoObservationRepository;
import com.stock.market.stock.basicinfo.observation.storage.KisStockBasicInfoObservationStore;
import com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture;
import com.stock.market.stock.basicinfo.provider.kis.KisStockBasicInfoClient;
import com.stock.market.stock.basicinfo.provider.kis.KisStockBasicInfoProvider;
import com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.KisStockRestrictionScreeningPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.support.KisStockRestrictionScreeningFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
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
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import static com.stock.market.stock.basicinfo.observation.analysis.restriction.support.KisStockRestrictionAnalysisFixture.inputs;
import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.screening;
import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.service;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.RECORDED_AT;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.normalized;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.sha256;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningReasonCode.MARKET_WARNING_MARKET_NOT_MATCHED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus.EXCLUSION_SIGNAL_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus.NO_EXCLUSION_SIGNAL_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus.REVIEW_REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({KisStockBasicInfoObservationStore.class, KisStockRestrictionAnalysisServiceIntegrationTest.FixedClockConfiguration.class})
class KisStockRestrictionAnalysisServiceIntegrationTest {
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
    @MethodSource("marketAndWarningCases")
    void storedAnalysisMatchesDirectPoliciesAndLeavesAllStoredEvidenceUnchanged(
            KisStockMasterMarket market, String code, KisStockRestrictionScreeningStatus expectedStatus
    ) {
        var input = inputs(market, code, "N", Map.of(), Map.of());
        var expectedBasic = screening(input.master(), input.response());
        var expectedCombined = new KisStockRestrictionScreeningPolicy().evaluate(expectedBasic, input.warnings());
        Long id = store.save(input.response());
        entityManager.clear();
        var rowBefore = repository.findById(id).orElseThrow();
        var recordedBefore = rowBefore.getRecordedAt();
        entityManager.clear();
        var actualService = new KisStockRestrictionAnalysisService(service(store), new KisStockRestrictionScreeningPolicy());

        var result = actualService.analyze(id, input.master(), input.warnings());
        var repeated = actualService.analyze(id, input.master(), input.warnings());
        Arrays.fill(result.basicInfoAnalysis().response().content(), (byte) 0);
        entityManager.flush();
        entityManager.clear();

        assertThat(result.basicInfoAnalysis().observationId()).isEqualTo(id);
        assertThat(result.basicInfoAnalysis().response()).isEqualTo(normalized(input.response()));
        assertThat(result.basicInfoAnalysis().screeningResult()).isEqualTo(expectedBasic);
        assertThat(result.restrictionScreeningResult()).isEqualTo(expectedCombined);
        assertThat(result.restrictionScreeningResult().status()).isEqualTo(expectedStatus);
        assertThat(repeated).isEqualTo(result);
        assertThat(repository.count()).isEqualTo(1L);
        var rowAfter = repository.findById(id).orElseThrow();
        assertThat(rowAfter.getRawContent()).isEqualTo(input.response().content());
        assertThat(rowAfter.getContentLength()).isEqualTo(input.response().content().length);
        assertThat(rowAfter.getContentSha256()).isEqualTo(sha256(input.response().content()));
        assertThat(rowAfter.getRecordedAt()).isEqualTo(recordedBefore);
        assertThat(rowAfter.toRawResponse()).isEqualTo(normalized(input.response()));
        assertThat(context.getBeansOfType(KisStockBasicInfoProvider.class)).isEmpty();
        assertThat(context.getBeansOfType(KisStockBasicInfoClient.class)).isEmpty();
        assertThat(context.getBeansOfType(KisTokenProvider.class)).isEmpty();
        assertThat(context.getBeansOfType(KisStockRestrictionAnalysisService.class)).isEmpty();
    }

    @Test
    void differentMarketWarningsProduceReviewWithoutChangingOrReplacingTheStoredObservation() {
        var input = inputs();
        Long id = store.save(input.response());
        var otherWarnings = KisStockRestrictionScreeningFixture.warnings(input.master(), KOSPI);
        entityManager.clear();

        var result = new KisStockRestrictionAnalysisService(service(store), new KisStockRestrictionScreeningPolicy())
                .analyze(id, input.master(), otherWarnings);

        assertThat(result.restrictionScreeningResult().status()).isEqualTo(REVIEW_REQUIRED);
        assertThat(result.restrictionScreeningResult().reasonCodes()).containsExactly(MARKET_WARNING_MARKET_NOT_MATCHED);
        assertThat(result.basicInfoAnalysis().response()).isEqualTo(normalized(input.response()));
        assertThat(result.restrictionScreeningResult().marketWarningObservation()).isSameAs(otherWarnings);
        assertThat(repository.count()).isEqualTo(1L);
        assertThat(store.findById(id).orElseThrow()).isEqualTo(normalized(input.response()));
    }

    @Test
    void missingObservationDoesNotCreateAnyRow() {
        var input = inputs();

        assertThatThrownBy(() -> new KisStockRestrictionAnalysisService(service(store), new KisStockRestrictionScreeningPolicy())
                .analyze(Long.MAX_VALUE, input.master(), input.warnings()))
                .isExactlyInstanceOf(NoSuchElementException.class)
                .hasMessage("KIS stock basic info observation not found. id=" + Long.MAX_VALUE);
        assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @MethodSource("unparseableContents")
    void storedFailureIsNotRepairedOrReplacedByALaterSuccessfulObservation(byte[] bytes) {
        var input = inputs();
        Long failedId = store.save(KisStockBasicInfoObservationFixture.response(bytes));
        Long successfulId = store.save(input.response());
        entityManager.clear();
        var actual = new KisStockRestrictionAnalysisService(service(store), new KisStockRestrictionScreeningPolicy());

        assertThatThrownBy(() -> actual.analyze(failedId, input.master(), input.warnings()))
                .isExactlyInstanceOf(IllegalArgumentException.class).hasNoCause();

        entityManager.clear();
        assertThat(repository.count()).isEqualTo(2L);
        assertThat(store.findById(failedId).orElseThrow().content()).isEqualTo(bytes);
        assertThat(actual.analyze(successfulId, input.master(), input.warnings()).basicInfoAnalysis().response())
                .isEqualTo(normalized(input.response()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"raw_content", "content_sha256", "requested_symbol"})
    void corruptStoredEvidenceStopsWithoutRepairingTheRow(String column) {
        var input = inputs();
        Long id = store.save(input.response());
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
        var recordedBefore = rowBefore.getRecordedAt();
        entityManager.clear();

        assertThatThrownBy(() -> new KisStockRestrictionAnalysisService(service(store), new KisStockRestrictionScreeningPolicy())
                .analyze(id, input.master(), input.warnings()))
                .isExactlyInstanceOf(IllegalStateException.class).hasMessageEndingWith("id=" + id).hasNoCause();

        entityManager.clear();
        var rowAfter = repository.findById(id).orElseThrow();
        assertThat(rowAfter.getRawContent()).isEqualTo(bytesBefore);
        assertThat(rowAfter.getContentSha256()).isEqualTo(hashBefore);
        assertThat(rowAfter.getRequestedSymbol()).isEqualTo(symbolBefore);
        assertThat(rowAfter.getRecordedAt()).isEqualTo(recordedBefore);
        assertThat(repository.count()).isEqualTo(1L);
    }

    private static Stream<Arguments> marketAndWarningCases() {
        return Stream.of(KOSPI, KOSDAQ).flatMap(market -> Stream.of(
                Arguments.of(market, "00", NO_EXCLUSION_SIGNAL_OBSERVED),
                Arguments.of(market, "02", EXCLUSION_SIGNAL_OBSERVED),
                Arguments.of(market, "99", REVIEW_REQUIRED)));
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
