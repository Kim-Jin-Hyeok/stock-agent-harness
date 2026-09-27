package com.stock.agent.evidence.movingaverage;

import com.stock.agent.evidence.movingaverage.order.MovingAverageOrderDecisionEvidence;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisResult;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisStatus;

import java.util.Objects;

public record MovingAverageDecisionEvidence(
        MovingAverageAnalysisResult analysis,
        Long currentPriceKrw,
        CurrentPriceLookupSource currentPriceSource,
        MovingAverageOrderDecisionEvidence orderDecisionEvidence
) {
    public MovingAverageDecisionEvidence {
        Objects.requireNonNull(analysis, "analysis must not be null.");
        if (analysis.status() == MovingAverageAnalysisStatus.ANALYZED) {
            if (currentPriceKrw == null || currentPriceKrw <= 0) {
                throw new IllegalArgumentException(
                        "Analyzed evidence requires a positive currentPriceKrw."
                );
            }
            Objects.requireNonNull(
                    currentPriceSource,
                    "Analyzed evidence requires currentPriceSource."
            );
            Objects.requireNonNull(
                    orderDecisionEvidence,
                    "Analyzed evidence requires orderDecisionEvidence."
            );
        } else if (currentPriceKrw != null
                || currentPriceSource != null
                || orderDecisionEvidence != null) {
            throw new IllegalArgumentException(
                    "Insufficient data evidence must not contain decision "
                            + "details."
            );
        }
    }

    public static MovingAverageDecisionEvidence analyzed(
            MovingAverageAnalysisResult analysis,
            long currentPriceKrw,
            CurrentPriceLookupSource currentPriceSource,
            MovingAverageOrderDecisionEvidence orderDecisionEvidence
    ) {
        return new MovingAverageDecisionEvidence(
                analysis,
                currentPriceKrw,
                currentPriceSource,
                orderDecisionEvidence
        );
    }

    public static MovingAverageDecisionEvidence insufficientData(
            MovingAverageAnalysisResult analysis
    ) {
        return new MovingAverageDecisionEvidence(
                analysis,
                null,
                null,
                null
        );
    }
}
