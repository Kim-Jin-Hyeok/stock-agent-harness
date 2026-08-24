package com.stock.harness.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HarnessRunSnapshotJsonConverter {
    private final ObjectMapper objectMapper;

    public String toDecisionJson(HarnessDecisionSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize decision snapshot.", e);
        }
    }

    public String toRiskCheckJson(HarnessRiskCheckSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize risk check snapshot.", e);
        }
    }

    public String toPortfolioJson(HarnessPortfolioSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize portfolio snapshot.", e);
        }
    }

    public String toMarketJson(HarnessMarketSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize market snapshot.", e);
        }
    }

    public HarnessDecisionSnapshot toDecisionSnapshot(String json) {
        try {
            return objectMapper.readValue(json, HarnessDecisionSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize decision snapshot json.", e);
        }
    }

    public HarnessRiskCheckSnapshot toRiskCheckSnapshot(String json) {
        try {
            return objectMapper.readValue(json, HarnessRiskCheckSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize risk check snapshot json.", e);
        }
    }

    public HarnessPortfolioSnapshot toPortfolioSnapshot(String json) {
        try {
            return objectMapper.readValue(json, HarnessPortfolioSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize portfolio snapshot json.", e);
        }
    }

    public HarnessMarketSnapshot toMarketSnapshot(String json) {
        try {
            return objectMapper.readValue(json, HarnessMarketSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize market snapshot json.", e);
        }
    }
}
