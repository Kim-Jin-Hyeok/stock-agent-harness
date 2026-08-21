package com.stock.harness.api;

import com.stock.harness.*;
import com.stock.trade.TradeHistoryService;
import com.stock.trade.TradeRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/harness")
public class HarnessController {
    private final InvestmentHarness investmentHarness;
    private final TradeHistoryService tradeHistoryService;
    private final HarnessRunHistoryService harnessRunHistoryService;
    private final HarnessStateService harnessStateService;

    @PostMapping("/run")
    public HarnessRunResponse run() {
        HarnessRunResult result = investmentHarness.run();

        List<TradeRecord> tradeRecords = tradeHistoryService.getRecordsByRunId(result.runId());

        return HarnessRunResponse.from(result, tradeRecords);
    }

    @GetMapping("/runs")
    public List<HarnessRunSummary> getRuns() {
        return harnessRunHistoryService.getRunSummaries();
    }

    @GetMapping("/runs/{runId}")
    public HarnessRunResponse getRun(@PathVariable String runId) {
        HarnessRunResult result = harnessRunHistoryService.getRunById(runId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Harness run not found. runId=" + runId
                ));

        List<TradeRecord> tradeRecords = tradeHistoryService.getRecordsByRunId(runId);

        return HarnessRunResponse.from(
                result,
                tradeRecords
        );
    }

    @GetMapping("/runs/{runId}/steps")
    public List<HarnessStepResult> getSteps(@PathVariable String runId) {
        if (!harnessRunHistoryService.existsRun(runId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Harness run not found. runId=" + runId
            );
        }

        return harnessRunHistoryService.getStepsByRunId(runId);
    }

    @PostMapping("/reset")
    public void reset() {
        harnessStateService.reset();
    }
}
