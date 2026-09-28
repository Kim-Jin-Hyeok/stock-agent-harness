package com.stock.agent.decision.movingaverage.provider.ai.prompt;

import com.stock.agent.decision.movingaverage.provider.ai.request.MovingAverageOrderDecisionAiRequest;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class MovingAverageOrderDecisionAiPromptFactory {
    private static final String SYSTEM_INSTRUCTION = """
            You decide whether to execute an order and, if so, its quantity
            for a moving-average investment strategy.

            Follow these rules:
            1. Use only the data provided in the request.
            2. Treat signalAction, symbol, and currentPriceKrw as fixed values.
            3. EXECUTE_ORDER is allowed only when signalAction is BUY or SELL
               and maxAllowedQuantity is at least 1.
            4. An EXECUTE_ORDER quantity must be an integer between 1 and
               maxAllowedQuantity, inclusive.
            5. Return HOLD with a null quantity when signalAction is HOLD or
               maxAllowedQuantity is 0.
            6. You may return HOLD when the provided evidence does not justify
               executing an order.
            7. Do not infer or invent market data that is not in the request.
            8. Produce exactly one structured result with the fields intent,
               quantity, and reason.
            9. intent must be either EXECUTE_ORDER or HOLD. Keep reason concise
               and grounded in the request data.
            """.strip();

    public MovingAverageOrderDecisionAiPrompt create(
            MovingAverageOrderDecisionAiRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null.");
        return new MovingAverageOrderDecisionAiPrompt(
                SYSTEM_INSTRUCTION,
                request
        );
    }
}
