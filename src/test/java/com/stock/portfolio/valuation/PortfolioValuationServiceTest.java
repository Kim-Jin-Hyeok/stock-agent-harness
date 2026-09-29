package com.stock.portfolio.valuation;

import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.validation.CurrentPriceFreshnessPolicy;
import com.stock.market.price.validation.CurrentPriceFreshnessProperties;
import com.stock.portfolio.PortfolioPosition;
import com.stock.portfolio.PortfolioSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PortfolioValuationServiceTest {
    private static final Instant EVALUATED_AT =
            Instant.parse("2026-09-29T00:10:00Z");
    private static final Instant FRESH_PRICE_OBSERVED_AT =
            EVALUATED_AT.minusSeconds(30);

    private final PortfolioValuationService service = service();

    @Test
    void valuesEmptyPortfolioAsCashOnly() {
        PortfolioValuationSnapshot result = service.evaluate(
                new PortfolioSnapshot(
                        1_000_000L,
                        1_000_000L,
                        List.of()
                ),
                List.of(),
                EVALUATED_AT
        );

        assertThat(result.cashAmountKrw()).isEqualTo(1_000_000L);
        assertThat(result.positionEvaluationAmountKrw()).isZero();
        assertThat(result.totalAssetAmountKrw()).isEqualTo(1_000_000L);
        assertThat(result.positions()).isEmpty();
    }

    @Test
    void calculatesUnrealizedProfitWhenCurrentPriceRises() {
        PortfolioValuationSnapshot result = service.evaluate(
                portfolio(position("005930", 10L, 70_000L, 700_000L)),
                List.of(currentPrice("005930", 80_000L)),
                EVALUATED_AT
        );

        PortfolioPositionValuation position = result.positions().getFirst();
        assertThat(position.acquisitionAmountKrw()).isEqualTo(700_000L);
        assertThat(position.evaluationAmountKrw()).isEqualTo(800_000L);
        assertThat(position.unrealizedProfitLossKrw()).isEqualTo(100_000L);
        assertThat(result.totalAssetAmountKrw()).isEqualTo(1_100_000L);
    }

    @Test
    void calculatesUnrealizedLossWhenCurrentPriceFalls() {
        PortfolioValuationSnapshot result = service.evaluate(
                portfolio(position("005930", 10L, 70_000L, 700_000L)),
                List.of(currentPrice("005930", 60_000L)),
                EVALUATED_AT
        );

        PortfolioPositionValuation position = result.positions().getFirst();
        assertThat(position.evaluationAmountKrw()).isEqualTo(600_000L);
        assertThat(position.unrealizedProfitLossKrw()).isEqualTo(-100_000L);
        assertThat(result.totalAssetAmountKrw()).isEqualTo(900_000L);
    }

    @Test
    void sumsMultiplePositionEvaluations() {
        PortfolioSnapshot portfolio = new PortfolioSnapshot(
                100_000L,
                1_100_000L,
                List.of(
                        position("005930", 10L, 70_000L, 700_000L),
                        position("000660", 2L, 150_000L, 300_000L)
                )
        );

        PortfolioValuationSnapshot result = service.evaluate(
                portfolio,
                List.of(
                        currentPrice("005930", 80_000L),
                        currentPrice("000660", 160_000L)
                ),
                EVALUATED_AT
        );

        assertThat(result.positionEvaluationAmountKrw())
                .isEqualTo(1_120_000L);
        assertThat(result.totalAssetAmountKrw()).isEqualTo(1_220_000L);
        assertThat(result.positions())
                .extracting(PortfolioPositionValuation::unrealizedProfitLossKrw)
                .containsExactly(100_000L, 20_000L);
    }

    @Test
    void rejectsMissingCurrentPriceForPosition() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.evaluate(
                        portfolio(position(
                                "005930",
                                10L,
                                70_000L,
                                700_000L
                        )),
                        List.of(),
                        EVALUATED_AT
                ))
                .withMessage(
                        "Current price is required for portfolio position. "
                                + "symbol=005930"
                );
    }

    @Test
    void rejectsDuplicateCurrentPrice() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.evaluate(
                        portfolio(position(
                                "005930",
                                10L,
                                70_000L,
                                700_000L
                        )),
                        List.of(
                                currentPrice("005930", 70_000L),
                                currentPrice("005930", 71_000L)
                        ),
                        EVALUATED_AT
                ))
                .withMessage(
                        "currentPrices must not contain duplicate symbol. "
                                + "symbol=005930"
                );
    }

    @Test
    void rejectsStaleCurrentPrice() {
        CurrentPriceSnapshot stalePrice = new CurrentPriceSnapshot(
                "005930",
                70_000L,
                EVALUATED_AT.minus(Duration.ofMinutes(1))
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.evaluate(
                        portfolio(position(
                                "005930",
                                10L,
                                70_000L,
                                700_000L
                        )),
                        List.of(stalePrice),
                        EVALUATED_AT
                ))
                .withMessage(
                        "Current price is stale at portfolio evaluation. "
                                + "symbol=005930"
                );
    }

    @Test
    void rejectsCurrentPriceObservedAfterEvaluation() {
        CurrentPriceSnapshot futurePrice = new CurrentPriceSnapshot(
                "005930",
                70_000L,
                EVALUATED_AT.plusSeconds(1)
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.evaluate(
                        portfolio(position(
                                "005930",
                                10L,
                                70_000L,
                                700_000L
                        )),
                        List.of(futurePrice),
                        EVALUATED_AT
                ))
                .withMessage(
                        "currentPrice.observedAt must not be after "
                                + "evaluatedAt. symbol=005930"
                );
    }

    @Test
    void doesNotModifySourcePortfolio() {
        PortfolioSnapshot source = portfolio(position(
                "005930",
                10L,
                70_000L,
                700_000L
        ));

        service.evaluate(
                source,
                List.of(currentPrice("005930", 80_000L)),
                EVALUATED_AT
        );

        assertThat(source.cashAmountKrw()).isEqualTo(300_000L);
        assertThat(source.totalAssetAmountKrw()).isEqualTo(1_000_000L);
        assertThat(source.positions().getFirst().marketValueKrw())
                .isEqualTo(700_000L);
    }

    @Test
    void rejectsEvaluationAmountOverflow() {
        PortfolioPosition oversizedPosition = position(
                "005930",
                Long.MAX_VALUE,
                1L,
                Long.MAX_VALUE
        );

        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> service.evaluate(
                        portfolio(oversizedPosition),
                        List.of(currentPrice("005930", 2L)),
                        EVALUATED_AT
                ));
    }

    private PortfolioValuationService service() {
        return new PortfolioValuationService(
                new CurrentPriceFreshnessPolicy(
                        new CurrentPriceFreshnessProperties(
                                Duration.ofMinutes(1)
                        ),
                        Clock.fixed(EVALUATED_AT, ZoneOffset.UTC)
                )
        );
    }

    private PortfolioSnapshot portfolio(PortfolioPosition position) {
        return new PortfolioSnapshot(
                300_000L,
                1_000_000L,
                List.of(position)
        );
    }

    private PortfolioPosition position(
            String symbol,
            long quantity,
            long averagePriceKrw,
            long acquisitionAmountKrw
    ) {
        return new PortfolioPosition(
                symbol,
                quantity,
                averagePriceKrw,
                acquisitionAmountKrw
        );
    }

    private CurrentPriceSnapshot currentPrice(
            String symbol,
            long priceKrw
    ) {
        return new CurrentPriceSnapshot(
                symbol,
                priceKrw,
                FRESH_PRICE_OBSERVED_AT
        );
    }
}
