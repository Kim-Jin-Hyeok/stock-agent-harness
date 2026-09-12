package com.stock.harness.agent.validation;

import com.stock.agent.AgentNextActionType;

public record HarnessAgentActionValidationResult(
        HarnessAgentActionValidationStatus status,
        AgentNextActionType type,
        HarnessAgentActionValidationReasonCode reasonCode,
        String reason
) {
    public static HarnessAgentActionValidationResult valid(AgentNextActionType type) {
        return new HarnessAgentActionValidationResult(
                HarnessAgentActionValidationStatus.VALID,
                type,
                HarnessAgentActionValidationReasonCode.AGENT_ACTION_VALID,
                "Harness agent action is valid."
        );
    }

    public static HarnessAgentActionValidationResult invalid(
            AgentNextActionType type,
            HarnessAgentActionValidationReasonCode reasonCode,
            String reason
    ) {
        return new HarnessAgentActionValidationResult(
                HarnessAgentActionValidationStatus.INVALID,
                type,
                reasonCode,
                reason
        );
    }
}
