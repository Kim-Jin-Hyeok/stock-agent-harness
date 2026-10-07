package com.stock.strategy.universe.eligibility.restriction.kis.screening.result;

import com.stock.market.stock.master.matching.kisbasicinfo.result.KisStockBasicInfoMatchReasonCode;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningResult;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.KisStockRestrictionScreeningPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.warning.result.KisStockMarketWarningObservation;
import com.stock.strategy.universe.eligibility.restriction.kis.warning.result.KisStockMarketWarningObservationResult;

import java.util.ArrayList;
import java.util.List;

public enum KisStockRestrictionScreeningReasonCode {
    STANDARD_CODE_AND_MARKET_MATCH_NOT_CONFIRMED(false, true),
    MARKET_WARNING_MARKET_NOT_MATCHED(false, true),
    MARKET_WARNING_MASTER_SOURCE_NOT_MATCHED(false, true),
    MARKET_WARNING_REQUESTED_RECORD_NOT_MATCHED(false, true),
    BASIC_INFO_EXCLUSION_SIGNAL_OBSERVED(true, false),
    BASIC_INFO_REVIEW_REQUIRED(false, false),
    MARKET_WARNING_INVESTMENT_CAUTION_OBSERVED(true, false),
    MARKET_WARNING_INVESTMENT_WARNING_OBSERVED(true, false),
    MARKET_WARNING_INVESTMENT_RISK_OBSERVED(true, false),
    MARKET_WARNING_VALUE_UNVERIFIED(false, false),
    MARKET_WARNING_RISK_PREANNOUNCEMENT_Y_OBSERVED(true, false),
    MARKET_WARNING_RISK_PREANNOUNCEMENT_VALUE_UNVERIFIED(false, false);

    private final boolean exclusionSignal;
    private final boolean connectionFailure;

    KisStockRestrictionScreeningReasonCode(boolean exclusionSignal, boolean connectionFailure) {
        this.exclusionSignal = exclusionSignal;
        this.connectionFailure = connectionFailure;
    }

    public boolean isExclusionSignal() {
        return exclusionSignal;
    }

    public boolean isConnectionFailure() {
        return connectionFailure;
    }

    public static List<KisStockRestrictionScreeningReasonCode> fromInputs(
            KisStockBasicInfoRestrictionScreeningResult basicInfoScreening,
            KisStockMarketWarningObservationResult marketWarningObservation
    ) {
        KisStockRestrictionScreeningPolicy.requireSupportedInputs(basicInfoScreening, marketWarningObservation);
        var reasons = new ArrayList<KisStockRestrictionScreeningReasonCode>();
        var matching = basicInfoScreening.observation().typeResolution().matchingResult();
        if (matching.reasonCode() != KisStockBasicInfoMatchReasonCode.STANDARD_CODE_AND_MARKET_MATCH) {
            reasons.add(STANDARD_CODE_AND_MARKET_MATCH_NOT_CONFIRMED);
        }
        var matchedWarning = matchedObservation(basicInfoScreening, marketWarningObservation, reasons);
        switch (basicInfoScreening.status()) {
            case EXCLUSION_SIGNAL_OBSERVED -> reasons.add(BASIC_INFO_EXCLUSION_SIGNAL_OBSERVED);
            case REVIEW_REQUIRED -> reasons.add(BASIC_INFO_REVIEW_REQUIRED);
            case NO_EXCLUSION_SIGNAL_OBSERVED -> {
            }
        }
        if (matchedWarning != null) {
            switch (matchedWarning.warningStatus()) {
                case INVESTMENT_CAUTION_OBSERVED -> reasons.add(MARKET_WARNING_INVESTMENT_CAUTION_OBSERVED);
                case INVESTMENT_WARNING_OBSERVED -> reasons.add(MARKET_WARNING_INVESTMENT_WARNING_OBSERVED);
                case INVESTMENT_RISK_OBSERVED -> reasons.add(MARKET_WARNING_INVESTMENT_RISK_OBSERVED);
                case VALUE_UNVERIFIED -> reasons.add(MARKET_WARNING_VALUE_UNVERIFIED);
                case NO_WARNING_OBSERVED -> {
                }
            }
            switch (matchedWarning.riskPreannouncementStatus()) {
                case Y_OBSERVED -> reasons.add(MARKET_WARNING_RISK_PREANNOUNCEMENT_Y_OBSERVED);
                case VALUE_UNVERIFIED -> reasons.add(MARKET_WARNING_RISK_PREANNOUNCEMENT_VALUE_UNVERIFIED);
                case N_OBSERVED -> {
                }
                case FIELD_NOT_PROVIDED -> throw new IllegalArgumentException("Market warning preannouncement must be provided in the supported source layout.");
            }
        }
        return List.copyOf(reasons);
    }

    private static KisStockMarketWarningObservation matchedObservation(
            KisStockBasicInfoRestrictionScreeningResult basicInfoScreening,
            KisStockMarketWarningObservationResult marketWarningObservation,
            List<KisStockRestrictionScreeningReasonCode> reasons
    ) {
        var matching = basicInfoScreening.observation().typeResolution().matchingResult();
        var compared = matching.comparedRecord();
        if (compared == null || matching.comparedMarket() == null) {
            reasons.add(MARKET_WARNING_REQUESTED_RECORD_NOT_MATCHED);
            return null;
        }
        var warningMaster = marketWarningObservation.source().source();
        if (matching.comparedMarket() != warningMaster.market()) {
            reasons.add(MARKET_WARNING_MARKET_NOT_MATCHED);
            return null;
        }
        var master = matching.masterBatch().marketResults().stream()
                .filter(result -> result.market() == matching.comparedMarket()).findFirst().orElseThrow();
        // A hash alone is insufficient for legacy objects built directly or restored from JSON.
        if (!master.equals(warningMaster)) {
            reasons.add(MARKET_WARNING_MASTER_SOURCE_NOT_MATCHED);
            return null;
        }
        int index = compared.lineNumber() - 1;
        if (index < 0 || index >= marketWarningObservation.observations().size()) {
            reasons.add(MARKET_WARNING_REQUESTED_RECORD_NOT_MATCHED);
            return null;
        }
        var observed = marketWarningObservation.observations().get(index);
        var raw = observed.rawRecord();
        if (raw.lineNumber() != compared.lineNumber() || !raw.symbol().equals(matching.requestedSymbol())
                || !raw.standardCode().equals(compared.standardCode())) {
            reasons.add(MARKET_WARNING_REQUESTED_RECORD_NOT_MATCHED);
            return null;
        }
        return observed;
    }
}
