package com.stock.harness.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HarnessRunRepository extends JpaRepository<HarnessRunEntity, Long> {
    Optional<HarnessRunEntity> findByRunId(String runId);
}
