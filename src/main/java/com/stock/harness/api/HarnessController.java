package com.stock.harness.api;

import com.stock.harness.HarnessRunResult;
import com.stock.harness.InvestmentHarness;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/harness")
public class HarnessController {

    private final InvestmentHarness investmentHarness;

    @PostMapping("/run")
    public HarnessRunResponse run() {
        HarnessRunResult result = investmentHarness.run();

        return HarnessRunResponse.from(result);
    }
}
