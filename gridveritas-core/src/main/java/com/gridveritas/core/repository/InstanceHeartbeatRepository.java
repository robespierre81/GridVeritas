package com.gridveritas.core.repository;

import com.gridveritas.core.domain.InstanceHeartbeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InstanceHeartbeatRepository extends JpaRepository<InstanceHeartbeat, String> {
}
