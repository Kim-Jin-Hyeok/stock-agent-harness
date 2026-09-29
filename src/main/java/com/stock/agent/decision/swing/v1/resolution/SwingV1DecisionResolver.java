package com.stock.agent.decision.swing.v1.resolution;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.agent.decision.swing.v1.quantity.result.SwingV1OrderQuantityResult;
import com.stock.agent.decision.swing.v1.result.SwingV1ActionPolicyResult;
import com.stock.agent.evidence.swing.v1.SwingV1DecisionEvidence;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class SwingV1DecisionResolver {

    public InvestmentDecision resolve(SwingV1DecisionEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence must not be null.");

        SwingV1ActionPolicyResult actionResult =
                evidence.actionPolicyResult();
        SwingV1OrderQuantityResult quantityResult =
                evidence.orderQuantityResult();

        if (actionResult.action() == InvestmentAction.HOLD) {
            return holdDecision(actionResult.reason(), evidence);
        }
        if (!quantityResult.canOrder()) {
            return holdDecision(quantityResult.reason(), evidence);
        }

        return new InvestmentDecision(
                actionResult.action(),
                evidence.analysis().symbol(),
                quantityResult.finalQuantity(),
                evidence.currentPrice().priceKrw(),
                actionResult.reason(),
                evidence
        );
    }

    private InvestmentDecision holdDecision(
            String reason,
            SwingV1DecisionEvidence evidence
    ) {
        return new InvestmentDecision(
                InvestmentAction.HOLD,
                null,
                null,
                null,
                reason,
                evidence
        );
    }
}
