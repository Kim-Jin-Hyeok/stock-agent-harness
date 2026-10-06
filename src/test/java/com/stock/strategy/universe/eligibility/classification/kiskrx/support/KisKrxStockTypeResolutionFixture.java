package com.stock.strategy.universe.eligibility.classification.kiskrx.support;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stock.market.stock.master.collection.result.StockMasterCollectionResult;
import com.stock.market.stock.master.matching.kiskrx.KisKrxStockIdentityMatchingPolicy;
import com.stock.market.stock.master.matching.kiskrx.result.KisKrxStockIdentityMatchingResult;
import com.stock.market.stock.master.parsing.result.StockMasterBatchParseResult;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.KisStockMasterParser;
import com.stock.market.stock.master.provider.kis.parsing.record.KisStockMasterRawRecord;
import com.stock.market.stock.master.provider.kis.parsing.result.KisStockMasterParseResult;
import com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture;
import com.stock.market.stock.master.provider.krx.parsing.KrxStockBasicInfoParser;
import com.stock.market.stock.master.provider.krx.parsing.record.KrxStockBasicInfoRawRecord;
import com.stock.market.stock.master.provider.krx.parsing.result.KrxStockBasicInfoParseResult;
import com.stock.strategy.universe.eligibility.classification.kis.KisStockMasterTypeClassificationPolicy;
import com.stock.strategy.universe.eligibility.classification.kis.result.KisStockMasterTypeClassificationReasonCode;
import com.stock.strategy.universe.eligibility.classification.kis.result.KisStockMasterTypeClassificationResult;
import com.stock.strategy.universe.eligibility.classification.krx.KrxStockBasicInfoTypeClassificationPolicy;
import com.stock.strategy.universe.eligibility.classification.krx.result.KrxStockBasicInfoTypeClassificationReasonCode;
import com.stock.strategy.universe.eligibility.classification.krx.result.KrxStockBasicInfoTypeClassificationResult;
import com.stock.strategy.universe.eligibility.classification.kiskrx.KisKrxStockTypeResolutionPolicy;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.market.stock.master.provider.krx.parsing.support.KrxStockBasicInfoParsingFixture.content;
import static com.stock.market.stock.master.provider.krx.parsing.support.KrxStockBasicInfoParsingFixture.row;

public final class KisKrxStockTypeResolutionFixture {
    private KisKrxStockTypeResolutionFixture() {
    }

    public static KisKrxStockTypeResolutionPolicy policy() {
        return new KisKrxStockTypeResolutionPolicy(new KisStockMasterTypeClassificationPolicy(),
                new KrxStockBasicInfoTypeClassificationPolicy());
    }

    public static StockMasterBatchParseResult batch(byte[]... kospiRows) {
        byte[] kospiBytes = KisStockMasterParsingFixture.content(kospiRows);
        byte[] kosdaqBytes = KisStockMasterParsingFixture.content(
                KisStockMasterParsingFixture.row(KOSDAQ, "0001A0", "KR70001A0001", "ALPHA"));
        var parser = new KisStockMasterParser();
        var kospi = parser.parse(KOSPI, kospiBytes);
        var kosdaq = parser.parse(KOSDAQ, kosdaqBytes);
        var at = Instant.parse("2026-10-05T09:32:36Z");
        // Synthetic collection metadata connects real parser hashes without filesystem or HTTP access.
        var collection = new StockMasterCollectionResult(1, UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "CURRENT_OBSERVATION", at, at, List.of(observation(kospi, kospiBytes.length, at),
                observation(kosdaq, kosdaqBytes.length, at)));
        return new StockMasterBatchParseResult(collection, List.of(kospi, kosdaq));
    }

    public static StockMasterBatchParseResult baseBatch() {
        return batch(KisStockMasterParsingFixture.row(KOSPI), etfRow());
    }

    public static byte[] etfRow() {
        byte[] bytes = KisStockMasterParsingFixture.row(KOSPI, "111111", "KR7111111111", "SYNTHETIC ETF");
        KisStockMasterParsingFixture.put(bytes, 61, "EF");
        KisStockMasterParsingFixture.put(bytes, 83, "2");
        return bytes;
    }

    public static EnumMap<KisStockMasterMarket, KrxStockBasicInfoParseResult> inputs(ObjectNode... kospiRows) {
        var inputs = new EnumMap<KisStockMasterMarket, KrxStockBasicInfoParseResult>(KisStockMasterMarket.class);
        inputs.put(KOSPI, parsed(kospiRows));
        inputs.put(KOSDAQ, parsed());
        return inputs;
    }

    public static KisKrxStockIdentityMatchingResult matching(StockMasterBatchParseResult batch, ObjectNode... kospiRows) {
        return new KisKrxStockIdentityMatchingPolicy().match(batch, inputs(kospiRows));
    }

    public static ObjectNode stock(KisStockMasterMarket market, String symbol, String standardCode) {
        return row().put("MKT_TP_NM", market.name()).put("ISU_SRT_CD", symbol).put("ISU_CD", standardCode)
                .put("SECUGRP_NM", "\uc8fc\uad8c").put("KIND_STKCERT_TP_NM", "\ubcf4\ud1b5\uc8fc");
    }

    public static KrxStockBasicInfoParseResult parsed(ObjectNode... rows) {
        return new KrxStockBasicInfoParser().parse(content(rows));
    }

    public static KisStockMasterTypeClassificationResult syntheticKis(
            KisStockMasterMarket market, KisStockMasterRawRecord raw, StockSecurityType type
    ) {
        return new KisStockMasterTypeClassificationResult(market, raw, type,
                type == null ? KisStockMasterTypeClassificationReasonCode.ETP_VALUE_UNVERIFIED
                        : KisStockMasterTypeClassificationReasonCode.TYPE_INTERPRETED,
                "SYNTHETIC_KIS_TYPE_V1", "0".repeat(40));
    }

    public static KrxStockBasicInfoTypeClassificationResult syntheticKrx(KrxStockBasicInfoRawRecord raw, StockSecurityType type) {
        return new KrxStockBasicInfoTypeClassificationResult(raw, type,
                type == null ? KrxStockBasicInfoTypeClassificationReasonCode.STOCK_KIND_VALUE_UNVERIFIED
                        : KrxStockBasicInfoTypeClassificationReasonCode.TYPE_INTERPRETED,
                "SYNTHETIC_KRX_TYPE_V1", "SYNTHETIC_RULE_EVIDENCE", "0".repeat(64));
    }

    private static StockMasterCollectionResult.FileObservation observation(KisStockMasterParseResult parsed, int bytes, Instant at) {
        var market = parsed.market();
        return new StockMasterCollectionResult.FileObservation(market, market.sourceUri(), at, at,
                market.name() + "/" + market.fileName() + ".zip", 1, "f".repeat(64),
                market.name() + "/" + market.fileName(), bytes, parsed.inputSha256());
    }
}
