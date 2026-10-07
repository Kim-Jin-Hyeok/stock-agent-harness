package com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result;

import com.stock.market.stock.master.matching.kisbasicinfo.result.KisStockBasicInfoMatchReasonCode;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.result.KisStockBasicInfoRestrictionObservationResult;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.KisStockBasicInfoRestrictionScreeningPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.result.KisStockTradingFlagStatus;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static com.stock.strategy.universe.eligibility.restriction.kis.result.KisStockTradingFlagStatus.FIELD_NOT_PROVIDED;
import static com.stock.strategy.universe.eligibility.restriction.kis.result.KisStockTradingFlagStatus.VALUE_UNVERIFIED;
import static com.stock.strategy.universe.eligibility.restriction.kis.result.KisStockTradingFlagStatus.Y_OBSERVED;

public enum KisStockBasicInfoRestrictionScreeningReasonCode {
    STANDARD_CODE_AND_MARKET_MATCH_NOT_CONFIRMED(null, null),
    MASTER_SUSPENSION_Y_OBSERVED(KisStockBasicInfoRestrictionObservationResult::masterSuspensionStatus, Y_OBSERVED),
    MASTER_SUSPENSION_VALUE_UNVERIFIED(KisStockBasicInfoRestrictionObservationResult::masterSuspensionStatus, VALUE_UNVERIFIED),
    MASTER_LIQUIDATION_Y_OBSERVED(KisStockBasicInfoRestrictionObservationResult::masterLiquidationStatus, Y_OBSERVED),
    MASTER_LIQUIDATION_VALUE_UNVERIFIED(KisStockBasicInfoRestrictionObservationResult::masterLiquidationStatus, VALUE_UNVERIFIED),
    MASTER_SPAC_Y_OBSERVED(KisStockBasicInfoRestrictionObservationResult::masterSpacStatus, Y_OBSERVED),
    MASTER_SPAC_VALUE_UNVERIFIED(KisStockBasicInfoRestrictionObservationResult::masterSpacStatus, VALUE_UNVERIFIED),
    MASTER_MANAGEMENT_Y_OBSERVED(KisStockBasicInfoRestrictionObservationResult::masterManagementStatus, Y_OBSERVED),
    MASTER_MANAGEMENT_VALUE_UNVERIFIED(KisStockBasicInfoRestrictionObservationResult::masterManagementStatus, VALUE_UNVERIFIED),
    MASTER_INVESTMENT_CAUTION_Y_OBSERVED(KisStockBasicInfoRestrictionObservationResult::masterInvestmentCautionStatus, Y_OBSERVED),
    MASTER_INVESTMENT_CAUTION_VALUE_UNVERIFIED(KisStockBasicInfoRestrictionObservationResult::masterInvestmentCautionStatus, VALUE_UNVERIFIED),
    MASTER_INVESTMENT_CAUTION_FIELD_NOT_PROVIDED(KisStockBasicInfoRestrictionObservationResult::masterInvestmentCautionStatus, FIELD_NOT_PROVIDED),
    MASTER_INVESTMENT_CAUTION_NOT_APPLICABLE(null, null),
    BASIC_INFO_SUSPENSION_Y_OBSERVED(KisStockBasicInfoRestrictionObservationResult::basicInfoSuspensionStatus, Y_OBSERVED),
    BASIC_INFO_SUSPENSION_VALUE_UNVERIFIED(KisStockBasicInfoRestrictionObservationResult::basicInfoSuspensionStatus, VALUE_UNVERIFIED),
    BASIC_INFO_MANAGEMENT_Y_OBSERVED(KisStockBasicInfoRestrictionObservationResult::basicInfoManagementStatus, Y_OBSERVED),
    BASIC_INFO_MANAGEMENT_VALUE_UNVERIFIED(KisStockBasicInfoRestrictionObservationResult::basicInfoManagementStatus, VALUE_UNVERIFIED);

    private final Function<KisStockBasicInfoRestrictionObservationResult, KisStockTradingFlagStatus> field;
    private final KisStockTradingFlagStatus expectedStatus;

    KisStockBasicInfoRestrictionScreeningReasonCode(
            Function<KisStockBasicInfoRestrictionObservationResult, KisStockTradingFlagStatus> field,
            KisStockTradingFlagStatus expectedStatus
    ) {
        this.field = field;
        this.expectedStatus = expectedStatus;
    }

    public static List<KisStockBasicInfoRestrictionScreeningReasonCode> fromObservation(
            KisStockBasicInfoRestrictionObservationResult observation
    ) {
        return fromObservation(observation, KisStockBasicInfoRestrictionScreeningPolicy.SCREENING_VERSION);
    }

    public static List<KisStockBasicInfoRestrictionScreeningReasonCode> fromObservation(
            KisStockBasicInfoRestrictionObservationResult observation, String screeningVersion
    ) {
        Objects.requireNonNull(observation, "observation must not be null.");
        KisStockBasicInfoRestrictionScreeningPolicy.requireSupportedVersion(screeningVersion);
        // Keep match diagnostics first, followed by master fields and API fields in declaration order.
        return Arrays.stream(values()).filter(reason -> reason.observedIn(observation, screeningVersion)).toList();
    }

    public boolean isExclusionSignal() {
        return expectedStatus == Y_OBSERVED;
    }

    private boolean observedIn(KisStockBasicInfoRestrictionObservationResult observation, String screeningVersion) {
        if (this == STANDARD_CODE_AND_MARKET_MATCH_NOT_CONFIRMED) {
            return observation.typeResolution().matchingResult().reasonCode()
                    != KisStockBasicInfoMatchReasonCode.STANDARD_CODE_AND_MARKET_MATCH;
        }
        if (this == MASTER_INVESTMENT_CAUTION_NOT_APPLICABLE) {
            return KisStockBasicInfoRestrictionScreeningPolicy.SCREENING_VERSION_V2.equals(screeningVersion)
                    && hasVerifiedKospiCommonStockContext(observation);
        }
        return field.apply(observation) == expectedStatus;
    }

    private static boolean hasVerifiedKospiCommonStockContext(KisStockBasicInfoRestrictionObservationResult observation) {
        var type = observation.typeResolution();
        var matching = type.matchingResult();
        var master = type.masterClassification();
        var api = type.basicInfoClassification();
        if (matching.reasonCode() != KisStockBasicInfoMatchReasonCode.STANDARD_CODE_AND_MARKET_MATCH
                || matching.comparedMarket() != KisStockMasterMarket.KOSPI
                || master == null || api == null || type.referenceSecurityType() != StockSecurityType.COMMON_STOCK
                || observation.masterInvestmentCautionStatus() != FIELD_NOT_PROVIDED) {
            return false;
        }
        // Freeze the evidence accepted by V2; future input policies require a new applicability review.
        var raw = api.parseResult().rawRecord();
        return "KIS_STOCK_BASIC_INFO_RESTRICTION_OBSERVATION_V1".equals(observation.observationVersion())
                && "KIS_STOCK_BASIC_INFO_CURRENT_TYPE_RESOLUTION_V1".equals(type.resolutionVersion())
                && "KIS_STOCK_BASIC_INFO_STANDARD_CODE_MARKET_V1".equals(matching.matchingVersion())
                && "KIS_STOCK_MASTER_CURRENT_TYPE_V1".equals(master.classificationVersion())
                && "277ec0eb7a9b7f63b6807829286c80f36649dad2".equals(master.sourceRevision())
                && matching.masterBatch().marketResults().stream()
                .filter(result -> result.market() == KisStockMasterMarket.KOSPI)
                .allMatch(result -> "KIS_STOCK_MASTER_RAW_V2".equals(result.parserVersion())
                        && "OBSERVED_2026_10_05_LF_V1".equals(result.layoutVersion()))
                && "KIS_STOCK_BASIC_INFO_CURRENT_TYPE_V1".equals(api.classificationVersion())
                && "build/kis-stock-eligibility-source-validation-01/apiportal-specification.json".equals(api.sourceReference())
                && "1fbf349c755a3f54469450e0b7ca89ee3aa3a779e4a33286348cc2e198c0567a".equals(api.sourceSha256())
                && "KIS_STOCK_BASIC_INFO_RAW_V1".equals(api.parseResult().parserVersion())
                && api.securityType() == StockSecurityType.COMMON_STOCK
                && "STK".equals(raw.rawMarket()) && "300".equals(raw.rawProductType())
                && "ST".equals(raw.rawSecurityGroup()) && "101".equals(raw.rawStockKind());
    }
}
