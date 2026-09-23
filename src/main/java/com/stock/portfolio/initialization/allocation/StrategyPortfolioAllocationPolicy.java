package com.stock.portfolio.initialization.allocation;

import com.stock.broker.account.BrokerAccountSnapshot;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.portfolio.initialization.allocation.config.StrategyAllocationProperties;
import com.stock.portfolio.initialization.allocation.config.StrategyPortfolioAllocationProperties;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

@Component
public class StrategyPortfolioAllocationPolicy {
    private final List<StrategyAllocationProperties> allocations;
    private final BigInteger totalWeight;

    public StrategyPortfolioAllocationPolicy(
            StrategyPortfolioAllocationProperties properties
    ) {
        Objects.requireNonNull(properties, "properties must not be null.");
        this.allocations = properties.allocations();
        this.totalWeight = allocations.stream()
                .map(StrategyAllocationProperties::weight)
                .map(BigInteger::valueOf)
                .reduce(BigInteger.ZERO, BigInteger::add);
    }

    public PortfolioSnapshot allocate(
            BrokerAccountSnapshot brokerSnapshot,
            InvestmentStrategyIdentity strategyIdentity
    ) {
        Objects.requireNonNull(
                brokerSnapshot,
                "brokerSnapshot must not be null."
        );
        Objects.requireNonNull(
                strategyIdentity,
                "strategyIdentity must not be null."
        );
        if (!brokerSnapshot.positions().isEmpty()) {
            throw new IllegalStateException(
                    "Broker account must not have positions before strategy "
                            + "portfolio initialization."
            );
        }

        int allocationIndex = findAllocationIndex(strategyIdentity);
        return new PortfolioSnapshot(
                allocateAmount(
                        brokerSnapshot.depositAmountKrw(),
                        allocationIndex
                ),
                allocateAmount(
                        brokerSnapshot.totalAssetAmountKrw(),
                        allocationIndex
                ),
                List.of()
        );
    }

    private int findAllocationIndex(
            InvestmentStrategyIdentity strategyIdentity
    ) {
        for (int index = 0; index < allocations.size(); index++) {
            if (allocations.get(index).strategyIdentity().equals(strategyIdentity)) {
                return index;
            }
        }
        throw new IllegalArgumentException(
                "Strategy portfolio allocation was not configured: "
                        + strategyIdentity
        );
    }

    private long allocateAmount(long amountKrw, int allocationIndex) {
        BigInteger amount = BigInteger.valueOf(amountKrw);
        long[] allocatedAmounts = new long[allocations.size()];
        long allocatedTotal = 0L;

        for (int index = 0; index < allocations.size(); index++) {
            BigInteger weightedAmount = amount.multiply(
                    BigInteger.valueOf(allocations.get(index).weight())
            );
            long allocatedAmount = weightedAmount
                    .divide(totalWeight)
                    .longValueExact();
            allocatedAmounts[index] = allocatedAmount;
            allocatedTotal = Math.addExact(allocatedTotal, allocatedAmount);
        }

        long remainder = amountKrw - allocatedTotal;
        for (int index = 0; index < remainder; index++) {
            allocatedAmounts[index]++;
        }
        return allocatedAmounts[allocationIndex];
    }
}
