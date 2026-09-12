package com.stock.harness.tool.validation;

import com.stock.harness.tool.HarnessToolRequest;
import com.stock.harness.tool.HarnessToolType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessToolRequestValidatorTest {
    private final HarnessToolRequestValidator validator = new HarnessToolRequestValidator();

    @Test
    void allowsRequestWithToolType() {
        HarnessToolRequestValidationResult result = validator.validate(
                new HarnessToolRequest(HarnessToolType.GET_PORTFOLIO)
        );

        assertThat(result.status()).isEqualTo(HarnessToolRequestValidationStatus.VALID);
        assertThat(result.reasonCode()).isEqualTo(
                HarnessToolRequestValidationReasonCode.TOOL_REQUEST_VALID
        );
    }

    @Test
    void rejectsMissingRequest() {
        HarnessToolRequestValidationResult result = validator.validate(null);

        assertThat(result.status()).isEqualTo(HarnessToolRequestValidationStatus.INVALID);
        assertThat(result.reasonCode()).isEqualTo(
                HarnessToolRequestValidationReasonCode.REQUEST_MISSING
        );
    }

    @Test
    void rejectsMissingToolType() {
        HarnessToolRequestValidationResult result = validator.validate(
                new HarnessToolRequest(null)
        );

        assertThat(result.status()).isEqualTo(HarnessToolRequestValidationStatus.INVALID);
        assertThat(result.reasonCode()).isEqualTo(
                HarnessToolRequestValidationReasonCode.TOOL_TYPE_MISSING
        );
    }
}
