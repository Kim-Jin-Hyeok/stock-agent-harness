package com.stock.strategy.universe.candidate.evaluation.snapshot.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.stock.strategy.universe.candidate.evaluation.snapshot.StockCandidateEvaluationSnapshot;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class StockCandidateEvaluationSnapshotJsonConverter {
    private final ObjectWriter writer;
    private final ObjectReader reader;

    public StockCandidateEvaluationSnapshotJsonConverter(ObjectMapper objectMapper) {
        // Keep evidence parsing strict without changing the application's shared mapper.
        ObjectMapper snapshotMapper = Objects.requireNonNull(
                objectMapper, "objectMapper must not be null."
        ).copy().setDefaultPropertyInclusion(JsonInclude.Include.ALWAYS);
        snapshotMapper.coercionConfigFor(LogicalType.Integer)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail);
        snapshotMapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        writer = snapshotMapper.writerFor(StockCandidateEvaluationSnapshot.class)
                .without(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        reader = snapshotMapper.readerFor(StockCandidateEvaluationSnapshot.class)
                .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                        DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS,
                        DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT,
                        DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES,
                        DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS,
                        DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY,
                        DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL,
                        DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE);
    }

    public String toJson(StockCandidateEvaluationSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");
        try {
            return writer.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize stock candidate evaluation snapshot.", e);
        }
    }

    public StockCandidateEvaluationSnapshot fromJson(String json) {
        Objects.requireNonNull(json, "json must not be null.");
        try {
            StockCandidateEvaluationSnapshot snapshot = reader.readValue(json);
            if (snapshot == null) {
                throw new IllegalArgumentException("Snapshot json must contain an object, not null.");
            }
            return snapshot;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize stock candidate evaluation snapshot json.", e);
        }
    }
}
