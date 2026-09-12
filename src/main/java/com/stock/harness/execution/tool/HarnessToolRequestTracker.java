package com.stock.harness.execution.tool;

import com.stock.harness.tool.HarnessToolRequest;

import java.util.HashSet;
import java.util.Set;

public class HarnessToolRequestTracker {
    private final Set<HarnessToolRequest> requests = new HashSet<>();

    public boolean tryRegister(HarnessToolRequest request) {
        return requests.add(request);
    }
}
