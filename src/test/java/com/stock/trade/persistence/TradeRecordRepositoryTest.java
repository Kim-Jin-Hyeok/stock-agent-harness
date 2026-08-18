package com.stock.trade.persistence;

import com.stock.agent.InvestmentAction;
import com.stock.trade.TradeReasonCode;
import com.stock.trade.TradeStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class TradeRecordRepositoryTest {

    @Autowired
    private TradeRecordRepository tradeRecordRepository;

    @Test
    void findAllByRunIdOrderByExecutedAtDescReturnsMatchingRecordsLatestFirst() {
        // given
        String run1 = "run-1";
        LocalDateTime run1ExecutedAt = LocalDateTime.of(2026, 1, 1, 9, 0);

        String run2 = "run-2";
        LocalDateTime run2ExecutedAt1 = LocalDateTime.of(2026, 1, 1, 9, 10);
        LocalDateTime run2ExecutedAt2 = LocalDateTime.of(2026, 1, 1, 9, 20);

        tradeRecordRepository.save(executedBuyTradeRecordEntity(run1, run1ExecutedAt));
        tradeRecordRepository.save(executedBuyTradeRecordEntity(run2, run2ExecutedAt1));
        tradeRecordRepository.save(executedBuyTradeRecordEntity(run2, run2ExecutedAt2));

        // when
        List<TradeRecordEntity> entities = tradeRecordRepository.findAllByRunIdOrderByExecutedAtDesc(run2);

        // then

        assertThat(entities).hasSize(2);

        assertThat(entities.getFirst().getRunId()).isEqualTo(run2);
        assertThat(entities.getFirst().getExecutedAt()).isEqualTo(run2ExecutedAt2);

        assertThat(entities.getLast().getRunId()).isEqualTo(run2);
        assertThat(entities.getLast().getExecutedAt()).isEqualTo(run2ExecutedAt1);
    }

    @Test
    void findAllByOrderByExecutedAtDescReturnsLatestRecordsFirst() {
        // given
        String run1 = "run-1";
        LocalDateTime run1ExecutedAt = LocalDateTime.of(2026, 1, 1, 9, 0);

        String run2 = "run-2";
        LocalDateTime run2ExecutedAt1 = LocalDateTime.of(2026, 1, 1, 9, 10);
        LocalDateTime run2ExecutedAt2 = LocalDateTime.of(2026, 1, 1, 9, 20);

        tradeRecordRepository.save(executedBuyTradeRecordEntity(run1, run1ExecutedAt));
        tradeRecordRepository.save(executedBuyTradeRecordEntity(run2, run2ExecutedAt1));
        tradeRecordRepository.save(executedBuyTradeRecordEntity(run2, run2ExecutedAt2));

        // when
        List<TradeRecordEntity> entities = tradeRecordRepository.findAllByOrderByExecutedAtDesc();

        // then
        assertThat(entities).hasSize(3);
        assertThat(entities.getFirst().getRunId()).isEqualTo(run2);
        assertThat(entities.getFirst().getExecutedAt()).isEqualTo(run2ExecutedAt2);

        assertThat(entities.getLast().getRunId()).isEqualTo(run1);
        assertThat(entities.getLast().getExecutedAt()).isEqualTo(run1ExecutedAt);
    }

    private TradeRecordEntity executedBuyTradeRecordEntity(String runId) {
        return TradeRecordEntity.of(
                runId,
                InvestmentAction.BUY,
                "005930",
                10L,
                70_000L,
                700_000L,
                TradeStatus.EXECUTED,
                TradeReasonCode.EXECUTION_COMPLETED,
                "BUY execution completed.",
                executedAt()
        );
    }

    private TradeRecordEntity executedBuyTradeRecordEntity(
            String runId,
            LocalDateTime executedAt
    ) {
        return TradeRecordEntity.of(
                runId,
                InvestmentAction.BUY,
                "005930",
                10L,
                70_000L,
                700_000L,
                TradeStatus.EXECUTED,
                TradeReasonCode.EXECUTION_COMPLETED,
                "BUY execution completed.",
                executedAt
        );
    }

    private LocalDateTime executedAt() {
        return LocalDateTime.of(2026, 1, 1, 9, 0, 1);
    }
}
