package com.stock.portfolio;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PortfolioService {

    public PortfolioSnapshot getCurrentSnapshot() {
        return new PortfolioSnapshot(
                10_000_000L,
                10_000_000L,
                List.of()
        );
    }
}
