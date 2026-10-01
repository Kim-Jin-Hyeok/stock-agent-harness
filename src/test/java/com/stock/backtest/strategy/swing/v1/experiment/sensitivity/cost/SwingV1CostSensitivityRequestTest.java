package com.stock.backtest.strategy.swing.v1.experiment.sensitivity.cost;

import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentRequest;
import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.stock.backtest.strategy.swing.v1.experiment.sensitivity.cost.SwingV1CostSensitivityFixtures.model;
import static com.stock.backtest.strategy.swing.v1.experiment.sensitivity.cost.SwingV1CostSensitivityFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingV1CostSensitivityRequestTest {
    @Test
    void buildsBaselineDoubleAndTripleSlippageWithFixedCommissionAndTax() {
        SwingV1BacktestExperimentRequest base = request();
        SwingV1CostSensitivityRequest sensitivity = SwingV1CostSensitivityRequest.forSlippageStress(base);

        assertThat(sensitivity.costModels()).extracting(TradeCostModel::modelId)
                .containsExactly("BASE", "BASE_SLIPPAGE_X2", "BASE_SLIPPAGE_X3");
        assertThat(sensitivity.costModels()).extracting(TradeCostModel::buySlippageRate)
                .containsExactly(new BigDecimal("0.001"), new BigDecimal("0.002"), new BigDecimal("0.003"));
        assertThat(sensitivity.costModels()).extracting(TradeCostModel::sellSlippageRate)
                .containsExactly(new BigDecimal("0.001"), new BigDecimal("0.002"), new BigDecimal("0.003"));
        assertThat(sensitivity.costModels()).allSatisfy(cost -> {
            assertThat(cost.modelVersion()).isEqualTo(base.costModel().modelVersion());
            assertThat(cost.buyCommissionRate()).isEqualTo(base.costModel().buyCommissionRate());
            assertThat(cost.sellCommissionRate()).isEqualTo(base.costModel().sellCommissionRate());
            assertThat(cost.sellTaxRate()).isEqualTo(base.costModel().sellTaxRate());
        });
        assertThat(sensitivity.costModels().getFirst()).isSameAs(base.costModel());
    }

    @Test
    void changesOnlyCostModelAndPreservesCandidateOrder() {
        SwingV1CostSensitivityRequest sensitivity = SwingV1CostSensitivityRequest.forSlippageStress(request());

        for (TradeCostModel model : sensitivity.costModels()) {
            SwingV1BacktestExperimentRequest scenario = sensitivity.requestFor(model);
            assertThat(scenario).usingRecursiveComparison()
                    .ignoringFields("costModel").isEqualTo(sensitivity.baseRequest());
            assertThat(scenario.candidateSymbols()).containsExactly("000660", "005930");
            assertThat(scenario.costModel()).isSameAs(model);
        }
        assertThat(sensitivity.baseRequest()).isEqualTo(request());
    }

    @Test
    void copiesCostModelsAndRejectsExternalMutation() {
        SwingV1BacktestExperimentRequest base = request();
        List<TradeCostModel> models = new ArrayList<>(List.of(base.costModel(), model("STRESS", "0.002", "0.002")));
        SwingV1CostSensitivityRequest sensitivity = new SwingV1CostSensitivityRequest(base, models);
        models.clear();

        assertThat(sensitivity.costModels()).hasSize(2);
        assertThatThrownBy(() -> sensitivity.costModels().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullRequestsListsAndElements() {
        assertThatNullPointerException().isThrownBy(() -> new SwingV1CostSensitivityRequest(null, List.of()));
        assertThatNullPointerException().isThrownBy(() -> SwingV1CostSensitivityRequest.forSlippageStress(null));
        assertThatNullPointerException().isThrownBy(() -> new SwingV1CostSensitivityRequest(request(), null));
        assertThatNullPointerException().isThrownBy(() -> new SwingV1CostSensitivityRequest(
                request(), Arrays.asList(request().costModel(), null)));
    }

    @Test
    void rejectsMissingBaselineOrStressModels() {
        assertThatThrownBy(() -> new SwingV1CostSensitivityRequest(request(), List.of()))
                .hasMessageContaining("baseline and at least one stress model");
        assertThatThrownBy(() -> new SwingV1CostSensitivityRequest(request(), List.of(request().costModel())))
                .hasMessageContaining("baseline and at least one stress model");
        assertThatThrownBy(() -> new SwingV1CostSensitivityRequest(request(), List.of(
                model("OTHER", "0.001", "0.001"), model("STRESS", "0.002", "0.002"))))
                .hasMessage("The first costModel must match baseRequest costModel.");
    }

    @Test
    void rejectsDuplicateIdentityEvenWhenRatesDiffer() {
        assertThatThrownBy(() -> new SwingV1CostSensitivityRequest(request(), List.of(
                request().costModel(), model("BASE", "0.002", "0.002"))))
                .hasMessage("costModels must not contain duplicate model identity.");
    }

    @Test
    void rejectsNumericallyDuplicateSlippageRegardlessOfScaleOrIdentity() {
        assertThatThrownBy(() -> new SwingV1CostSensitivityRequest(request(), List.of(
                request().costModel(), model("STRESS", "0.0010", "0.00100"))))
                .hasMessage("costModels must not contain duplicate slippage assumptions.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"BUY_COMMISSION", "SELL_COMMISSION", "TAX"})
    void rejectsChangingCommissionOrTax(String field) {
        TradeCostModel base = request().costModel();
        BigDecimal changed = new BigDecimal("0.01");
        TradeCostModel stress = new TradeCostModel("STRESS", 1,
                field.equals("BUY_COMMISSION") ? changed : base.buyCommissionRate(),
                field.equals("SELL_COMMISSION") ? changed : base.sellCommissionRate(),
                field.equals("TAX") ? changed : base.sellTaxRate(),
                new BigDecimal("0.002"), new BigDecimal("0.002"));

        assertThatThrownBy(() -> new SwingV1CostSensitivityRequest(request(), List.of(base, stress)))
                .hasMessage("Commission and tax rates must remain fixed across costModels.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"BUY", "SELL"})
    void rejectsSlippageBelowBaseline(String side) {
        assertThatThrownBy(() -> new SwingV1CostSensitivityRequest(request(), List.of(
                request().costModel(), model("STRESS", side.equals("BUY") ? "0" : "0.002",
                side.equals("SELL") ? "0" : "0.002"))))
                .hasMessage("Stress slippage rates must not be below the baseline.");
    }

    @Test
    void rejectsUnrequestedCostModels() {
        SwingV1CostSensitivityRequest sensitivity = SwingV1CostSensitivityRequest.forSlippageStress(request());
        assertThatNullPointerException().isThrownBy(() -> sensitivity.requestFor(null));
        assertThatThrownBy(() -> sensitivity.requestFor(model("OTHER", "0.004", "0.004")))
                .hasMessage("costModel must belong to the sensitivity request.");
    }

    @Test
    void rejectsZeroBaselineAndStressRateReachingOne() {
        assertThatThrownBy(() -> SwingV1CostSensitivityRequest.forSlippageStress(
                withModel(model("ZERO", "0", "0"))))
                .hasMessageContaining("at least one positive baseline slippage rate");
        assertThatThrownBy(() -> SwingV1CostSensitivityRequest.forSlippageStress(
                withModel(model("HIGH", "0.34", "0.001"))))
                .hasMessage("buySlippageRate must be at least 0 and less than 1.");
    }

    @Test
    void allowsOneZeroSlippageSideAndNumericallyEqualCommissionScales() {
        SwingV1CostSensitivityRequest asymmetric = SwingV1CostSensitivityRequest.forSlippageStress(
                withModel(model("ASYMMETRIC", "0", "0.001")));
        assertThat(asymmetric.costModels()).extracting(TradeCostModel::buySlippageRate)
                .allSatisfy(rate -> assertThat(rate).isEqualByComparingTo("0"));
        TradeCostModel scaled = new TradeCostModel("SCALED", 1,
                new BigDecimal("0.000150"), new BigDecimal("0.0001500"), new BigDecimal("0.00180"),
                new BigDecimal("0.002"), new BigDecimal("0.002"));
        assertThat(new SwingV1CostSensitivityRequest(request(), List.of(request().costModel(), scaled))
                .costModels()).hasSize(2);
    }

    private SwingV1BacktestExperimentRequest withModel(TradeCostModel costModel) {
        SwingV1BacktestExperimentRequest base = request();
        return new SwingV1BacktestExperimentRequest(base.strategyIdentity(), base.candidateSymbols(),
                base.benchmarkId(), base.fromSignalDate(), base.toSignalDate(),
                base.initialCashAmountKrwPerSymbol(), costModel);
    }
}
