package com.gridveritas.core.repository;

import com.gridveritas.core.domain.AnomalyFinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AnomalyFindingRepository extends JpaRepository<AnomalyFinding, UUID> {

    boolean existsByDedupKey(String dedupKey);

    List<AnomalyFinding> findTop100ByOrderByDetectedAtDesc();

    List<AnomalyFinding> findBySourceIdOrderByDetectedAtDesc(UUID sourceId);
}
