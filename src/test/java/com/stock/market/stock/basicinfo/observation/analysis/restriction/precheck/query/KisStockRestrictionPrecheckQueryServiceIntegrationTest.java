package com.stock.market.stock.basicinfo.observation.analysis.restriction.precheck.query;

import com.stock.broker.kis.auth.KisTokenProvider;
import com.stock.market.stock.basicinfo.observation.analysis.restriction.KisStockRestrictionAnalysisService;
import com.stock.market.stock.basicinfo.observation.analysis.restriction.precheck.KisStockRestrictionPrecheckService;
import com.stock.market.stock.basicinfo.observation.analysis.restriction.result.KisStockRestrictionAnalysisResult;
import com.stock.market.stock.basicinfo.observation.analysis.result.KisStockBasicInfoAnalysisResult;
import com.stock.market.stock.basicinfo.observation.persistence.KisStockBasicInfoObservationRepository;
import com.stock.market.stock.basicinfo.observation.storage.KisStockBasicInfoObservationStore;
import com.stock.market.stock.basicinfo.provider.kis.KisStockBasicInfoClient;
import com.stock.market.stock.basicinfo.provider.kis.KisStockBasicInfoProvider;
import com.stock.market.stock.basicinfo.provider.kis.dto.KisStockBasicInfoRawResponse;
import com.stock.market.stock.master.parsing.result.StockMasterBatchParseResult;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.KisStockRestrictionFreshnessPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessStatus;
import com.stock.strategy.universe.eligibility.restriction.kis.precheck.KisStockRestrictionPrecheckPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.precheck.result.KisStockRestrictionPrecheckResult;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.KisStockRestrictionScreeningPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.screening;
import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.service;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.normalized;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessStatus.FRESH;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.EVALUATED_AT;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.request;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.withResponseTimes;
import static com.stock.strategy.universe.eligibility.restriction.kis.precheck.support.KisStockRestrictionPrecheckFixture.freshness;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus.NO_EXCLUSION_SIGNAL_OBSERVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import({KisStockBasicInfoObservationStore.class, KisStockRestrictionPrecheckQueryServiceIntegrationTest.FixedClockConfiguration.class})
class KisStockRestrictionPrecheckQueryServiceIntegrationTest {
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
    void selectsOneIdAndLoadsOneRawObservationWithoutChangingPolicyResultOrEvidence(
            KisStockMasterMarket market, KisStockRestrictionScreeningStatus restrictionStatus,
            KisStockRestrictionFreshnessStatus freshnessStatus
    ) {
        var input = freshness(market, restrictionStatus, freshnessStatus).analysisResult();
        var master = master(input);
        var warning = input.restrictionScreeningResult().marketWarningObservation();
        var request = request();
        Long id = store.save(input.basicInfoAnalysis().response());
        entityManager.clear();
        var before = repository.findById(id).orElseThrow();
        var responseBefore = before.toRawResponse();
        var recordedBefore = before.getRecordedAt();
        var hashBefore = before.getContentSha256();
        int lengthBefore = before.getContentLength();
        var basic = new KisStockBasicInfoAnalysisResult(id, normalized(input.basicInfoAnalysis().response()),
                screening(master, normalized(input.basicInfoAnalysis().response())));
        var analysis = new KisStockRestrictionAnalysisResult(basic, new KisStockRestrictionScreeningPolicy().evaluate(basic.screeningResult(), warning));
        var expected = new KisStockRestrictionPrecheckPolicy().evaluate(new KisStockRestrictionFreshnessPolicy().evaluate(request, analysis));
        var precheck = spy(actualService());
        var query = new KisStockRestrictionPrecheckQueryService(store, precheck);
        var statistics = resetStatistics();

        var result = query.precheckLatest(symbol(input), master, warning, request).orElseThrow();

        assertReadsOnly(statistics, 2L, 1L);
        assertThat(statistics.getQueryExecutionCount()).isEqualTo(1L);
        assertThat(result).isEqualTo(expected);
        assertThat(result.freshnessResult().status()).isEqualTo(freshnessStatus);
        assertThat(result.freshnessResult().analysisResult().restrictionScreeningResult().status()).isEqualTo(restrictionStatus);
        assertThat(result.freshnessResult().request()).isSameAs(request);
        assertThat(master(result.freshnessResult().analysisResult())).isSameAs(master);
        assertThat(result.freshnessResult().analysisResult().restrictionScreeningResult().marketWarningObservation()).isSameAs(warning);
        verify(precheck).precheck(id, master, warning, request);
        verifyNoMoreInteractions(precheck);
        Arrays.fill(result.freshnessResult().analysisResult().basicInfoAnalysis().response().content(), (byte) 0);
        entityManager.flush();
        entityManager.clear();

        var after = repository.findById(id).orElseThrow();
        assertThat(after.toRawResponse()).isEqualTo(responseBefore);
        assertThat(after.getRecordedAt()).isEqualTo(recordedBefore);
        assertThat(after.getContentSha256()).isEqualTo(hashBefore);
        assertThat(after.getContentLength()).isEqualTo(lengthBefore);
        assertThat(repository.count()).isEqualTo(1L);
        assertThat(context.getBeansOfType(KisStockRestrictionPrecheckQueryService.class)).isEmpty();
        assertThat(context.getBeansOfType(KisStockRestrictionPrecheckService.class)).isEmpty();
        assertThat(context.getBeansOfType(KisStockBasicInfoProvider.class)).isEmpty();
        assertThat(context.getBeansOfType(KisStockBasicInfoClient.class)).isEmpty();
        assertThat(context.getBeansOfType(KisTokenProvider.class)).isEmpty();
    }

    @Test
    void missingObservationReturnsEmptyAfterOnlyIdQueryWithoutPrechecking() {
        var input = input();
        var precheck = mock(KisStockRestrictionPrecheckService.class);
        var query = new KisStockRestrictionPrecheckQueryService(store, precheck);
        var statistics = resetStatistics();

        assertThat(query.precheckLatest(symbol(input), master(input), input.restrictionScreeningResult().marketWarningObservation(), request())).isEmpty();

        assertReadsOnly(statistics, 1L, 0L);
        verifyNoInteractions(precheck);
        assertThat(repository.count()).isZero();
    }

    @Test
    void anotherSymbolsObservationDoesNotReplaceMissingRequestedSymbol() {
        var input = input();
        var other = freshness(KOSPI, NO_EXCLUSION_SIGNAL_OBSERVED, FRESH).analysisResult();
        store.save(other.basicInfoAnalysis().response());
        var precheck = mock(KisStockRestrictionPrecheckService.class);
        var query = new KisStockRestrictionPrecheckQueryService(store, precheck);
        var statistics = resetStatistics();

        assertThat(query.precheckLatest(symbol(input), master(input), input.restrictionScreeningResult().marketWarningObservation(), request())).isEmpty();

        assertReadsOnly(statistics, 1L, 0L);
        verifyNoInteractions(precheck);
        assertThat(repository.count()).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"response", "recorded", "both"})
    void excludesFutureReceivedOrRecordedRowBeforePrechecking(String futureField) {
        var input = input();
        Long expectedId = store.save(input.basicInfoAnalysis().response());
        var future = EVALUATED_AT.truncatedTo(ChronoUnit.MICROS).plus(1, ChronoUnit.MICROS);
        var received = futureField.equals("recorded") ? EVALUATED_AT.minusSeconds(2) : future;
        var recorded = futureField.equals("response") ? EVALUATED_AT.minusSeconds(1) : future;
        var futureResponse = withResponseTimes(input.basicInfoAnalysis().response(), EVALUATED_AT.minusSeconds(10), received);
        Long futureId = saveAt(futureResponse, recorded);
        var precheck = spy(actualService());
        var statistics = resetStatistics();

        var result = check(input, precheck).orElseThrow();

        assertReadsOnly(statistics, 2L, 1L);
        assertThat(result.freshnessResult().analysisResult().basicInfoAnalysis().observationId()).isEqualTo(expectedId).isNotEqualTo(futureId);
        assertThat(result.freshnessResult().status()).isEqualTo(FRESH);
        verify(precheck).precheck(expectedId, master(input), input.restrictionScreeningResult().marketWarningObservation(), request());
        verifyNoMoreInteractions(precheck);
        assertThat(repository.count()).isEqualTo(2L);
    }

    @Test
    void lateArrivalOfOlderRequestDoesNotReplaceNewerRequestInPrecheck() {
        var input = input();
        var response = input.basicInfoAnalysis().response();
        Long expectedId = saveAt(response, EVALUATED_AT.minusSeconds(3));
        Long lateId = saveAt(withResponseTimes(response, response.requestStartedAt().minusSeconds(20),
                response.responseReceivedAt().plusSeconds(100)), EVALUATED_AT.minusSeconds(1));
        var precheck = spy(actualService());
        var statistics = resetStatistics();

        var result = check(input, precheck).orElseThrow();

        assertReadsOnly(statistics, 2L, 1L);
        assertThat(lateId).isGreaterThan(expectedId);
        assertThat(result.freshnessResult().analysisResult().basicInfoAnalysis().observationId()).isEqualTo(expectedId);
        verify(precheck).precheck(expectedId, master(input), input.restrictionScreeningResult().marketWarningObservation(), request());
        verifyNoMoreInteractions(precheck);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "{\"rt_cd\":\"1\",\"msg1\":\"Synthetic failure\"}"})
    void latestParsingOrBusinessFailureStopsWithoutUsingOlderSuccess(String failedContent) {
        var input = input();
        store.save(input.basicInfoAnalysis().response());
        byte[] bytes = failedContent.getBytes(StandardCharsets.UTF_8);
        var response = new KisStockBasicInfoRawResponse(symbol(input), EVALUATED_AT.minusSeconds(10), EVALUATED_AT.minusSeconds(8), 200, bytes);
        Long failedId = store.save(response);
        var freshnessPolicy = spy(new KisStockRestrictionFreshnessPolicy());
        var precheckPolicy = spy(new KisStockRestrictionPrecheckPolicy());
        var precheck = spy(actualService(freshnessPolicy, precheckPolicy));
        var statistics = resetStatistics();

        assertThatThrownBy(() -> check(input, precheck)).isExactlyInstanceOf(IllegalArgumentException.class);

        assertReadsOnly(statistics, 2L, 1L);
        verify(precheck).precheck(failedId, master(input), input.restrictionScreeningResult().marketWarningObservation(), request());
        verifyNoMoreInteractions(precheck);
        verifyNoInteractions(freshnessPolicy, precheckPolicy);
        assertThat(store.findById(failedId).orElseThrow().content()).isEqualTo(bytes);
        assertThat(repository.count()).isEqualTo(2L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"content_sha256", "http_status"})
    void latestCorruptRowStopsWithoutFallbackRepairOrPolicyEvaluation(String corruption) {
        var input = input();
        store.save(input.basicInfoAnalysis().response());
        Long corruptId = store.save(withResponseTimes(input.basicInfoAnalysis().response(), EVALUATED_AT.minusSeconds(10), EVALUATED_AT.minusSeconds(8)));
        if (corruption.equals("content_sha256")) {
            jdbcTemplate.update("UPDATE kis_stock_basic_info_observation SET content_sha256 = ? WHERE id = ?", "0".repeat(64), corruptId);
        } else {
            jdbcTemplate.update("UPDATE kis_stock_basic_info_observation SET http_status = ? WHERE id = ?", 201, corruptId);
        }
        entityManager.clear();
        var before = repository.findById(corruptId).orElseThrow();
        byte[] bytesBefore = before.getRawContent();
        var hashBefore = before.getContentSha256();
        int statusBefore = before.getHttpStatus();
        var recordedBefore = before.getRecordedAt();
        var freshnessPolicy = spy(new KisStockRestrictionFreshnessPolicy());
        var precheckPolicy = spy(new KisStockRestrictionPrecheckPolicy());
        var precheck = spy(actualService(freshnessPolicy, precheckPolicy));
        var statistics = resetStatistics();

        assertThatThrownBy(() -> check(input, precheck)).isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageEndingWith("id=" + corruptId).hasNoCause();

        assertReadsOnly(statistics, 2L, 1L);
        verify(precheck).precheck(corruptId, master(input), input.restrictionScreeningResult().marketWarningObservation(), request());
        verifyNoMoreInteractions(precheck);
        verifyNoInteractions(freshnessPolicy, precheckPolicy);
        entityManager.flush();
        entityManager.clear();
        var after = repository.findById(corruptId).orElseThrow();
        assertThat(after.getRawContent()).isEqualTo(bytesBefore);
        assertThat(after.getContentSha256()).isEqualTo(hashBefore);
        assertThat(after.getHttpStatus()).isEqualTo(statusBefore);
        assertThat(after.getRecordedAt()).isEqualTo(recordedBefore);
        assertThat(repository.count()).isEqualTo(2L);
    }

    private Optional<KisStockRestrictionPrecheckResult> check(
            KisStockRestrictionAnalysisResult input, KisStockRestrictionPrecheckService precheck
    ) {
        return new KisStockRestrictionPrecheckQueryService(store, precheck).precheckLatest(
                symbol(input), master(input), input.restrictionScreeningResult().marketWarningObservation(), request());
    }

    private Long saveAt(KisStockBasicInfoRawResponse response, Instant recordedAt) {
        return new KisStockBasicInfoObservationStore(repository, Clock.fixed(recordedAt, ZoneOffset.UTC)).save(response);
    }

    private Statistics resetStatistics() {
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManager.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        return statistics;
    }

    private static void assertReadsOnly(Statistics statistics, long statements, long entities) {
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(statements);
        assertThat(statistics.getEntityLoadCount()).isEqualTo(entities);
        assertThat(statistics.getEntityInsertCount()).isZero();
        assertThat(statistics.getEntityUpdateCount()).isZero();
        assertThat(statistics.getEntityDeleteCount()).isZero();
    }

    private KisStockRestrictionPrecheckService actualService() {
        return actualService(new KisStockRestrictionFreshnessPolicy(), new KisStockRestrictionPrecheckPolicy());
    }

    private KisStockRestrictionPrecheckService actualService(KisStockRestrictionFreshnessPolicy freshnessPolicy,
                                                            KisStockRestrictionPrecheckPolicy precheckPolicy) {
        return new KisStockRestrictionPrecheckService(
                new KisStockRestrictionAnalysisService(service(store), new KisStockRestrictionScreeningPolicy()), freshnessPolicy, precheckPolicy);
    }

    private static KisStockRestrictionAnalysisResult input() {
        return freshness(KOSDAQ, NO_EXCLUSION_SIGNAL_OBSERVED, FRESH).analysisResult();
    }

    private static String symbol(KisStockRestrictionAnalysisResult input) {
        return input.basicInfoAnalysis().response().requestedSymbol();
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
            return Clock.fixed(EVALUATED_AT.minusSeconds(1), ZoneOffset.UTC);
        }
    }
}
