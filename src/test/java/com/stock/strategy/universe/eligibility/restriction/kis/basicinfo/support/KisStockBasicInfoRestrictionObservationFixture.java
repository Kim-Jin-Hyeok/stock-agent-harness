package com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.support;

import com.stock.market.stock.master.matching.kisbasicinfo.KisStockBasicInfoMatchingPolicy;
import com.stock.market.stock.master.matching.kisbasicinfo.support.KisStockBasicInfoMatchingFixture;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.result.KisStockBasicInfoTypeResolutionResult;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.support.KisStockBasicInfoTypeResolutionFixture;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.result.KisStockBasicInfoRestrictionObservationResult;
import com.stock.strategy.universe.eligibility.restriction.kis.result.KisStockTradingFlagStatus;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;

public final class KisStockBasicInfoRestrictionObservationFixture {
    public static final List<String> MASTER_FIELDS = List.of("suspension", "liquidation", "spac", "management", "caution");
    public static final List<String> RESULT_FIELDS = List.of("masterSuspensionStatus", "masterLiquidationStatus",
            "masterSpacStatus", "masterManagementStatus", "masterInvestmentCautionStatus",
            "basicInfoSuspensionStatus", "basicInfoManagementStatus");

    private KisStockBasicInfoRestrictionObservationFixture() {
    }

    public static KisStockBasicInfoTypeResolutionResult input(
            KisStockMasterMarket market, Map<String, String> masterFields, Map<String, String> apiFields
    ) {
        byte[] kospi = KisStockMasterParsingFixture.row(KOSPI);
        byte[] kosdaq = KisStockMasterParsingFixture.row(KOSDAQ, "0004Y0", "KR70004Y0000", "SYNTHETIC ALPHA");
        byte[] selected = market == KOSPI ? kospi : kosdaq;
        for (var entry : masterFields.entrySet()) {
            int offset = switch (entry.getKey()) {
                case "suspension" -> market == KOSPI ? 121 : 116;
                case "liquidation" -> market == KOSPI ? 122 : 117;
                case "spac" -> market == KOSPI ? 90 : 85;
                case "management" -> market == KOSPI ? 123 : 118;
                case "caution" -> {
                    if (market == KOSPI) {
                        throw new IllegalArgumentException("KOSPI has no investment caution field.");
                    }
                    yield 91;
                }
                default -> throw new IllegalArgumentException("Unknown fixture master field.");
            };
            KisStockMasterParsingFixture.put(selected, offset, entry.getValue());
        }
        var batch = KisStockBasicInfoMatchingFixture.batch(KisStockMasterParsingFixture.content(kospi),
                KisStockMasterParsingFixture.content(kosdaq));
        String symbol = market == KOSPI ? "005930" : "0004Y0";
        var output = KisStockBasicInfoMatchingFixture.fields(symbol, market == KOSPI ? "KR7005930003" : "KR70004Y0000",
                market == KOSPI ? "STK" : "KSQ").put("scty_grp_id_cd", "ST").put("stck_kind_cd", "101")
                .put("tr_stop_yn", "N").put("admn_item_yn", "N");
        apiFields.forEach(output::put);
        var matching = new KisStockBasicInfoMatchingPolicy().match(batch, symbol, KisStockBasicInfoMatchingFixture.api(output));
        return KisStockBasicInfoTypeResolutionFixture.policy().resolve(matching);
    }

    public static List<KisStockTradingFlagStatus> statuses(KisStockBasicInfoRestrictionObservationResult result) {
        return Arrays.asList(result.masterSuspensionStatus(), result.masterLiquidationStatus(), result.masterSpacStatus(),
                result.masterManagementStatus(), result.masterInvestmentCautionStatus(),
                result.basicInfoSuspensionStatus(), result.basicInfoManagementStatus());
    }

    public static KisStockTradingFlagStatus expected(String raw) {
        if (raw == null) {
            return KisStockTradingFlagStatus.FIELD_NOT_PROVIDED;
        }
        return "Y".equals(raw) ? KisStockTradingFlagStatus.Y_OBSERVED
                : "N".equals(raw) ? KisStockTradingFlagStatus.N_OBSERVED : KisStockTradingFlagStatus.VALUE_UNVERIFIED;
    }
}
