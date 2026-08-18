package com.gridveritas.core.repository;

import com.gridveritas.core.domain.PeerRoot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PeerRootRepository extends JpaRepository<PeerRoot, UUID> {

    boolean existsByPeerIdAndRootHash(UUID peerId, String rootHash);

    List<PeerRoot> findAllByOrderByFetchedAtDesc(Pageable pageable);

    long countBySignatureValidTrueAndAnchorValidTrue();
}
