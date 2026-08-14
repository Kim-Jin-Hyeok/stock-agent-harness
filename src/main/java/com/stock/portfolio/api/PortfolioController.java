package com.stock.portfolio.api;

import com.stock.portfolio.PortfolioService;
import com.stock.portfolio.PortfolioSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/portfolio")
public class PortfolioController {
    private final PortfolioService portfolioService;

    @GetMapping
    public PortfolioSnapshot getPortfolio() {
        return portfolioService.getCurrentSnapshot();
    }

    @PostMapping("/reset")
    public void reset() {
        portfolioService.reset();
    }
}
