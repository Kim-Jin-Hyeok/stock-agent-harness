package com.stock.broker.order.persistence;

import com.stock.broker.order.BrokerOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BrokerOrderRepository extends JpaRepository<BrokerOrderEntity, Long> {
    List<BrokerOrderEntity> findAllByStatusInOrderBySubmittedAtAsc(
            Collection<BrokerOrderStatus> statuses
    );
}
