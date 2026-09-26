package com.stock.agent.decision.movingaverage.validation;

public enum MovingAverageOrderProposalValidationReasonCode {
    ORDER_PROPOSAL_VALID,
    PROPOSAL_MISSING,
    SIGNAL_ACTION_DOES_NOT_ALLOW_ORDER,
    ORDER_CAPACITY_UNAVAILABLE,
    QUANTITY_EXCEEDS_ALLOWED_CAPACITY
}
