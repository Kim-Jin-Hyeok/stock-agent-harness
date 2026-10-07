package com.stock.market.stock.basicinfo.observation.analysis.restriction.precheck;

import com.stock.broker.kis.auth.KisTokenProvider;
import com.stock.market.stock.basicinfo.observation.analysis.restriction.KisStockRestrictionAnalysisService;
import com.stock.market.stock.basicinfo.observation.analysis.restriction.result.KisStockRestrictionAnalysisResult;
import com.stock.market.stock.basicinfo.observation.analysis.result.KisStockBasicInfoAnalysisResult;
import com.stock.market.stock.basicinfo.observation.persistence.KisStockBasicInfoObservationRepository;
import com.stock.market.stock.basicinfo.observation.storage.KisStockBasicInfoObservationStore;
import com.stock.market.stock.basicinfo.provider.kis.KisStockBasicInfoClient;
import com.stock.market.stock.basicinfo.provider.kis.KisStockBasicInfoProvider;
import com.stock.market.stock.master.parsing.result.StockMasterBatchParseResult;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.KisStockRestrictionFreshnessPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessStatus;
import com.stock.strategy.universe.eligibility.restriction.kis.precheck.KisStockRestrictionPrecheckPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.KisStockRestrictionScreeningPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus;
import org.hibernate.SessionFactory;
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

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.screening;
import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.service;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.RECORDED_AT;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.normalized;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.response;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.sha256;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessReasonCode.MASTER_OBSERVATION_SOURCE_UNVERIFIED;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessStatus.FRESH;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessStatus.TIME_UNVERIFIED;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.request;
import static com.stock.strategy.universe.eligibility.restriction.kis.precheck.result.KisStockRestrictionPrecheckStatus.BLOCKED;
import static com.stock.strategy.universe.eligibility.restriction.kis.precheck.result.KisStockRestrictionPrecheckStatus.CLEAR;
import static com.stock.strategy.universe.eligibility.restriction.kis.precheck.support.KisStockRestrictionPrecheckFixture.freshness;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningReasonCode.MARKET_WARNING_MARKET_NOT_MATCHED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus.NO_EXCLUSION_SIGNAL_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus.REVIEW_REQUIRED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.support.KisStockRestrictionScreeningFixture.warnings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import({KisStockBasicInfoObservationStore.class, KisStockRestrictionPrecheckServiceIntegrationTest.FixedClockConfiguration.class})
class KisStockRestrictionPrecheckServiceIntegrationTest {
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
    @MethodSource("statusCombinations")
    void readsOneStoredObservationAndPreservesEvidenceAcrossAllStatusCombinations(
            KisStockMasterMarket market, KisStockRestrictionScreeningStatus restrictionStatus,
            KisStockRestrictionFreshnessStatus freshnessStatus
    ) {
        var input = freshness(market, restrictionStatus, freshnessStatus).analysisResult();
        var master = master(input);
        var warnings = input.restrictionScreeningResult().marketWarningObservation();
        var request = request();
        Long id = store.save(input.basicInfoAnalysis().response());
        entityManager.clear();
        var rowBefore = repository.findById(id).orElseThrow();
        var recordedBefore = rowBefore.getRecordedAt();
        var responseBefore = rowBefore.toRawResponse();
        var expectedBasic = new KisStockBasicInfoAnalysisResult(id, normalized(input.basicInfoAnalysis().response()),
                screening(master, normalized(input.basicInfoAnalysis().response())));
        var expectedAnalysis = new KisStockRestrictionAnalysisResult(expectedBasic,
                new KisStockRestrictionScreeningPolicy().evaluate(expectedBasic.screeningResult(), warnings));
        var expected = new KisStockRestrictionPrecheckPolicy().evaluate(new KisStockRestrictionFreshnessPolicy().evaluate(request, expectedAnalysis));
        entityManager.clear();
        var statistics = entityManager.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var result = actualService().precheck(id, master, warnings, request);

        assertThat(statistics.getEntityLoadCount()).isEqualTo(1L);
        assertThat(statistics.getEntityInsertCount()).isZero();
        assertThat(statistics.getEntityUpdateCount()).isZero();
        assertThat(statistics.getEntityDeleteCount()).isZero();
        assertThat(result).isEqualTo(expected);
        assertThat(result.freshnessResult().status()).isEqualTo(freshnessStatus);
        assertThat(result.freshnessResult().analysisResult().restrictionScreeningResult().status()).isEqualTo(restrictionStatus);
        assertThat(result.freshnessResult().request()).isSameAs(request);
        assertThat(result.freshnessResult().analysisResult().restrictionScreeningResult().marketWarningObservation()).isSameAs(warnings);
        assertThat(master(result.freshnessResult().analysisResult())).isSameAs(master);
        Arrays.fill(result.freshnessResult().analysisResult().basicInfoAnalysis().response().content(), (byte) 0);
        entityManager.flush();
        entityManager.clear();

        var rowAfter = repository.findById(id).orElseThrow();
        assertThat(rowAfter.toRawResponse()).isEqualTo(responseBefore);
        assertThat(rowAfter.getRawContent()).isEqualTo(input.basicInfoAnalysis().response().content());
        assertThat(rowAfter.getContentLength()).isEqualTo(input.basicInfoAnalysis().response().content().length);
        assertThat(rowAfter.getContentSha256()).isEqualTo(sha256(input.basicInfoAnalysis().response().content()));
        assertThat(rowAfter.getRecordedAt()).isEqualTo(recordedBefore);
        assertThat(repository.count()).isEqualTo(1L);
        assertThat(context.getBeansOfType(KisStockBasicInfoProvider.class)).isEmpty();
        assertThat(context.getBeansOfType(KisStockBasicInfoClient.class)).isEmpty();
        assertThat(context.getBeansOfType(KisTokenProvider.class)).isEmpty();
        assertThat(context.getBeansOfType(KisStockRestrictionPrecheckService.class)).isEmpty();
        assertThat(context.getBeansOfType(KisStockRestrictionPrecheckPolicy.class)).isEmpty();
    }

    @Test
    void suppliedOtherMarketWarningsRemainBlockedDiagnosticsInsteadOfAnExecutionFailure() {
        var input = freshness(KOSDAQ, NO_EXCLUSION_SIGNAL_OBSERVED, FRESH).analysisResult();
        Long id = store.save(input.basicInfoAnalysis().response());
        var otherWarnings = warnings(master(input), KOSPI);
        entityManager.clear();

        var result = actualService().precheck(id, master(input), otherWarnings, request());

        assertThat(result.status()).isEqualTo(BLOCKED);
        assertThat(result.freshnessResult().status()).isEqualTo(TIME_UNVERIFIED);
        assertThat(result.freshnessResult().reasonCodes()).containsExactly(MASTER_OBSERVATION_SOURCE_UNVERIFIED);
        var restriction = result.freshnessResult().analysisResult().restrictionScreeningResult();
        assertThat(restriction.status()).isEqualTo(REVIEW_REQUIRED);
        assertThat(restriction.reasonCodes()).containsExactly(MARKET_WARNING_MARKET_NOT_MATCHED);
        assertThat(restriction.marketWarningObservation()).isSameAs(otherWarnings);
        assertThat(store.findById(id).orElseThrow()).isEqualTo(normalized(input.basicInfoAnalysis().response()));
        assertThat(repository.count()).isEqualTo(1L);
    }

    @Test
    void missingObservationStopsBeforePoliciesWithoutInsertingOrSubstitutingAnotherRow() {
        var input = freshness(KOSDAQ, NO_EXCLUSION_SIGNAL_OBSERVED, FRESH).analysisResult();
        store.save(input.basicInfoAnalysis().response());
        entityManager.clear();
        var freshnessPolicy = spy(new KisStockRestrictionFreshnessPolicy());
        var precheckPolicy = spy(new KisStockRestrictionPrecheckPolicy());
        var service = new KisStockRestrictionPrecheckService(
                new KisStockRestrictionAnalysisService(service(store), new KisStockRestrictionScreeningPolicy()), freshnessPolicy, precheckPolicy);

        assertThatThrownBy(() -> service.precheck(Long.MAX_VALUE, master(input), input.restrictionScreeningResult().marketWarningObservation(), request()))
                .isExactlyInstanceOf(NoSuchElementException.class)
                .hasMessage("KIS stock basic info observation not found. id=" + Long.MAX_VALUE);
        verifyNoInteractions(freshnessPolicy, precheckPolicy);
        assertThat(repository.count()).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "{\"rt_cd\":\"1\",\"msg1\":\"Synthetic failure\"}"})
    void doesNotReplaceFailedObservationWithAnotherSuccessfulObservation(String content) {
        var input = freshness(KOSDAQ, NO_EXCLUSION_SIGNAL_OBSERVED, FRESH).analysisResult();
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        Long failedId = store.save(response(bytes));
        Long successfulId = store.save(input.basicInfoAnalysis().response());
        entityManager.clear();
        var freshnessPolicy = spy(new KisStockRestrictionFreshnessPolicy());
        var precheckPolicy = spy(new KisStockRestrictionPrecheckPolicy());
        var service = new KisStockRestrictionPrecheckService(
                new KisStockRestrictionAnalysisService(service(store), new KisStockRestrictionScreeningPolicy()), freshnessPolicy, precheckPolicy);

        assertThatThrownBy(() -> service.precheck(failedId, master(input), input.restrictionScreeningResult().marketWarningObservation(), request()))
                .isExactlyInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(freshnessPolicy, precheckPolicy);
        assertThat(store.findById(failedId).orElseThrow().content()).isEqualTo(bytes);
        assertThat(repository.count()).isEqualTo(2L);
        assertThat(service.precheck(successfulId, master(input), input.restrictionScreeningResult().marketWarningObservation(), request()).status())
                .isEqualTo(CLEAR);
    }

    @Test
    void corruptStoredHashStopsWithoutRepairingEvidenceOrEvaluatingPolicies() {
        var input = freshness(KOSDAQ, NO_EXCLUSION_SIGNAL_OBSERVED, FRESH).analysisResult();
        Long id = store.save(input.basicInfoAnalysis().response());
        jdbcTemplate.update("UPDATE kis_stock_basic_info_observation SET content_sha256 = ? WHERE id = ?", "0".repeat(64), id);
        entityManager.clear();
        var rowBefore = repository.findById(id).orElseThrow();
        byte[] bytesBefore = rowBefore.getRawContent();
        var requestStartedBefore = rowBefore.getRequestStartedAt();
        var responseReceivedBefore = rowBefore.getResponseReceivedAt();
        var recordedBefore = rowBefore.getRecordedAt();
        entityManager.clear();
        var freshnessPolicy = spy(new KisStockRestrictionFreshnessPolicy());
        var precheckPolicy = spy(new KisStockRestrictionPrecheckPolicy());
        var service = new KisStockRestrictionPrecheckService(
                new KisStockRestrictionAnalysisService(service(store), new KisStockRestrictionScreeningPolicy()), freshnessPolicy, precheckPolicy);

        assertThatThrownBy(() -> service.precheck(id, master(input), input.restrictionScreeningResult().marketWarningObservation(), request()))
                .isExactlyInstanceOf(IllegalStateException.class).hasMessageEndingWith("id=" + id);
        verifyNoInteractions(freshnessPolicy, precheckPolicy);
        entityManager.clear();
        var rowAfter = repository.findById(id).orElseThrow();
        assertThat(rowAfter.getContentSha256()).isEqualTo("0".repeat(64));
        assertThat(rowAfter.getRawContent()).isEqualTo(bytesBefore);
        assertThat(rowAfter.getRequestStartedAt()).isEqualTo(requestStartedBefore);
        assertThat(rowAfter.getResponseReceivedAt()).isEqualTo(responseReceivedBefore);
        assertThat(rowAfter.getRecordedAt()).isEqualTo(recordedBefore);
        assertThat(repository.count()).isEqualTo(1L);
    }

    private KisStockRestrictionPrecheckService actualService() {
        return new KisStockRestrictionPrecheckService(new KisStockRestrictionAnalysisService(service(store), new KisStockRestrictionScreeningPolicy()),
                new KisStockRestrictionFreshnessPolicy(), new KisStockRestrictionPrecheckPolicy());
    }

    private static StockMasterBatchParseResult master(KisStockRestrictionAnalysisResult input) {
        return input.basicInfoAnalysis().screeningResult().observation().typeResolution().matchingResult().masterBatch();
    }

    private static Stream<Arguments> statusCombinations() {
        return Arrays.stream(KisStockMasterMarket.values()).flatMap(market -> Arrays.stream(KisStockRestrictionScreeningStatus.values())
                .flatMap(restriction -> Arrays.stream(KisStockRestrictionFreshnessStatus.values())
                        .map(status -> Arguments.of(market, restriction, status))));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        Clock observationClock() {
            return Clock.fixed(RECORDED_AT, ZoneOffset.UTC);
        }
    }
}
