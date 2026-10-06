package com.stock.market.stock.master.matching.kiskrx.result;

public enum KisKrxStockIdentityMatchReasonCode {
    EXACT_IDENTITY_MATCH,
    KRX_IDENTIFIER_BLANK,
    KRX_IDENTIFIER_DUPLICATED,
    REQUEST_MARKET_MISMATCH,
    STANDARD_CODE_NOT_FOUND,
    SYMBOL_ONLY_MATCH,
    SYMBOL_MISMATCH,
    MARKET_MISMATCH;
}
