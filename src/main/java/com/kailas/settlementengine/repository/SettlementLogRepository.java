package com.kailas.settlementengine.repository;

import com.kailas.settlementengine.entity.SettlementLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementLogRepository extends JpaRepository<SettlementLog, Long> {
}
