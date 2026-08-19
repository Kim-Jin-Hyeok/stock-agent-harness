package com.stock.harness.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HarnessStepRepository extends JpaRepository<HarnessStepEntity, Long> {
    List<HarnessStepEntity> findAllByRunIdOrderByStepOrderAsc(String runId);
}
