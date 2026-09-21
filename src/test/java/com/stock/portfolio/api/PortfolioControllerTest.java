package com.stock.portfolio.api;

import com.stock.portfolio.PortfolioPosition;
import com.stock.portfolio.PortfolioService;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortfolioController.class)
class PortfolioControllerTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity("DAY_TRADING_V1", 1, InvestmentHorizon.DAY_TRADING);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PortfolioService portfolioService;

    @Test
    void getPortfolioReturnsCurrentSnapshot() throws Exception {
        when(portfolioService.getCurrentSnapshot(STRATEGY_IDENTITY))
                .thenReturn(portfolioSnapshot());

        mockMvc.perform(get("/api/portfolio")
                        .param("strategyId", "DAY_TRADING_V1")
                        .param("strategyVersion", "1")
                        .param("horizon", "DAY_TRADING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashAmountKrw").value(9_300_000L))
                .andExpect(jsonPath("$.totalAssetAmountKrw").value(10_000_000L))
                .andExpect(jsonPath("$.positions[0].symbol").value("005930"));

        verify(portfolioService).getCurrentSnapshot(STRATEGY_IDENTITY);
    }

    @Test
    void resetPortfolioDelegatesToPortfolioService() throws Exception {
        mockMvc.perform(post("/api/portfolio/reset")
                        .param("strategyId", "DAY_TRADING_V1")
                        .param("strategyVersion", "1")
                        .param("horizon", "DAY_TRADING"))
                .andExpect(status().isOk());

        verify(portfolioService).reset(STRATEGY_IDENTITY);
    }

    private PortfolioSnapshot portfolioSnapshot() {
        return new PortfolioSnapshot(
                9_300_000L,
                10_000_000L,
                List.of(portfolioPosition())
        );
    }

    private PortfolioPosition portfolioPosition() {
        return new PortfolioPosition(
                "005930",
                10L,
                70_000L,
                700_000L
        );
    }
}
