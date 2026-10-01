package com.stock.backtest.strategy.swing.v1.experiment.sensitivity.cost;

import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentRequest;
import com.stock.trade.cost.model.TradeCostModel;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record SwingV1CostSensitivityRequest(
        SwingV1BacktestExperimentRequest baseRequest,
        List<TradeCostModel> costModels
) {
    public SwingV1CostSensitivityRequest {
        Objects.requireNonNull(baseRequest, "baseRequest must not be null.");
        costModels = List.copyOf(Objects.requireNonNull(
                costModels, "costModels must not be null."
        ));
        if (costModels.size() < 2) {
            throw new IllegalArgumentException(
                    "costModels must contain a baseline and at least one stress model."
            );
        }
        TradeCostModel baseline = baseRequest.costModel();
        if (!baseline.equals(costModels.getFirst())) {
            throw new IllegalArgumentException(
                    "The first costModel must match baseRequest costModel."
            );
        }
        for (int index = 0; index < costModels.size(); index++) {
            TradeCostModel model = costModels.get(index);
            if (baseline.buyCommissionRate().compareTo(model.buyCommissionRate()) != 0
                    || baseline.sellCommissionRate().compareTo(model.sellCommissionRate()) != 0
                    || baseline.sellTaxRate().compareTo(model.sellTaxRate()) != 0) {
                throw new IllegalArgumentException(
                        "Commission and tax rates must remain fixed across costModels."
                );
            }
            if (model.buySlippageRate().compareTo(baseline.buySlippageRate()) < 0
                    || model.sellSlippageRate().compareTo(baseline.sellSlippageRate()) < 0) {
                throw new IllegalArgumentException(
                        "Stress slippage rates must not be below the baseline."
                );
            }
            for (int previousIndex = 0; previousIndex < index; previousIndex++) {
                TradeCostModel previous = costModels.get(previousIndex);
                if (model.modelId().equals(previous.modelId())
                        && model.modelVersion() == previous.modelVersion()) {
                    throw new IllegalArgumentException(
                            "costModels must not contain duplicate model identity."
                    );
                }
                if (model.buySlippageRate().compareTo(previous.buySlippageRate()) == 0
                        && model.sellSlippageRate().compareTo(previous.sellSlippageRate()) == 0) {
                    throw new IllegalArgumentException(
                            "costModels must not contain duplicate slippage assumptions."
                    );
                }
            }
        }
    }

    public static SwingV1CostSensitivityRequest forSlippageStress(
            SwingV1BacktestExperimentRequest baseRequest
    ) {
        Objects.requireNonNull(baseRequest, "baseRequest must not be null.");
        TradeCostModel baseline = baseRequest.costModel();
        if (baseline.buySlippageRate().signum() == 0
                && baseline.sellSlippageRate().signum() == 0) {
            throw new IllegalArgumentException(
                    "Slippage stress requires at least one positive baseline slippage rate."
            );
        }
        return new SwingV1CostSensitivityRequest(baseRequest, List.of(
                baseline,
                multiplySlippage(baseline, 2),
                multiplySlippage(baseline, 3)
        ));
    }

    public SwingV1BacktestExperimentRequest requestFor(TradeCostModel costModel) {
        if (!costModels.contains(Objects.requireNonNull(
                costModel, "costModel must not be null."
        ))) {
            throw new IllegalArgumentException(
                    "costModel must belong to the sensitivity request."
            );
        }
        return new SwingV1BacktestExperimentRequest(
                baseRequest.strategyIdentity(),
                baseRequest.candidateSymbols(),
                baseRequest.benchmarkId(),
                baseRequest.fromSignalDate(),
                baseRequest.toSignalDate(),
                baseRequest.initialCashAmountKrwPerSymbol(),
                costModel
        );
    }

    private static TradeCostModel multiplySlippage(TradeCostModel baseline, int multiplier) {
        BigDecimal factor = BigDecimal.valueOf(multiplier);
        return new TradeCostModel(
                baseline.modelId() + "_SLIPPAGE_X" + multiplier,
                baseline.modelVersion(),
                baseline.buyCommissionRate(),
                baseline.sellCommissionRate(),
                baseline.sellTaxRate(),
                baseline.buySlippageRate().multiply(factor),
                baseline.sellSlippageRate().multiply(factor)
        );
    }
}
