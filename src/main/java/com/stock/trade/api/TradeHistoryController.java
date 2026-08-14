package com.stock.trade.api;

import com.stock.trade.TradeHistoryService;
import com.stock.trade.TradeRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/trades")
public class TradeHistoryController {
    private final TradeHistoryService tradeHistoryService;

    @GetMapping
    public List<TradeRecord> getTrades(@RequestParam(required = false) String runId) {
        if (runId == null || runId.isBlank()) {
            return tradeHistoryService.getRecords();
        }

        return tradeHistoryService.getRecordsByRunId(runId);
    }
}
