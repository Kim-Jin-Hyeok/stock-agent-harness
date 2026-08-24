package com.stock.harness;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.harness.persistence.HarnessMarketSnapshot;
import com.stock.harness.persistence.HarnessPortfolioPositionSnapshot;
import com.stock.harness.persistence.HarnessPortfolioSnapshot;
import com.stock.harness.persistence.HarnessRunEntity;
import com.stock.harness.persistence.HarnessRunRepository;
import com.stock.harness.persistence.HarnessRunSnapshotJsonConverter;
import com.stock.harness.persistence.HarnessStepEntity;
import com.stock.harness.persistence.HarnessStepRepository;
import com.stock.market.MarketSnapshot;
import com.stock.portfolio.PortfolioPosition;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.risk.RiskCheckResult;
import com.stock.risk.RiskCheckStatus;
import com.stock.risk.RiskReasonCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HarnessRunHistoryServiceTest {
    private final HarnessRunSnapshotJsonConverter harnessRunSnapshotJsonConverter =
            mock(HarnessRunSnapshotJsonConverter.class);
    private final HarnessRunRepository harnessRunRepository = mock(HarnessRunRepository.class);
    private final HarnessStepRepository harnessStepRepository = mock(HarnessStepRepository.class);
    private final HarnessRunHistoryService harnessRunHistoryService = new HarnessRunHistoryService(
            harnessRunSnapshotJsonConverter,
            harnessRunRepository,
            harnessStepRepository
    );

    @Test
    void recordStoresCompleteRunResult() {
        harnessRunHistoryService.record(completedRun("run-1"));

        assertThat(harnessRunHistoryService.getRunById("run-1")).isPresent();

        verify(harnessRunRepository).save(any());
        verify(harnessStepRepository).saveAll(any());
    }

    @Test
    void recordStoresFailRunResult() {
        harnessRunHistoryService.record(failedRun("run-1"));

        Optional<HarnessRunResult> result = harnessRunHistoryService.getRunById("run-1");

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(HarnessRunStatus.FAILED);

        verify(harnessRunRepository).save(any());
        verify(harnessStepRepository).saveAll(any());
    }

    @Test
    void recordStoresDecisionAndRiskCheckSnapshotJson() {
        HarnessRunResult result = completedBuyRun("run-1");
        when(harnessRunSnapshotJsonConverter.toDecisionJson(any()))
                .thenReturn("{\"action\":\"BUY\"}");
        when(harnessRunSnapshotJsonConverter.toRiskCheckJson(any()))
                .thenReturn("{\"status\":\"APPROVED\"}");

        harnessRunHistoryService.record(result);

        ArgumentCaptor<HarnessRunEntity> captor = ArgumentCaptor.forClass(HarnessRunEntity.class);
        verify(harnessRunRepository).save(captor.capture());

        HarnessRunEntity savedEntity = captor.getValue();
        assertThat(savedEntity.getDecisionSnapshotJson()).isEqualTo("{\"action\":\"BUY\"}");
        assertThat(savedEntity.getRiskCheckSnapshotJson()).isEqualTo("{\"status\":\"APPROVED\"}");

        verify(harnessRunSnapshotJsonConverter).toDecisionJson(any());
        verify(harnessRunSnapshotJsonConverter).toRiskCheckJson(any());
    }

    @Test
    void recordStoresPortfolioSnapshotJson() {
        HarnessRunResult result = completedBuyRunWithPortfolio("run-1");
        when(harnessRunSnapshotJsonConverter.toDecisionJson(any()))
                .thenReturn("{\"action\":\"BUY\"}");
        when(harnessRunSnapshotJsonConverter.toRiskCheckJson(any()))
                .thenReturn("{\"status\":\"APPROVED\"}");
        when(harnessRunSnapshotJsonConverter.toPortfolioJson(any()))
                .thenReturn(portfolioSnapshotJson());

        harnessRunHistoryService.record(result);

        ArgumentCaptor<HarnessRunEntity> captor = ArgumentCaptor.forClass(HarnessRunEntity.class);
        verify(harnessRunRepository).save(captor.capture());

        HarnessRunEntity savedEntity = captor.getValue();
        assertThat(savedEntity.getPortfolioSnapshotJson()).isEqualTo(portfolioSnapshotJson());

        verify(harnessRunSnapshotJsonConverter).toPortfolioJson(any());
    }

    @Test
    void recordStoresMarketSnapshotJson() {
        HarnessRunResult result = completedBuyRunWithMarket("run-1");
        when(harnessRunSnapshotJsonConverter.toDecisionJson(any()))
                .thenReturn("{\"action\":\"BUY\"}");
        when(harnessRunSnapshotJsonConverter.toRiskCheckJson(any()))
                .thenReturn("{\"status\":\"APPROVED\"}");
        when(harnessRunSnapshotJsonConverter.toMarketJson(any()))
                .thenReturn(marketSnapshotJson());

        harnessRunHistoryService.record(result);

        ArgumentCaptor<HarnessRunEntity> captor = ArgumentCaptor.forClass(HarnessRunEntity.class);
        verify(harnessRunRepository).save(captor.capture());

        HarnessRunEntity savedEntity = captor.getValue();
        assertThat(savedEntity.getMarketSnapshotJson()).isEqualTo(marketSnapshotJson());

        verify(harnessRunSnapshotJsonConverter).toMarketJson(any());
    }

    @Test
    void getRunDetailReturnsPortfolioSnapshot() {
        String runId = "run-1";
        HarnessRunEntity entity = HarnessRunEntity.of(
                runId,
                HarnessRunStatus.COMPLETED,
                startedAt(),
                finishedAt(),
                null,
                null,
                portfolioSnapshotJson(),
                null
        );
        when(harnessRunRepository.findByRunId(runId))
                .thenReturn(Optional.of(entity));
        when(harnessStepRepository.findAllByRunIdOrderByStepOrderAsc(runId))
                .thenReturn(List.of(completedStepEntity(runId, 1)));
        when(harnessRunSnapshotJsonConverter.toPortfolioSnapshot(portfolioSnapshotJson()))
                .thenReturn(harnessPortfolioSnapshot());

        Optional<HarnessRunDetail> result = harnessRunHistoryService.getRunDetail(
                runId,
                List.of()
        );

        assertThat(result).isPresent();
        assertThat(result.get().portfolioSnapshot()).isEqualTo(harnessPortfolioSnapshot());
        assertThat(result.get().portfolioSnapshot().positions()).hasSize(2);

        verify(harnessRunSnapshotJsonConverter).toPortfolioSnapshot(portfolioSnapshotJson());
    }

    @Test
    void getRunDetailReturnsMarketSnapshot() {
        String runId = "run-1";
        HarnessRunEntity entity = HarnessRunEntity.of(
                runId,
                HarnessRunStatus.COMPLETED,
                startedAt(),
                finishedAt(),
                null,
                null,
                null,
                marketSnapshotJson()
        );
        when(harnessRunRepository.findByRunId(runId))
                .thenReturn(Optional.of(entity));
        when(harnessStepRepository.findAllByRunIdOrderByStepOrderAsc(runId))
                .thenReturn(List.of(completedStepEntity(runId, 1)));
        when(harnessRunSnapshotJsonConverter.toMarketSnapshot(marketSnapshotJson()))
                .thenReturn(harnessMarketSnapshot());

        Optional<HarnessRunDetail> result = harnessRunHistoryService.getRunDetail(
                runId,
                List.of()
        );

        assertThat(result).isPresent();
        assertThat(result.get().marketSnapshot()).isEqualTo(harnessMarketSnapshot());
        assertThat(result.get().marketSnapshot().market()).isEqualTo("KR");
        assertThat(result.get().marketSnapshot().marketOpen()).isTrue();

        verify(harnessRunSnapshotJsonConverter).toMarketSnapshot(marketSnapshotJson());
    }

    @Test
    void getRunByIdReturnsMatchingRun() {
        harnessRunHistoryService.record(completedRun("run-1"));
        harnessRunHistoryService.record(completedRun("run-2"));

        Optional<HarnessRunResult> result = harnessRunHistoryService.getRunById("run-2");

        assertThat(result.isPresent()).isTrue();
        assertThat(result.get().runId()).isEqualTo("run-2");
    }

    @Test
    void getRunSummariesReturnsRunMetadata() {
        when(harnessRunRepository.findAllByOrderByStartedAtDesc())
                .thenReturn(List.of(completedRunEntity("run-1")));

        List<HarnessRunSummary> summaries = harnessRunHistoryService.getRunSummaries();
        verify(harnessRunRepository).findAllByOrderByStartedAtDesc();
        assertThat(summaries).hasSize(1);

        HarnessRunSummary summary = summaries.getFirst();
        assertThat(summary.runId()).isEqualTo("run-1");
        assertThat(summary.status()).isEqualTo(HarnessRunStatus.COMPLETED);
        assertThat(summary.startedAt()).isEqualTo(startedAt());
        assertThat(summary.finishedAt()).isEqualTo(finishedAt());
    }

    @Test
    void clearRemovesRunHistory() {
        harnessRunHistoryService.record(failedRun("run-1"));

        harnessRunHistoryService.clear();

        assertThat(harnessRunHistoryService.getRunById("run-1")).isEmpty();
        verify(harnessRunRepository).deleteAll();
        verify(harnessStepRepository).deleteAll();
    }

    @Test
    void getStepsByRunIdReturnsStepResults() {
        String runId = "run-1";
        when(harnessStepRepository.findAllByRunIdOrderByStepOrderAsc(runId))
                .thenReturn(List.of(completedStepEntity(runId, 1)));

        List<HarnessStepResult> steps = harnessRunHistoryService.getStepsByRunId(runId);

        assertThat(steps).hasSize(1);
        assertThat(steps.getFirst().type()).isEqualTo(completedStep().type());
        assertThat(steps.getFirst().status()).isEqualTo(completedStep().status());
        assertThat(steps.getFirst().message()).isEqualTo(completedStep().message());

        verify(harnessStepRepository).findAllByRunIdOrderByStepOrderAsc(runId);
    }

    @Test
    void existsRunFoundRunId() {
        String runId = "run-1";
        when(harnessRunRepository.findByRunId(runId))
                .thenReturn(Optional.of(completedRunEntity(runId)));

        boolean result = harnessRunHistoryService.existsRun(runId);

        assertThat(result).isTrue();

        verify(harnessRunRepository).findByRunId(runId);
    }

    @Test
    void existsRunNotFoundRunId() {
        String runId = "missing-id";
        when(harnessRunRepository.findByRunId(runId))
                .thenReturn(Optional.empty());

        boolean result = harnessRunHistoryService.existsRun(runId);

        assertThat(result).isFalse();

        verify(harnessRunRepository).findByRunId(runId);
    }

    private HarnessRunResult completedRun(String runId) {
        return HarnessRunResult.of(
                runId,
                HarnessRunStatus.COMPLETED,
                startedAt(),
                finishedAt(),
                List.of(completedStep()),
                null,
                null,
                null,
                null,
                null
        );
    }

    private HarnessRunResult failedRun(String runId) {
        return HarnessRunResult.failed(
                runId,
                startedAt(),
                finishedAt(),
                List.of(failedStep())
        );
    }

    private HarnessRunEntity completedRunEntity(String runId) {
        return HarnessRunEntity.of(
                runId,
                HarnessRunStatus.COMPLETED,
                startedAt(),
                finishedAt(),
                null,
                null,
                null,
                null
        );
    }

    private HarnessRunResult completedBuyRun(String runId) {
        return HarnessRunResult.of(
                runId,
                HarnessRunStatus.COMPLETED,
                startedAt(),
                finishedAt(),
                List.of(completedStep()),
                buyDecision(),
                approvedRiskCheckResult(),
                null,
                null,
                null
        );
    }

    private HarnessRunResult completedBuyRunWithPortfolio(String runId) {
        return HarnessRunResult.of(
                runId,
                HarnessRunStatus.COMPLETED,
                startedAt(),
                finishedAt(),
                List.of(completedStep()),
                buyDecision(),
                approvedRiskCheckResult(),
                null,
                portfolioSnapshot(),
                null
        );
    }

    private HarnessRunResult completedBuyRunWithMarket(String runId) {
        return HarnessRunResult.of(
                runId,
                HarnessRunStatus.COMPLETED,
                startedAt(),
                finishedAt(),
                List.of(completedStep()),
                buyDecision(),
                approvedRiskCheckResult(),
                null,
                null,
                marketSnapshot()
        );
    }

    private InvestmentDecision buyDecision() {
        return new InvestmentDecision(
                InvestmentAction.BUY,
                "005930",
                10L,
                70_000L,
                "Buy Samsung Electronics."
        );
    }

    private RiskCheckResult approvedRiskCheckResult() {
        return new RiskCheckResult(
                RiskCheckStatus.APPROVED,
                InvestmentAction.BUY,
                "005930",
                10L,
                70_000L,
                700_000L,
                RiskReasonCode.RISK_APPROVED,
                "Risk check approved."
        );
    }

    private PortfolioSnapshot portfolioSnapshot() {
        return new PortfolioSnapshot(
                8_700_000L,
                10_000_000L,
                List.of(
                        samsungPosition(),
                        skHynixPosition()
                )
        );
    }

    private PortfolioPosition samsungPosition() {
        return new PortfolioPosition(
                "005930",
                10L,
                70_000L,
                700_000L
        );
    }

    private PortfolioPosition skHynixPosition() {
        return new PortfolioPosition(
                "000660",
                5L,
                120_000L,
                600_000L
        );
    }

    private String portfolioSnapshotJson() {
        return "{\"cashAmountKrw\":8700000,\"totalAssetAmountKrw\":10000000}";
    }

    private MarketSnapshot marketSnapshot() {
        return new MarketSnapshot(
                "KR",
                true,
                "Korean market is open."
        );
    }

    private String marketSnapshotJson() {
        return "{\"market\":\"KR\",\"marketOpen\":true,\"description\":\"Korean market is open.\"}";
    }

    private HarnessPortfolioSnapshot harnessPortfolioSnapshot() {
        return new HarnessPortfolioSnapshot(
                8_700_000L,
                10_000_000L,
                List.of(
                        samsungPositionSnapshot(),
                        skHynixPositionSnapshot()
                )
        );
    }

    private HarnessPortfolioPositionSnapshot samsungPositionSnapshot() {
        return new HarnessPortfolioPositionSnapshot(
                "005930",
                10L,
                70_000L,
                700_000L
        );
    }

    private HarnessPortfolioPositionSnapshot skHynixPositionSnapshot() {
        return new HarnessPortfolioPositionSnapshot(
                "000660",
                5L,
                120_000L,
                600_000L
        );
    }

    private HarnessMarketSnapshot harnessMarketSnapshot() {
        return new HarnessMarketSnapshot(
                "KR",
                true,
                "Korean market is open."
        );
    }

    private HarnessStepEntity completedStepEntity(String runId, Integer stepOrder) {
        return HarnessStepEntity.of(
                runId,
                stepOrder,
                HarnessStepType.CHECK_STEP_LIMIT,
                HarnessStepStatus.COMPLETED,
                "Test completed step."
        );
    }

    private LocalDateTime startedAt() {
        return LocalDateTime.of(2026, 1, 1, 9, 0);
    }

    private LocalDateTime finishedAt() {
        return startedAt().plusSeconds(1);
    }

    private HarnessStepResult completedStep() {
        return new HarnessStepResult(
                HarnessStepType.CHECK_STEP_LIMIT,
                HarnessStepStatus.COMPLETED,
                "Test completed step."
        );
    }

    private HarnessStepResult failedStep() {
        return new HarnessStepResult(
                HarnessStepType.RUN_FAILED,
                HarnessStepStatus.FAILED,
                "Test failed step."
        );
    }
}
