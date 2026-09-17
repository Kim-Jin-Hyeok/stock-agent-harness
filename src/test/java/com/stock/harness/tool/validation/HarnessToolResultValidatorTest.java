package com.stock.harness.tool.validation;

import com.stock.harness.tool.HarnessToolExecutionReasonCode;
import com.stock.harness.tool.HarnessToolExecutionResult;
import com.stock.harness.tool.HarnessToolExecutionStatus;
import com.stock.harness.tool.HarnessToolOutput;
import com.stock.harness.tool.HarnessToolRequest;
import com.stock.harness.tool.HarnessToolType;
import com.stock.market.MarketSnapshot;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.validation.CurrentPriceFreshnessPolicy;
import com.stock.market.price.validation.CurrentPriceFreshnessProperties;
import com.stock.portfolio.PortfolioSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessToolResultValidatorTest {
    private static final Instant OBSERVED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration MAX_AGE = Duration.ofMinutes(1);
    private final HarnessToolResultValidator validator = validatorAt(OBSERVED_AT);

    @Test
    void allowsValidPortfolioResult() {
        HarnessToolResultValidationResult result = validator.validate(
                request(HarnessToolType.GET_PORTFOLIO),
                HarnessToolExecutionResult.executed(
                        HarnessToolOutput.portfolio(portfolioSnapshot())
                )
        );

        assertThat(result.status()).isEqualTo(HarnessToolResultValidationStatus.VALID);
        assertThat(result.reasonCode()).isEqualTo(
                HarnessToolResultValidationReasonCode.TOOL_RESULT_VALID
        );
    }

    @Test
    void allowsValidMarketResult() {
        HarnessToolResultValidationResult result = validator.validate(
                request(HarnessToolType.GET_MARKET),
                HarnessToolExecutionResult.executed(
                        HarnessToolOutput.market(marketSnapshot())
                )
        );

        assertThat(result.status()).isEqualTo(HarnessToolResultValidationStatus.VALID);
        assertThat(result.reasonCode()).isEqualTo(
                HarnessToolResultValidationReasonCode.TOOL_RESULT_VALID
        );
    }

    @Test
    void allowsValidCurrentPriceResult() {
        HarnessToolResultValidationResult result = validator.validate(
                HarnessToolRequest.currentPrice("005930"),
                HarnessToolExecutionResult.executed(
                        HarnessToolOutput.currentPrice(
                                currentPrice("005930", 70_000L)
                        )
                )
        );

        assertThat(result.status()).isEqualTo(HarnessToolResultValidationStatus.VALID);
        assertThat(result.reasonCode()).isEqualTo(
                HarnessToolResultValidationReasonCode.TOOL_RESULT_VALID
        );
    }

    @Test
    void rejectsMissingCurrentPricePayload() {
        HarnessToolOutput output = new HarnessToolOutput(
                HarnessToolType.GET_CURRENT_PRICE,
                null,
                null,
                null
        );

        HarnessToolResultValidationResult result = validator.validate(
                HarnessToolRequest.currentPrice("005930"),
                executedResult(HarnessToolType.GET_CURRENT_PRICE, output)
        );

        assertThat(result.status()).isEqualTo(HarnessToolResultValidationStatus.INVALID);
        assertThat(result.reasonCode()).isEqualTo(
                HarnessToolResultValidationReasonCode.OUTPUT_PAYLOAD_MISSING
        );
    }

    @Test
    void rejectsZeroCurrentPrice() {
        HarnessToolResultValidationResult result = validator.validate(
                HarnessToolRequest.currentPrice("005930"),
                HarnessToolExecutionResult.executed(
                        HarnessToolOutput.currentPrice(
                                currentPrice("005930", 0L)
                        )
                )
        );

        assertThat(result.status()).isEqualTo(HarnessToolResultValidationStatus.INVALID);
        assertThat(result.reasonCode()).isEqualTo(
                HarnessToolResultValidationReasonCode.OUTPUT_PRICE_INVALID
        );
        assertThat(result.reason()).contains("actual=0");
    }

    @Test
    void rejectsNegativeCurrentPrice() {
        HarnessToolResultValidationResult result = validator.validate(
                HarnessToolRequest.currentPrice("005930"),
                HarnessToolExecutionResult.executed(
                        HarnessToolOutput.currentPrice(
                                currentPrice("005930", -1L)
                        )
                )
        );

        assertThat(result.status()).isEqualTo(HarnessToolResultValidationStatus.INVALID);
        assertThat(result.reasonCode()).isEqualTo(
                HarnessToolResultValidationReasonCode.OUTPUT_PRICE_INVALID
        );
        assertThat(result.reason()).contains("actual=-1");
    }

    @Test
    void rejectsMismatchedSymbolBeforeInvalidCurrentPrice() {
        HarnessToolResultValidationResult result = validator.validate(
                HarnessToolRequest.currentPrice("005930"),
                HarnessToolExecutionResult.executed(
                        HarnessToolOutput.currentPrice(
                                currentPrice("000660", 0L)
                        )
                )
        );

        assertThat(result.status()).isEqualTo(HarnessToolResultValidationStatus.INVALID);
        assertThat(result.reasonCode()).isEqualTo(
                HarnessToolResultValidationReasonCode.OUTPUT_SYMBOL_MISMATCH
        );
    }

    @Test
    void rejectsCurrentPriceResultWithDifferentSymbol() {
        HarnessToolResultValidationResult result = validator.validate(
                HarnessToolRequest.currentPrice("005930"),
                HarnessToolExecutionResult.executed(
                        HarnessToolOutput.currentPrice(
                                currentPrice("000660", 200_000L)
                        )
                )
        );

        assertThat(result.status()).isEqualTo(HarnessToolResultValidationStatus.INVALID);
        assertThat(result.reasonCode()).isEqualTo(
                HarnessToolResultValidationReasonCode.OUTPUT_SYMBOL_MISMATCH
        );
        assertThat(result.reason())
                .contains("requested=005930")
                .contains("actual=000660");
    }

    @Test
    void rejectsCurrentPriceWithoutObservedAt() {
        HarnessToolResultValidationResult result = validator.validate(
                HarnessToolRequest.currentPrice("005930"),
                HarnessToolExecutionResult.executed(
                        HarnessToolOutput.currentPrice(
                                new CurrentPriceSnapshot("005930", 70_000L, null)
                        )
                )
        );

        assertThat(result.status()).isEqualTo(HarnessToolResultValidationStatus.INVALID);
        assertThat(result.reasonCode()).isEqualTo(
                HarnessToolResultValidationReasonCode.OUTPUT_OBSERVED_AT_MISSING
        );
        assertThat(result.reason()).isEqualTo("Current price observedAt is missing.");
    }

    @Test
    void rejectsStaleCurrentPriceAtMaxAgeBoundary() {
        HarnessToolResultValidator stalePriceValidator = validatorAt(
                OBSERVED_AT.plus(MAX_AGE)
        );

        HarnessToolResultValidationResult result = stalePriceValidator.validate(
                HarnessToolRequest.currentPrice("005930"),
                HarnessToolExecutionResult.executed(
                        HarnessToolOutput.currentPrice(
                                currentPrice("005930", 70_000L)
                        )
                )
        );

        assertThat(result.status()).isEqualTo(HarnessToolResultValidationStatus.INVALID);
        assertThat(result.reasonCode()).isEqualTo(
                HarnessToolResultValidationReasonCode.OUTPUT_CURRENT_PRICE_STALE
        );
        assertThat(result.reason()).contains("observedAt=" + OBSERVED_AT);
    }

    @Test
    void rejectsMismatchedResultType() {
        HarnessToolExecutionResult executionResult = executedResult(
                HarnessToolType.GET_MARKET,
                HarnessToolOutput.portfolio(portfolioSnapshot())
        );

        HarnessToolResultValidationResult result = validator.validate(
                request(HarnessToolType.GET_PORTFOLIO),
                executionResult
        );

        assertThat(result.status()).isEqualTo(HarnessToolResultValidationStatus.INVALID);
        assertThat(result.reasonCode()).isEqualTo(
                HarnessToolResultValidationReasonCode.RESULT_TYPE_MISMATCH
        );
    }

    @Test
    void rejectsMissingOutput() {
        HarnessToolExecutionResult executionResult = executedResult(
                HarnessToolType.GET_PORTFOLIO,
                null
        );

        HarnessToolResultValidationResult result = validator.validate(
                request(HarnessToolType.GET_PORTFOLIO),
                executionResult
        );

        assertThat(result.status()).isEqualTo(HarnessToolResultValidationStatus.INVALID);
        assertThat(result.reasonCode()).isEqualTo(
                HarnessToolResultValidationReasonCode.OUTPUT_MISSING
        );
    }

    @Test
    void rejectsMismatchedOutputType() {
        HarnessToolExecutionResult executionResult = executedResult(
                HarnessToolType.GET_PORTFOLIO,
                HarnessToolOutput.market(marketSnapshot())
        );

        HarnessToolResultValidationResult result = validator.validate(
                request(HarnessToolType.GET_PORTFOLIO),
                executionResult
        );

        assertThat(result.status()).isEqualTo(HarnessToolResultValidationStatus.INVALID);
        assertThat(result.reasonCode()).isEqualTo(
                HarnessToolResultValidationReasonCode.OUTPUT_TYPE_MISMATCH
        );
    }

    @Test
    void rejectsMissingExpectedPayload() {
        HarnessToolOutput output = new HarnessToolOutput(
                HarnessToolType.GET_PORTFOLIO,
                null,
                null
        );
        HarnessToolExecutionResult executionResult = executedResult(
                HarnessToolType.GET_PORTFOLIO,
                output
        );

        HarnessToolResultValidationResult result = validator.validate(
                request(HarnessToolType.GET_PORTFOLIO),
                executionResult
        );

        assertThat(result.status()).isEqualTo(HarnessToolResultValidationStatus.INVALID);
        assertThat(result.reasonCode()).isEqualTo(
                HarnessToolResultValidationReasonCode.OUTPUT_PAYLOAD_MISSING
        );
    }

    private HarnessToolExecutionResult executedResult(
            HarnessToolType type,
            HarnessToolOutput output
    ) {
        return new HarnessToolExecutionResult(
                HarnessToolExecutionStatus.EXECUTED,
                type,
                HarnessToolExecutionReasonCode.TOOL_EXECUTED,
                "Harness tool execution completed.",
                output
        );
    }

    private HarnessToolRequest request(HarnessToolType type) {
        return new HarnessToolRequest(type);
    }

    private HarnessToolResultValidator validatorAt(Instant now) {
        return new HarnessToolResultValidator(new CurrentPriceFreshnessPolicy(
                new CurrentPriceFreshnessProperties(MAX_AGE),
                Clock.fixed(now, ZoneOffset.UTC)
        ));
    }

    private CurrentPriceSnapshot currentPrice(String symbol, long priceKrw) {
        return new CurrentPriceSnapshot(symbol, priceKrw, OBSERVED_AT);
    }

    private PortfolioSnapshot portfolioSnapshot() {
        return new PortfolioSnapshot(
                5_000_000L,
                10_000_000L,
                List.of()
        );
    }

    private MarketSnapshot marketSnapshot() {
        return new MarketSnapshot(
                "KR",
                true,
                "Korean market is open."
        );
    }
}
