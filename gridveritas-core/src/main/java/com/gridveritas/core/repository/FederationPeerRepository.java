package com.gridveritas.core.repository;

import com.gridveritas.core.domain.FederationPeer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FederationPeerRepository extends JpaRepository<FederationPeer, UUID> {

    List<FederationPeer> findByEnabledTrue();

    List<FederationPeer> findAllByOrderByCreatedAtAsc();
}
