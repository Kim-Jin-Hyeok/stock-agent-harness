package com.stock.harness.agent.validation;

import com.stock.agent.AgentNextAction;
import com.stock.agent.AgentNextActionType;
import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.harness.tool.HarnessToolRequest;
import com.stock.harness.tool.HarnessToolType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessAgentActionValidatorTest {
    private final HarnessAgentActionValidator validator = new HarnessAgentActionValidator();

    @Test
    void allowsToolRequestAction() {
        HarnessAgentActionValidationResult result = validator.validate(
                AgentNextAction.requestTool(toolRequest())
        );

        assertThat(result.status()).isEqualTo(HarnessAgentActionValidationStatus.VALID);
        assertThat(result.reasonCode()).isEqualTo(
                HarnessAgentActionValidationReasonCode.AGENT_ACTION_VALID
        );
    }

    @Test
    void allowsFinalDecisionAction() {
        HarnessAgentActionValidationResult result = validator.validate(
                AgentNextAction.finalDecision(investmentDecision())
        );

        assertThat(result.status()).isEqualTo(HarnessAgentActionValidationStatus.VALID);
        assertThat(result.reasonCode()).isEqualTo(
                HarnessAgentActionValidationReasonCode.AGENT_ACTION_VALID
        );
    }

    @Test
    void rejectsMissingAction() {
        HarnessAgentActionValidationResult result = validator.validate(null);

        assertThat(result.status()).isEqualTo(HarnessAgentActionValidationStatus.INVALID);
        assertThat(result.reasonCode()).isEqualTo(
                HarnessAgentActionValidationReasonCode.ACTION_MISSING
        );
    }

    @Test
    void rejectsMissingActionType() {
        AgentNextAction action = new AgentNextAction(null, null, null);

        HarnessAgentActionValidationResult result = validator.validate(action);

        assertThat(result.status()).isEqualTo(HarnessAgentActionValidationStatus.INVALID);
        assertThat(result.reasonCode()).isEqualTo(
                HarnessAgentActionValidationReasonCode.ACTION_TYPE_MISSING
        );
    }

    @Test
    void rejectsToolRequestActionWithoutToolRequest() {
        AgentNextAction action = new AgentNextAction(
                AgentNextActionType.REQUEST_TOOL,
                null,
                null
        );

        HarnessAgentActionValidationResult result = validator.validate(action);

        assertThat(result.status()).isEqualTo(HarnessAgentActionValidationStatus.INVALID);
        assertThat(result.reasonCode()).isEqualTo(
                HarnessAgentActionValidationReasonCode.TOOL_REQUEST_MISSING
        );
    }

    @Test
    void rejectsToolRequestActionWithInvestmentDecision() {
        AgentNextAction action = new AgentNextAction(
                AgentNextActionType.REQUEST_TOOL,
                toolRequest(),
                investmentDecision()
        );

        HarnessAgentActionValidationResult result = validator.validate(action);

        assertThat(result.status()).isEqualTo(HarnessAgentActionValidationStatus.INVALID);
        assertThat(result.reasonCode()).isEqualTo(
                HarnessAgentActionValidationReasonCode.UNEXPECTED_INVESTMENT_DECISION
        );
    }

    @Test
    void rejectsFinalDecisionActionWithoutInvestmentDecision() {
        AgentNextAction action = new AgentNextAction(
                AgentNextActionType.FINAL_DECISION,
                null,
                null
        );

        HarnessAgentActionValidationResult result = validator.validate(action);

        assertThat(result.status()).isEqualTo(HarnessAgentActionValidationStatus.INVALID);
        assertThat(result.reasonCode()).isEqualTo(
                HarnessAgentActionValidationReasonCode.INVESTMENT_DECISION_MISSING
        );
    }

    @Test
    void rejectsFinalDecisionActionWithToolRequest() {
        AgentNextAction action = new AgentNextAction(
                AgentNextActionType.FINAL_DECISION,
                toolRequest(),
                investmentDecision()
        );

        HarnessAgentActionValidationResult result = validator.validate(action);

        assertThat(result.status()).isEqualTo(HarnessAgentActionValidationStatus.INVALID);
        assertThat(result.reasonCode()).isEqualTo(
                HarnessAgentActionValidationReasonCode.UNEXPECTED_TOOL_REQUEST
        );
    }

    private HarnessToolRequest toolRequest() {
        return new HarnessToolRequest(HarnessToolType.GET_PORTFOLIO);
    }

    private InvestmentDecision investmentDecision() {
        return new InvestmentDecision(
                InvestmentAction.HOLD,
                null,
                null,
                null,
                "Test hold decision."
        );
    }
}
