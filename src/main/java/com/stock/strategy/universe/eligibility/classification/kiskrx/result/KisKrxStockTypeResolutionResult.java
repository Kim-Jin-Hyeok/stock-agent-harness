package com.stock.strategy.universe.eligibility.classification.kiskrx.result;

import com.stock.market.stock.master.matching.kiskrx.result.KisKrxStockIdentityMatchReasonCode;
import com.stock.market.stock.master.matching.kiskrx.result.KisKrxStockIdentityMatchResult;
import com.stock.strategy.universe.eligibility.classification.kis.result.KisStockMasterTypeClassificationResult;
import com.stock.strategy.universe.eligibility.classification.krx.result.KrxStockBasicInfoTypeClassificationResult;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;

import java.util.Objects;

public record KisKrxStockTypeResolutionResult(
        KisStockMasterTypeClassificationResult kisClassification,
        KisKrxStockIdentityMatchResult identityMatch,
        KrxStockBasicInfoTypeClassificationResult krxClassification,
        StockSecurityType referenceSecurityType,
        KisKrxStockTypeResolutionReasonCode reasonCode
) {
    public KisKrxStockTypeResolutionResult {
        Objects.requireNonNull(kisClassification, "kisClassification must not be null.");
        Objects.requireNonNull(reasonCode, "reasonCode must not be null.");
        if ((identityMatch != null) != (krxClassification != null)) {
            throw new IllegalArgumentException("KRX classification requires its exact identity match and vice versa.");
        }
        if (identityMatch != null && (identityMatch.reasonCode() != KisKrxStockIdentityMatchReasonCode.EXACT_IDENTITY_MATCH
                || identityMatch.requestMarket() != kisClassification.market()
                || !kisClassification.rawRecord().equals(identityMatch.matchedKisRecord())
                || !krxClassification.rawRecord().equals(identityMatch.krxRecord()))) {
            throw new IllegalArgumentException("Type classifications must preserve the exact matched input rows and market.");
        }
        var kisType = kisClassification.securityType();
        var krxType = krxClassification == null ? null : krxClassification.securityType();
        boolean consistent = switch (reasonCode) {
            case KIS_TYPE_ONLY -> kisType != null && krxType == null && referenceSecurityType == kisType;
            case KRX_TYPE_SUPPLEMENTED -> kisType == null && krxType != null && referenceSecurityType == krxType;
            case SOURCES_AGREE -> kisType != null && kisType == krxType && referenceSecurityType == kisType;
            case TYPE_CONFLICT -> kisType != null && krxType != null && kisType != krxType && referenceSecurityType == null;
            case TYPE_UNVERIFIED -> kisType == null && krxType == null && referenceSecurityType == null;
        };
        if (!consistent) {
            throw new IllegalArgumentException("Reference type and resolution reason must agree with both source classifications.");
        }
    }
}
