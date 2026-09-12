package com.stock.harness.tool.validation;

import com.stock.harness.tool.HarnessToolExecutionReasonCode;
import com.stock.harness.tool.HarnessToolExecutionResult;
import com.stock.harness.tool.HarnessToolExecutionStatus;
import com.stock.harness.tool.HarnessToolOutput;
import com.stock.harness.tool.HarnessToolRequest;
import com.stock.harness.tool.HarnessToolType;
import com.stock.market.MarketSnapshot;
import com.stock.portfolio.PortfolioSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessToolResultValidatorTest {
    private final HarnessToolResultValidator validator = new HarnessToolResultValidator();

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
