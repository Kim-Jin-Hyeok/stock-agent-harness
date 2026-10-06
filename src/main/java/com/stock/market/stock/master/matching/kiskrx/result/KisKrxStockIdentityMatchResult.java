package com.stock.market.stock.master.matching.kiskrx.result;

import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.record.KisStockMasterRawRecord;
import com.stock.market.stock.master.provider.krx.parsing.record.KrxStockBasicInfoRawRecord;

import java.util.Objects;

public record KisKrxStockIdentityMatchResult(
        KisStockMasterMarket requestMarket,
        KrxStockBasicInfoRawRecord krxRecord,
        KisStockMasterRawRecord matchedKisRecord,
        KisKrxStockIdentityMatchReasonCode reasonCode
) {
    public KisKrxStockIdentityMatchResult {
        Objects.requireNonNull(requestMarket, "requestMarket must not be null.");
        Objects.requireNonNull(krxRecord, "krxRecord must not be null.");
        Objects.requireNonNull(reasonCode, "reasonCode must not be null.");
        if ((reasonCode == KisKrxStockIdentityMatchReasonCode.EXACT_IDENTITY_MATCH) != (matchedKisRecord != null)) {
            throw new IllegalArgumentException("matchedKisRecord must be present only for EXACT_IDENTITY_MATCH.");
        }
        if (matchedKisRecord != null && (!krxRecord.standardCode().equals(matchedKisRecord.standardCode())
                || !krxRecord.symbol().equals(matchedKisRecord.symbol())
                || !requestMarket.name().equals(krxRecord.rawMarket()))) {
            throw new IllegalArgumentException("Matched identifiers and request market must agree exactly.");
        }
    }
}
