package com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result;

import com.stock.market.stock.master.matching.kisbasicinfo.result.KisStockBasicInfoMatchReasonCode;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.result.KisStockBasicInfoRestrictionObservationResult;
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
        Objects.requireNonNull(observation, "observation must not be null.");
        // Keep match diagnostics first, followed by master fields and API fields in declaration order.
        return Arrays.stream(values()).filter(reason -> reason.observedIn(observation)).toList();
    }

    public boolean isExclusionSignal() {
        return expectedStatus == Y_OBSERVED;
    }

    private boolean observedIn(KisStockBasicInfoRestrictionObservationResult observation) {
        if (this == STANDARD_CODE_AND_MARKET_MATCH_NOT_CONFIRMED) {
            return observation.typeResolution().matchingResult().reasonCode()
                    != KisStockBasicInfoMatchReasonCode.STANDARD_CODE_AND_MARKET_MATCH;
        }
        return field.apply(observation) == expectedStatus;
    }
}
