package com.stock.trade.persistence;

import com.stock.agent.InvestmentAction;
import com.stock.trade.TradeReasonCode;
import com.stock.trade.TradeRecord;
import com.stock.trade.TradeResult;
import com.stock.trade.TradeStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradeRecordEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String runId;

    @Enumerated(EnumType.STRING)
    private InvestmentAction action;

    private String symbol;

    private Long quantity;

    private Long priceKrw;

    private Long orderAmountKrw;

    @Enumerated(EnumType.STRING)
    private TradeStatus status;

    @Enumerated(EnumType.STRING)
    private TradeReasonCode reasonCode;

    private String reason;

    private LocalDateTime executedAt;

    public static TradeRecordEntity of(
            String runId,
            InvestmentAction action,
            String symbol,
            Long quantity,
            Long priceKrw,
            Long orderAmountKrw,
            TradeStatus status,
            TradeReasonCode reasonCode,
            String reason,
            LocalDateTime executedAt
    ) {
        TradeRecordEntity entity = new TradeRecordEntity();
        entity.runId = runId;
        entity.action = action;
        entity.symbol = symbol;
        entity.quantity = quantity;
        entity.priceKrw = priceKrw;
        entity.orderAmountKrw = orderAmountKrw;
        entity.status = status;
        entity.reasonCode = reasonCode;
        entity.reason = reason;
        entity.executedAt = executedAt;
        return entity;
    }

    public TradeRecord toRecord() {
        return new TradeRecord(
                runId,
                action,
                symbol,
                quantity,
                priceKrw,
                orderAmountKrw,
                status,
                reasonCode,
                reason,
                executedAt
        );
    }

    public static TradeRecordEntity from(String runId, TradeResult result, LocalDateTime executedAt) {
        return TradeRecordEntity.of(
                runId,
                result.action(),
                result.symbol(),
                result.quantity(),
                result.expectedPriceKrw(),
                result.estimatedOrderAmountKrw(),
                result.status(),
                result.reasonCode(),
                result.reason(),
                executedAt
        );
    }
}
