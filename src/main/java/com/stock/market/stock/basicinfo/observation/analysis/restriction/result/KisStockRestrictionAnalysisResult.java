package com.stock.market.stock.basicinfo.observation.analysis.restriction.result;

import com.stock.market.stock.basicinfo.observation.analysis.result.KisStockBasicInfoAnalysisResult;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningResult;

import java.util.Objects;

public record KisStockRestrictionAnalysisResult(
        KisStockBasicInfoAnalysisResult basicInfoAnalysis,
        KisStockRestrictionScreeningResult restrictionScreeningResult
) {
    public KisStockRestrictionAnalysisResult {
        Objects.requireNonNull(basicInfoAnalysis, "basicInfoAnalysis must not be null.");
        Objects.requireNonNull(restrictionScreeningResult, "restrictionScreeningResult must not be null.");
        if (!basicInfoAnalysis.screeningResult().equals(restrictionScreeningResult.basicInfoScreening())) {
            throw new IllegalArgumentException("Restriction screening must use the complete screening result of the basic info analysis.");
        }
    }

    @Override
    public String toString() {
        return "KisStockRestrictionAnalysisResult[observationId=" + basicInfoAnalysis.observationId()
                + ", requestedSymbol=" + basicInfoAnalysis.response().requestedSymbol()
                + ", inputSha256=" + basicInfoAnalysis.screeningResult().observation().typeResolution().matchingResult().apiInput().inputSha256()
                + ", status=" + restrictionScreeningResult.status()
                + ", reasonCodes=" + restrictionScreeningResult.reasonCodes() + "]";
    }
}
