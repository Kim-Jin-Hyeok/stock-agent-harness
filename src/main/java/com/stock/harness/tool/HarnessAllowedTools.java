package com.stock.harness.tool;

import java.util.List;

public record HarnessAllowedTools(
        List<HarnessToolType> types
) {
    public boolean allows(HarnessToolType type) {
        return types.contains(type);
    }

    public static HarnessAllowedTools readOnly() {
        return new HarnessAllowedTools(
                List.of(
                        HarnessToolType.GET_PORTFOLIO,
                        HarnessToolType.GET_MARKET
                )
        );
    }
}
