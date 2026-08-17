package com.stock.portfolio.api;

import com.stock.portfolio.PortfolioPosition;
import com.stock.portfolio.PortfolioService;
import com.stock.portfolio.PortfolioSnapshot;
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

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PortfolioService portfolioService;

    @Test
    void getPortfolioReturnsCurrentSnapshot() throws Exception {
        when(portfolioService.getCurrentSnapshot())
                .thenReturn(portfolioSnapshot());

        mockMvc.perform(get("/api/portfolio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashAmountKrw").value(9_300_000L))
                .andExpect(jsonPath("$.totalAssetAmountKrw").value(10_000_000L))
                .andExpect(jsonPath("$.positions[0].symbol").value("005930"));

        verify(portfolioService).getCurrentSnapshot();
    }

    @Test
    void resetPortfolioDelegatesToPortfolioService() throws Exception {
        mockMvc.perform(post("/api/portfolio/reset"))
                .andExpect(status().isOk());

        verify(portfolioService).reset();
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
