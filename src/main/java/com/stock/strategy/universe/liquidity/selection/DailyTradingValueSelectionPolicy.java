package com.stock.strategy.universe.liquidity.selection;

import com.stock.strategy.universe.liquidity.DailyTradingValueAverage;
import com.stock.strategy.universe.liquidity.ranking.DailyTradingValueRankingPolicy;
import com.stock.strategy.universe.liquidity.selection.result.DailyTradingValueSelectionResult;
import com.stock.strategy.universe.liquidity.selection.result.DailyTradingValueSelectionStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class DailyTradingValueSelectionPolicy {
    private final DailyTradingValueRankingPolicy rankingPolicy;

    public DailyTradingValueSelectionPolicy(DailyTradingValueRankingPolicy rankingPolicy) {
        this.rankingPolicy = Objects.requireNonNull(
                rankingPolicy, "rankingPolicy must not be null."
        );
    }

    public List<DailyTradingValueSelectionResult> select(
            List<DailyTradingValueAverage> averages,
            long minimumAverageTradingValueKrw,
            int maxCandidateCount
    ) {
        if (minimumAverageTradingValueKrw <= 0) {
            throw new IllegalArgumentException(
                    "minimumAverageTradingValueKrw must be positive."
            );
        }
        if (maxCandidateCount <= 0) {
            throw new IllegalArgumentException("maxCandidateCount must be positive.");
        }

        // Validate all inputs before applying thresholds or the candidate limit.
        List<DailyTradingValueAverage> ranked = rankingPolicy.rank(averages);
        List<DailyTradingValueSelectionResult> results = new ArrayList<>(ranked.size());
        int selectedCount = 0;
        for (int index = 0; index < ranked.size(); index++) {
            DailyTradingValueAverage average = ranked.get(index);
            DailyTradingValueSelectionStatus status;
            if (!average.meetsMinimumAverageTradingValueKrw(minimumAverageTradingValueKrw)) {
                status = DailyTradingValueSelectionStatus.LIQUIDITY_BELOW_MINIMUM;
            } else if (selectedCount < maxCandidateCount) {
                status = DailyTradingValueSelectionStatus.SELECTED;
                selectedCount++;
            } else {
                status = DailyTradingValueSelectionStatus.CANDIDATE_LIMIT;
            }
            results.add(new DailyTradingValueSelectionResult(average, index + 1, status));
        }
        return List.copyOf(results);
    }
}
