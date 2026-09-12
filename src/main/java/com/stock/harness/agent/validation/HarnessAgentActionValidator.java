package com.stock.harness.agent.validation;

import com.stock.agent.AgentNextAction;
import com.stock.agent.AgentNextActionType;
import org.springframework.stereotype.Component;

@Component
public class HarnessAgentActionValidator {

    public HarnessAgentActionValidationResult validate(AgentNextAction action) {
        if (action == null) {
            return HarnessAgentActionValidationResult.invalid(
                    null,
                    HarnessAgentActionValidationReasonCode.ACTION_MISSING,
                    "Agent action is missing."
            );
        }

        AgentNextActionType type = action.type();

        if (type == null) {
            return HarnessAgentActionValidationResult.invalid(
                    null,
                    HarnessAgentActionValidationReasonCode.ACTION_TYPE_MISSING,
                    "Agent action type is missing."
            );
        }

        return switch (type) {
            case REQUEST_TOOL -> validateToolRequestAction(action);
            case FINAL_DECISION -> validateFinalDecisionAction(action);
        };
    }

    private HarnessAgentActionValidationResult validateToolRequestAction(AgentNextAction action) {
        if (action.toolRequest() == null) {
            return HarnessAgentActionValidationResult.invalid(
                    action.type(),
                    HarnessAgentActionValidationReasonCode.TOOL_REQUEST_MISSING,
                    "Tool request is missing for REQUEST_TOOL action."
            );
        }

        if (action.investmentDecision() != null) {
            return HarnessAgentActionValidationResult.invalid(
                    action.type(),
                    HarnessAgentActionValidationReasonCode.UNEXPECTED_INVESTMENT_DECISION,
                    "Investment decision must be absent for REQUEST_TOOL action."
            );
        }

        return HarnessAgentActionValidationResult.valid(action.type());
    }

    private HarnessAgentActionValidationResult validateFinalDecisionAction(AgentNextAction action) {
        if (action.investmentDecision() == null) {
            return HarnessAgentActionValidationResult.invalid(
                    action.type(),
                    HarnessAgentActionValidationReasonCode.INVESTMENT_DECISION_MISSING,
                    "Investment decision is missing for FINAL_DECISION action."
            );
        }

        if (action.toolRequest() != null) {
            return HarnessAgentActionValidationResult.invalid(
                    action.type(),
                    HarnessAgentActionValidationReasonCode.UNEXPECTED_TOOL_REQUEST,
                    "Tool request must be absent for FINAL_DECISION action."
            );
        }

        return HarnessAgentActionValidationResult.valid(action.type());
    }
}
