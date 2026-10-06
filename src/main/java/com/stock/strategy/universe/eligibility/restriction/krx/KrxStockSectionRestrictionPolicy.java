package com.stock.strategy.universe.eligibility.restriction.krx;

import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.strategy.universe.eligibility.classification.kiskrx.result.KisKrxStockTypeResolutionResult;
import com.stock.strategy.universe.eligibility.restriction.krx.result.KrxStockSectionRestrictionReasonCode;
import com.stock.strategy.universe.eligibility.restriction.krx.result.KrxStockSectionRestrictionResult;

import java.util.Objects;

public class KrxStockSectionRestrictionPolicy {
    public static final String RESTRICTION_VERSION = "KRX_STOCK_SECTION_CURRENT_RESTRICTION_V1";
    public static final String SOURCE_REFERENCE = "build/stock-eligibility-krx-source-validation-03/final-verification.json";
    public static final String SOURCE_SHA256 = "df0658234036f57e88f297ef3cdf13eca804ea07ff0b944474ba3151a6448711";

    public KrxStockSectionRestrictionResult evaluate(KisKrxStockTypeResolutionResult typeResolution) {
        Objects.requireNonNull(typeResolution, "typeResolution must not be null.");
        if (typeResolution.identityMatch() == null) {
            return result(typeResolution, KrxStockSectionRestrictionReasonCode.IDENTITY_MATCH_UNVERIFIED);
        }
        // The observed section meanings are KOSDAQ-only; an exact match is not trading permission.
        if (typeResolution.identityMatch().requestMarket() != KisStockMasterMarket.KOSDAQ) {
            return result(typeResolution, KrxStockSectionRestrictionReasonCode.SECTION_UNVERIFIED);
        }
        var reason = switch (typeResolution.identityMatch().krxRecord().rawSection()) {
            case "SPAC(\uc18c\uc18d\ubd80\uc5c6\uc74c)" -> KrxStockSectionRestrictionReasonCode.SPAC_OBSERVED;
            case "\uad00\ub9ac\uc885\ubaa9(\uc18c\uc18d\ubd80\uc5c6\uc74c)" -> KrxStockSectionRestrictionReasonCode.MANAGEMENT_DESIGNATION_OBSERVED;
            case "\ud22c\uc790\uc8fc\uc758\ud658\uae30\uc885\ubaa9(\uc18c\uc18d\ubd80\uc5c6\uc74c)" -> KrxStockSectionRestrictionReasonCode.INVESTMENT_CAUTION_OBSERVED;
            case "\uc6b0\ub7c9\uae30\uc5c5\ubd80", "\uc911\uacac\uae30\uc5c5\ubd80",
                 "\uae30\uc220\uc131\uc7a5\uae30\uc5c5\ubd80", "\ubca4\ucc98\uae30\uc5c5\ubd80" -> KrxStockSectionRestrictionReasonCode.NO_TARGET_RESTRICTION_OBSERVED;
            default -> KrxStockSectionRestrictionReasonCode.SECTION_UNVERIFIED;
        };
        return result(typeResolution, reason);
    }

    private static KrxStockSectionRestrictionResult result(
            KisKrxStockTypeResolutionResult typeResolution,
            KrxStockSectionRestrictionReasonCode reasonCode
    ) {
        return new KrxStockSectionRestrictionResult(typeResolution, reasonCode,
                RESTRICTION_VERSION, SOURCE_REFERENCE, SOURCE_SHA256);
    }
}
