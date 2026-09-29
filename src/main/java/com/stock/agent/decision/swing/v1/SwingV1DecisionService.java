package com.stock.agent.decision.swing.v1;

import com.stock.agent.InvestmentDecision;
import com.stock.agent.decision.swing.v1.policy.SwingV1ActionPolicy;
import com.stock.agent.decision.swing.v1.quantity.policy.SwingV1OrderQuantityPolicy;
import com.stock.agent.decision.swing.v1.quantity.result.SwingV1OrderQuantityResult;
import com.stock.agent.decision.swing.v1.resolution.SwingV1DecisionResolver;
import com.stock.agent.decision.swing.v1.result.SwingV1ActionPolicyResult;
import com.stock.agent.evidence.swing.v1.SwingV1DecisionEvidence;
import com.stock.portfolio.valuation.PortfolioValuationService;
import com.stock.portfolio.valuation.PortfolioValuationSnapshot;
import com.stock.strategy.analysis.swing.SwingTechnicalAnalysisResult;
import com.stock.strategy.analysis.swing.SwingTechnicalAnalysisService;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class SwingV1DecisionService {
    private final SwingTechnicalAnalysisService analysisService;
    private final SwingV1ActionPolicy actionPolicy;
    private final PortfolioValuationService portfolioValuationService;
    private final SwingV1OrderQuantityPolicy orderQuantityPolicy;
    private final SwingV1DecisionResolver decisionResolver;

    public SwingV1DecisionService(
            SwingTechnicalAnalysisService analysisService,
            SwingV1ActionPolicy actionPolicy,
            PortfolioValuationService portfolioValuationService,
            SwingV1OrderQuantityPolicy orderQuantityPolicy,
            SwingV1DecisionResolver decisionResolver
    ) {
        this.analysisService = Objects.requireNonNull(
                analysisService,
                "analysisService must not be null."
        );
        this.actionPolicy = Objects.requireNonNull(
                actionPolicy,
                "actionPolicy must not be null."
        );
        this.portfolioValuationService = Objects.requireNonNull(
                portfolioValuationService,
                "portfolioValuationService must not be null."
        );
        this.orderQuantityPolicy = Objects.requireNonNull(
                orderQuantityPolicy,
                "orderQuantityPolicy must not be null."
        );
        this.decisionResolver = Objects.requireNonNull(
                decisionResolver,
                "decisionResolver must not be null."
        );
    }

    public InvestmentDecision decide(SwingV1DecisionInput input) {
        Objects.requireNonNull(input, "input must not be null.");

        SwingTechnicalAnalysisResult analysis = analysisService.analyze(
                input.strategyIdentity(),
                input.dailyPriceHistory()
        );
        PortfolioValuationSnapshot portfolioValuation =
                portfolioValuationService.evaluate(
                        input.portfolioSnapshot(),
                        input.currentPrices(),
                        input.evaluatedAt()
                );
        SwingV1ActionPolicyResult actionResult = actionPolicy.decide(
                analysis,
                input.portfolioSnapshot(),
                input.candidateCurrentPrice()
        );
        SwingV1OrderQuantityResult quantityResult =
                orderQuantityPolicy.calculate(
                        actionResult,
                        analysis,
                        input.candidateCurrentPrice(),
                        portfolioValuation
                );
        SwingV1DecisionEvidence evidence = new SwingV1DecisionEvidence(
                analysis,
                input.candidateCurrentPrice(),
                input.candidateCurrentPriceSource(),
                portfolioValuation,
                actionResult,
                quantityResult
        );
        return decisionResolver.resolve(evidence);
    }
}
