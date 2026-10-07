package com.stock.market.stock.basicinfo.observation.analysis.result;

import com.stock.market.stock.basicinfo.provider.kis.dto.KisStockBasicInfoRawResponse;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningResult;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public record KisStockBasicInfoAnalysisResult(
        Long observationId,
        KisStockBasicInfoRawResponse response,
        KisStockBasicInfoRestrictionScreeningResult screeningResult
) {
    public KisStockBasicInfoAnalysisResult {
        Objects.requireNonNull(observationId, "observationId must not be null.");
        if (observationId <= 0) {
            throw new IllegalArgumentException("observationId must be positive.");
        }
        Objects.requireNonNull(response, "response must not be null.");
        Objects.requireNonNull(screeningResult, "screeningResult must not be null.");
        var matching = screeningResult.observation().typeResolution().matchingResult();
        if (!response.requestedSymbol().equals(matching.requestedSymbol())) {
            throw new IllegalArgumentException("Analysis requested symbol must match the stored response.");
        }
        if (!sha256(response.content()).equals(matching.apiInput().inputSha256())) {
            throw new IllegalArgumentException("Analysis input SHA-256 must match the stored response content.");
        }
    }

    @Override
    public String toString() {
        return "KisStockBasicInfoAnalysisResult[observationId=" + observationId
                + ", requestedSymbol=" + response.requestedSymbol()
                + ", inputSha256=" + screeningResult.observation().typeResolution().matchingResult().apiInput().inputSha256()
                + ", status=" + screeningResult.status() + ", reasonCodes=" + screeningResult.reasonCodes() + "]";
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 must be available.", failure);
        }
    }
}
