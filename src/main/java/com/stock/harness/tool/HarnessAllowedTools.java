package com.stock.harness.tool;

import java.util.List;

public record HarnessAllowedTools(
        List<HarnessToolType> types
) {
    public boolean allows(HarnessToolType type) {
        return types.contains(type);
    }
}
