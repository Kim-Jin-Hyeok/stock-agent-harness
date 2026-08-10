package com.stock.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InvestmentHarness {

    public void run() {
        log.info("Investment Harness started.");

        log.info("1. Load portfolio");
        log.info("2. Load market");
        log.info("3. Run investment agent");
        log.info("4. Validate decision");
        log.info("5. Execute virtual trade");

        log.info("Investment Harness finished.");
    }
}
