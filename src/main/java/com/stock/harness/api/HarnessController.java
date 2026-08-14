package com.stock.harness.api;

import com.stock.harness.HarnessRunResult;
import com.stock.harness.InvestmentHarness;
import com.stock.trade.TradeHistoryService;
import com.stock.trade.TradeRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/harness")
public class HarnessController {
    private final InvestmentHarness investmentHarness;
    private final TradeHistoryService tradeHistoryService;

    @PostMapping("/run")
    public HarnessRunResponse run() {
        HarnessRunResult result = investmentHarness.run();

        List<TradeRecord> tradeRecords = tradeHistoryService.getRecordsByRunId(result.runId());

        return HarnessRunResponse.from(result, tradeRecords);
    }
}
