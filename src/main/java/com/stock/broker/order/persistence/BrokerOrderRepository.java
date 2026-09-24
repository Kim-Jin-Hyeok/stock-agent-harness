package com.stock.broker.order.persistence;

import com.stock.broker.order.BrokerOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface BrokerOrderRepository extends JpaRepository<BrokerOrderEntity, Long> {
    List<BrokerOrderEntity> findAllByStatusInOrderBySubmittedAtAsc(
            Collection<BrokerOrderStatus> statuses
    );

    @Query("""
            select orderEntity
            from BrokerOrderEntity orderEntity
            where orderEntity.cumulativeFilledQuantity
                > orderEntity.portfolioAppliedQuantity
            order by orderEntity.submittedAt asc, orderEntity.id asc
            """)
    List<BrokerOrderEntity> findAllWithUnappliedFills();

    List<BrokerOrderEntity>
    findAllByStatusInAndExpiresAtLessThanEqualAndCancellationStatusIsNullOrderByExpiresAtAsc(
            Collection<BrokerOrderStatus> statuses,
            Instant expiresAt
    );
}
