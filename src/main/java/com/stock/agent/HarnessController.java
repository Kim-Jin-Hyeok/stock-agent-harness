package com.stock.agent;

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
    public void run() {
        investmentHarness.run();
    }
}
