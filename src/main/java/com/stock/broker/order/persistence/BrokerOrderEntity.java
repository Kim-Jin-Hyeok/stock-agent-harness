package com.stock.broker.order.persistence;

import com.stock.broker.order.BrokerOrderRecord;
import com.stock.broker.order.BrokerOrderReference;
import com.stock.broker.order.BrokerOrderSide;
import com.stock.broker.order.BrokerOrderStatus;
import com.stock.broker.order.cancellation.BrokerOrderCancellationSubmission;
import com.stock.broker.order.cancellation.BrokerOrderCancellationSubmissionStatus;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "broker_order",
        indexes = {
                @Index(
                        name = "idx_broker_order_status_submitted_at",
                        columnList = "status, submitted_at"
                ),
                @Index(
                        name = "idx_broker_order_run_id",
                        columnList = "run_id"
                ),
                @Index(
                        name = "idx_broker_order_cancellation_target",
                        columnList = "status, cancellation_status, expires_at"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrokerOrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "broker_order_id", length = 100)
    private String brokerOrderId;

    @Column(name = "broker_order_organization_number", length = 100)
    private String brokerOrderOrganizationNumber;

    @Column(name = "run_id", nullable = false, length = 100)
    private String runId;

    @Column(name = "strategy_id", nullable = false, length = 100)
    private String strategyId;

    @Column(name = "strategy_version", nullable = false)
    private int strategyVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "horizon", nullable = false, length = 30)
    private InvestmentHorizon horizon;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 10)
    private BrokerOrderSide side;

    @Column(name = "symbol", nullable = false, length = 30)
    private String symbol;

    @Column(name = "requested_quantity", nullable = false)
    private long requestedQuantity;

    @Column(name = "limit_price_krw", nullable = false)
    private long limitPriceKrw;

    @Column(name = "cumulative_filled_quantity", nullable = false)
    private long cumulativeFilledQuantity;

    @Column(name = "average_filled_price_krw")
    private Long averageFilledPriceKrw;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BrokerOrderStatus status;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_reconciled_at")
    private Instant lastReconciledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_status", length = 30)
    private BrokerOrderCancellationSubmissionStatus cancellationStatus;

    @Column(name = "cancellation_order_id", length = 100)
    private String cancellationOrderId;

    @Column(name = "cancellation_order_organization_number", length = 100)
    private String cancellationOrderOrganizationNumber;

    @Column(name = "cancellation_submitted_at")
    private Instant cancellationSubmittedAt;

    @Column(name = "cancellation_reason", length = 1000)
    private String cancellationReason;

    public static BrokerOrderEntity from(BrokerOrderRecord record) {
        BrokerOrderEntity entity = new BrokerOrderEntity();
        entity.id = record.id();
        if (record.reference() != null) {
            entity.brokerOrderId = record.reference().orderId();
            entity.brokerOrderOrganizationNumber =
                    record.reference().organizationNumber();
        }
        entity.runId = record.runId();
        entity.strategyId = record.strategyIdentity().strategyId();
        entity.strategyVersion = record.strategyIdentity().strategyVersion();
        entity.horizon = record.strategyIdentity().horizon();
        entity.side = record.side();
        entity.symbol = record.symbol();
        entity.requestedQuantity = record.requestedQuantity();
        entity.limitPriceKrw = record.limitPriceKrw();
        entity.cumulativeFilledQuantity = record.cumulativeFilledQuantity();
        entity.averageFilledPriceKrw = record.averageFilledPriceKrw();
        entity.status = record.status();
        entity.reason = record.reason();
        entity.submittedAt = record.submittedAt();
        entity.expiresAt = record.expiresAt();
        entity.lastReconciledAt = record.lastReconciledAt();
        if (record.cancellationSubmission() != null) {
            BrokerOrderCancellationSubmission cancellation =
                    record.cancellationSubmission();
            entity.cancellationStatus = cancellation.status();
            if (cancellation.cancellationReference() != null) {
                entity.cancellationOrderId =
                        cancellation.cancellationReference().orderId();
                entity.cancellationOrderOrganizationNumber =
                        cancellation.cancellationReference()
                                .organizationNumber();
            }
            entity.cancellationSubmittedAt = cancellation.submittedAt();
            entity.cancellationReason = cancellation.reason();
        }
        return entity;
    }

    public BrokerOrderRecord toRecord() {
        return new BrokerOrderRecord(
                id,
                toReference(),
                runId,
                new InvestmentStrategyIdentity(
                        strategyId,
                        strategyVersion,
                        horizon
                ),
                side,
                symbol,
                requestedQuantity,
                limitPriceKrw,
                cumulativeFilledQuantity,
                averageFilledPriceKrw,
                status,
                reason,
                submittedAt,
                expiresAt,
                lastReconciledAt,
                toCancellationSubmission()
        );
    }

    private BrokerOrderReference toReference() {
        if (brokerOrderId == null) {
            return null;
        }
        return new BrokerOrderReference(
                brokerOrderId,
                brokerOrderOrganizationNumber
        );
    }

    private BrokerOrderCancellationSubmission toCancellationSubmission() {
        if (cancellationStatus == null) {
            return null;
        }
        return new BrokerOrderCancellationSubmission(
                cancellationStatus,
                toCancellationReference(),
                cancellationSubmittedAt,
                cancellationReason
        );
    }

    private BrokerOrderReference toCancellationReference() {
        if (cancellationOrderId == null) {
            return null;
        }
        return new BrokerOrderReference(
                cancellationOrderId,
                cancellationOrderOrganizationNumber
        );
    }
}
