package com.stock.market.stock.basicinfo.observation.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface KisStockBasicInfoObservationRepository extends JpaRepository<KisStockBasicInfoObservationEntity, Long> {
    @Query("""
            select observation.id from KisStockBasicInfoObservationEntity observation
            where observation.requestedSymbol = :symbol
                and observation.responseReceivedAt <= :evaluatedAt
                and observation.recordedAt <= :evaluatedAt
            order by observation.requestStartedAt desc, observation.responseReceivedAt desc, observation.id desc
            """)
    List<Long> findObservationIdsAvailableAt(
            @Param("symbol") String symbol,
            @Param("evaluatedAt") Instant evaluatedAt,
            Pageable pageable
    );
}
