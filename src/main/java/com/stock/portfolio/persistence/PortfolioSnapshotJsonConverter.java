package com.stock.portfolio.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.portfolio.PortfolioSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PortfolioSnapshotJsonConverter {
    private final ObjectMapper objectMapper;

    public String toJson(PortfolioSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize portfolio snapshot.", e);
        }
    }

    public PortfolioSnapshot fromJson(String json) {
        try {
            return objectMapper.readValue(json, PortfolioSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Failed to deserialize portfolio snapshot json.",
                    e
            );
        }
    }
}
