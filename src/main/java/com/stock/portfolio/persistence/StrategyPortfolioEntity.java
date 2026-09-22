package com.stock.portfolio.persistence;

import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "strategy_portfolio",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_strategy_portfolio_identity",
                columnNames = {"strategy_id", "strategy_version", "horizon"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StrategyPortfolioEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "strategy_id", nullable = false)
    private String strategyId;

    @Column(name = "strategy_version", nullable = false)
    private int strategyVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "horizon", nullable = false)
    private InvestmentHorizon horizon;

    @Lob
    @Column(name = "snapshot_json", nullable = false)
    private String snapshotJson;

    public static StrategyPortfolioEntity of(
            InvestmentStrategyIdentity strategyIdentity,
            String snapshotJson
    ) {
        StrategyPortfolioEntity entity = new StrategyPortfolioEntity();
        entity.strategyId = strategyIdentity.strategyId();
        entity.strategyVersion = strategyIdentity.strategyVersion();
        entity.horizon = strategyIdentity.horizon();
        entity.snapshotJson = snapshotJson;
        return entity;
    }

    public void updateSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }

    public InvestmentStrategyIdentity strategyIdentity() {
        return new InvestmentStrategyIdentity(strategyId, strategyVersion, horizon);
    }
}
