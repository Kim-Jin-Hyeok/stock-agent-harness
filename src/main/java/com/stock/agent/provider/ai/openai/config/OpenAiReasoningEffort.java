package com.stock.agent.provider.ai.openai.config;

import java.util.Locale;

public enum OpenAiReasoningEffort {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    XHIGH,
    MAX;

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
