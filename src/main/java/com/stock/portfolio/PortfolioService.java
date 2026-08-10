package com.stock.portfolio;

import org.springframework.stereotype.Component;

@Component
public class PortfolioService {

    public PortfolioSnapshot getCurrentSnapshot() {
        return new PortfolioSnapshot(
                10_000_000L,
                10_000_000L
        );
    }
}
