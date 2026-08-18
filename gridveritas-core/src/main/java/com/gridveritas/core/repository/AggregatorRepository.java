package com.gridveritas.core.repository;

import com.gridveritas.core.domain.Aggregator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AggregatorRepository extends JpaRepository<Aggregator, UUID> {

    List<Aggregator> findAllByOrderByCreatedAtAsc();
}
